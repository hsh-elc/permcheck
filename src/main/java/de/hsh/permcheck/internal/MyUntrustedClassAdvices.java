package de.hsh.permcheck.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;

public class MyUntrustedClassAdvices {

    /**
     * The class initializing of MyUntrustedClassAdvices leads to some calls of the Java standard library, which in turn call 
     * the below enter and exit methods. As long as the initialization of MyUntrustedClassAdvices.class is on progress, 
     * we should not check any permissions. Hence, we set {@code MyUntrustedClassAdvices.initialized} to false in the first line
     * of this class. The last line of this class is a static initializer, that sets {@code MyUntrustedClassAdvices.initialized} to
     * true.
     */
    static boolean initialized;

    @Advice.OnMethodEnter(inline = false)
    public static void enter(@Advice.Origin String origin,
                      @Advice.Origin("#t #m") String detaildOrigin,
                      @Advice.Origin Class<?> originClazz,
                      @Advice.This(optional = true) Object target,
                      @Advice.Origin Executable originExecutable,
                      @Advice.AllArguments Object[] ary) {
        enterImpl(origin);
    }

    @Advice.OnMethodExit(inline = false, onThrowable = Throwable.class)
    public static void exit(@Advice.Origin String origin,
                      @Advice.Origin("#t #m") String detaildOrigin,
                      @Advice.Origin Class<?> originClazz,
                      @Advice.This(optional = true) Object target,
                      @Advice.Origin Executable originExecutable,
                      @Advice.AllArguments Object[] ary,
                      @Advice.Return(typing = Typing.DYNAMIC) Object result) {
        exitImpl(origin);
    }


    public static void enterConstructor() {
        enterImpl("constructor");
    }

    public static void exitConstructor() {
        exitImpl("constructor");
    }

    public static void enterConstructor(String className, Object[] args, String[] paramTypeNames) {
MyAdvices.logConstructorCall("enterConstructor", className, args, paramTypeNames);
       
        Class<?> clazz = null;
        Constructor<?> constructor = null;
        try {
            clazz = Class.forName(className);
            constructor = MyAdvices.findConstructorByNames(clazz, paramTypeNames);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new Error("Internal error in permcheck library", e);
        }
        enterImpl(constructor.toString());
    }

    public static void exitConstructor(String className, Object[] args, String[] paramTypeNames) {
MyAdvices.logConstructorCall("exitConstructor", className, args, paramTypeNames);
        Class<?> clazz = null;
        Constructor<?> constructor = null;
        try {
            clazz = Class.forName(className);
            constructor = MyAdvices.findConstructorByNames(clazz, paramTypeNames);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new Error("Internal error in permcheck library", e);
        }
        exitImpl(constructor.toString());
    }



    static void enterImpl(String origin) {
        boolean oldUntrustedCalledValue = MyAdvices.UNTRUSTED_CALLED.get().peek();
        MyAdvices.UNTRUSTED_CALLED.get().push(true);
        int d = MyAdvices.UNTRUSTED_CALL_DEPTH.get().incrementAndGet();
        log(VerboseCategory.TRACE, "Enter "+origin+"... depth = " + d + ", untrusted_called: "+oldUntrustedCalledValue+" -> true");
    }

    static void exitImpl(String origin) {
        int d = MyAdvices.UNTRUSTED_CALL_DEPTH.get().decrementAndGet();
        boolean uc = MyAdvices.UNTRUSTED_CALLED.get().pop();
        boolean oldUntrustedCalledValue = MyAdvices.UNTRUSTED_CALLED.get().peek();
        log(VerboseCategory.TRACE, "Exit "+origin+"... depth = " + d + ", untrusted_called: "+uc+" -> "+oldUntrustedCalledValue);
    }

    private static void log(VerboseCategory vc, String msg) {
        if (!Specs.include(vc)) return;
        System.out.println(msg);
    }

    // This mus be the last static initializer inside MyUntrustedClassAdvices:
    static {
        initialized = true;
    }




}