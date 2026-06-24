package de.hsh.permcheck.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;

public class MyAdvices {
    
    /**
     * The class initializing of MyAdvices leads to some calls of the Java standard library, which in turn call 
     * the below enter and exit methods. As long as the initialization of MyAdvices.class is on progress, 
     * we should not check any permissions. Hence, we set {@code MyAdvices.initialized} to false in the first line
     * of this class. The last line of this class is a static initializer, that sets {@code MyAdvices.initialized} to
     * true.
     */
    private static boolean initialized;

    /**
     * This threadlocal Boolean prevents entering enter/exit when we are already in
     * enter or exit. This threadlocal prevents cycles. All standard library method calls inside enter/exit
     * are assumed to be privileged.
     */
    private static ThreadLocal<Boolean> INSIDE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static ThreadLocal<String[]> CALLSTACK = null;


    private static String convert(Executable e) {
        StringBuilder sb = new StringBuilder();
        if (e == null) {
            sb.append("null");
        } else {
            Class<?> c = e.getDeclaringClass();
            sb.append(c.getName()).append("#");
            if (e instanceof Method) {
                Method m = (Method)e;
                sb.append(m.getName()).append("(");
            } else if (e instanceof Constructor) {
                sb.append("<init>(");
            }
            Parameter[] ps = e.getParameters();
            for (int i=0; i<ps.length; i++) {
                Parameter p = ps[i];
                sb.append(p.getType().getName());
                if (i < ps.length-1) sb.append(", ");
            }
            sb.append(")");
        }
        return sb.toString();
    }

    static boolean tryToGetInside() {
        if (INSIDE == null) {
            INSIDE = new ThreadLocal<>();
            INSIDE = ThreadLocal.withInitial(() -> Boolean.FALSE);
        }
        if (INSIDE.get().equals(Boolean.TRUE)) return false;
        INSIDE.set(Boolean.TRUE);
        return true;
    }

    static void leaveInside() {
        if (INSIDE == null) return;
        INSIDE.set(Boolean.FALSE);
    }

    private static String[] getCallStack() {
        if (CALLSTACK == null) {
            CALLSTACK = new ThreadLocal<>(); // Simple initialization will lead to less cyles.
            CALLSTACK = ThreadLocal.withInitial(() -> new String[10]);
        }
        return CALLSTACK.get();
    }

    private static int pushIfNotOnCallStack(Executable executable) {
        String val = convert(executable); // cannot call String.valueOf, because that would trigger setAccess calls.
        String[] stk = CALLSTACK.get();
        int topIndex = -1;
        for (int i=0; i<stk.length; i++) {
            String s = stk[i];
            if (s == null) {
                topIndex = i;
                break;
            }
            if (s.equals(val)) return -1; // prevent cycle
        }
        if (topIndex < 0) {
            // enlarge
            String[] newstk = new String[stk.length*2];
            System.arraycopy(stk, 0, newstk, 0, stk.length);
            topIndex = stk.length;
            stk = newstk;
            CALLSTACK.set(stk);
        }
        // push
        stk[topIndex] = val;
        return topIndex;
    }

    private static void popCallStack(int topIndex) {
        String[] stk = CALLSTACK.get();
        stk[topIndex] = null;
    }

    @Advice.OnMethodEnter(inline = false)
    public static void onEnter(@Advice.Origin String origin,
                      @Advice.Origin("#t #m") String detaildOrigin,
                      @Advice.Origin Class<?> originClazz,
                      @Advice.This(optional = true) Object target,
                      @Advice.Origin Executable originExecutable,
                      @Advice.AllArguments Object[] ary) {
        if (!initialized) return;
        if (!Specs.isActive()) return;
        if (!tryToGetInside()) return; // cycle
        try {
            enterImpl(MyAdvices.class, originClazz, target, originExecutable, ary);
        } finally {
            leaveInside();
        }
    }

    @Advice.OnMethodExit(inline = false, onThrowable = Throwable.class)
    public static void onMethodExit(@Advice.Origin String origin,
                      @Advice.Origin("#t #m") String detaildOrigin,
                      @Advice.Origin Class<?> originClazz,
                      @Advice.This(optional = true) Object target,
                      @Advice.Origin Executable originExecutable,
                      @Advice.AllArguments Object[] ary,
                      @Advice.Return(typing = Typing.DYNAMIC) Object result) {
        if (!initialized) return;
        if (!Specs.isActive()) return;
        if (!tryToGetInside()) return; // cycle
        try {
            exitImpl(MyAdvices.class, originClazz, target, originExecutable, ary, result);
        } finally {
            leaveInside();
        }
    }

    private static void enterImpl(Class<?> myAdvicesClass, Class<?> originClazz, Object target, Executable originExecutable, Object[] ary) {
        ArrayList<Insert> inserts = Specs.getInserts(originExecutable);
        if (inserts == null) return;
    
        // When this advice determines the stack trace, classes are loaded, which in turn
        // leads to a call to, for example, File.exists(), which itself should be instrumented.
        // Such cycles are broken by the following statements.

        if (getCallStack() == null) {
            // We are in the middle of the unfinished creation of the class variable CALLSTACK
            // This is definitely not called from distrusted classes, so we can safely
            // skip permission checks.
            return;
        }

        int topIndex = pushIfNotOnCallStack(originExecutable);
        if (topIndex < 0) { // already on call stack?
            return; // prevent cycle
        }

        try {
            Hook hook = new Hook(originClazz, target, originExecutable, ary);
            log(VerboseCategory.TRACE, "[PERMCHECK] onMethodEnter: ", hook);
            
            boolean[] stackInfo = isCalledFromSubmission(myAdvicesClass);
            if (!stackInfo[1]) {
                // not untrusted
                return; 
            }

            for (Insert insert : inserts) {
                if (insert instanceof EnterInsert) {
                    insert.onEnter(hook);
                }
            }
        } finally {
            popCallStack(topIndex);
        }
    }

    static void exitImpl(Class<?> myAdvicesClass, Class<?> originClazz, Object target, Executable originExecutable, Object[] ary, Object result) {
        ArrayList<Insert> inserts = Specs.getInserts(originExecutable);
        if (inserts == null) return;

        if (getCallStack() == null) {
            // We are in the middle of the unfinished creation of the class variable CALLSTACK
            // This is definitely not called from distrusted classes, so we can safely
            // skip permission checks.
            return;
        }

        int topIndex = pushIfNotOnCallStack(originExecutable);
        if (topIndex < 0) // already on call stack?
            return; // prevent cycle

        try {
            Hook hook = new Hook(originClazz, target, originExecutable, ary);
            log(VerboseCategory.TRACE, "[PERMCHECK] onMethodExit: ", hook);

            boolean[] stackInfo = isCalledFromSubmission(myAdvicesClass);
            if (!stackInfo[1]) {
                // not untrusted
                return; 
            }

            for (Insert insert : inserts) {
                if (insert instanceof ExitInsert) {
                    insert.onExit(hook, result);
                }
            }
        } finally {
            popCallStack(topIndex);
        }
    }


    /**
     * 
     * @param myAdvicesClass
     * @return two booleans signaling, which one of the following two events occured first when climbing
     *         up the call stack: (isPrivileged, isUntrustedClass). If both booleans are false, then none of
     *         the events occurred.
     */
    private static boolean[] isCalledFromSubmission(Class<?> myAdvicesClass) {
        boolean isUntrustedClass = false;
        boolean isPrivileged = false;
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        boolean leftMyAdvices = false;
        outerloop:
        for (int i = 1; i < trace.length; i++) {
            StackTraceElement e= trace[i];
            if (myAdvicesClass.getName().equals(e.getClassName())) {
                if (leftMyAdvices) {
                    // reentered MyAdvice... class. This is a cycle.
                    break outerloop;
                }
            } else {
                leftMyAdvices = true;
            }
            
            String mcm = e.getModuleName() + "/" + e.getClassName() + "#" + e.getMethodName();
            if (Specs.isPrivileged(mcm)) {
                isPrivileged = true;
                break outerloop;
            }
            
            if (Specs.isUntrustedClass(e.getClassName())) {
                isUntrustedClass = true;
                break outerloop;
            }
        }
        return new boolean[] { isPrivileged, isUntrustedClass };
    }

    private static void log(VerboseCategory vc, String prefix, Hook hook) {
        if (!Specs.include(vc)) return;
        StringBuilder msg = new StringBuilder();
        msg.append(prefix);
        msg.append(hook.pretty());
        System.out.println(msg.toString());
    }

    // This must be the last static initializer inside MyAdvices:
    static {
        MyAdvices.initialized = true;
    }
}
