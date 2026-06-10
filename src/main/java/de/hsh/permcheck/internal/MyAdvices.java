package de.hsh.permcheck.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;

public class MyAdvices {

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
    public static void enter(@Advice.Origin String origin,
                      @Advice.Origin("#t #m") String detaildOrigin,
                      @Advice.Origin Class<?> originClazz,
                      @Advice.This(optional = true) Object target,
                      @Advice.Origin Executable originExecutable,
                      @Advice.AllArguments Object[] ary) {
        if (!Specs.isActive()) return;

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
            
            Insert insert = Specs.getInsert(originExecutable);
            if (insert == null) return;
           
            // boolean isCalledFromSubmission = false;
            // StackTraceElement[] trace = Thread.currentThread().getStackTrace();
            // outerloop:
            // for (StackTraceElement e : trace) {
            //     String mcm = e.getModuleName() + "/" + e.getClassName() + "#" + e.getMethodName();
            //     for (String t : Specs.getPrivilege()) {
            //         if (t == null) break;
            //         if (t.equals(mcm)) {
            //             break outerloop;
            //         }
            //     }
            //     if (Specs.isUntrustedClass(e.getClassName())) {
            //         isCalledFromSubmission = true;
            //         break outerloop;
            //     }
            // }
            
            if (!isCalledFromSubmission()) return;
            
            insert.onEnter(hook);
        } finally {
            popCallStack(topIndex);
        }
    }

    @Advice.OnMethodExit(inline = false)
    public static void exit(@Advice.Origin String origin,
                      @Advice.Origin("#t #m") String detaildOrigin,
                      @Advice.Origin Class<?> originClazz,
                      @Advice.This(optional = true) Object target,
                      @Advice.Origin Executable originExecutable,
                      @Advice.AllArguments Object[] ary,
                      @Advice.Return(typing = Typing.DYNAMIC) Object result) {
        if (!Specs.isActive()) return;

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

            Insert insert = Specs.getInsert(originExecutable);
            if (insert == null) return;

            if (insert.callOnExitFromDistrustedCodeOnly()) {
                if (!isCalledFromSubmission()) return;
            }

            insert.onExit(hook, result);
        } finally {
            popCallStack(topIndex);
        }
    }

    private static boolean isCalledFromSubmission() {
        boolean isCalledFromSubmission = false;
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        boolean leftMyAdvices = false;
        outerloop:
        for (int i = 1; i < trace.length; i++) {
            StackTraceElement e= trace[i];
            if (MyAdvices.class.getName().equals(e.getClassName())) {
                if (leftMyAdvices) {
                    // reentered MyAdvice. This is a cycle.
                    break outerloop;
                }
            } else {
                leftMyAdvices = true;
            }
            
            String mcm = e.getModuleName() + "/" + e.getClassName() + "#" + e.getMethodName();
            if (Specs.isPrivileged(mcm)) {
                break outerloop;
            }
            
            if (Specs.isUntrustedClass(e.getClassName())) {
                isCalledFromSubmission = true;
                break outerloop;
            }
        }
        return isCalledFromSubmission;
    }

    private static void log(VerboseCategory vc, String prefix, Hook hook) {
        if (!Specs.include(vc)) return;
        StringBuilder msg = new StringBuilder();
        msg.append(prefix);
        msg.append(hook.pretty());
        System.out.println(msg.toString());
    }
}
