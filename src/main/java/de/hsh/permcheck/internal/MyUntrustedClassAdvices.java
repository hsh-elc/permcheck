package de.hsh.permcheck.internal;

import java.lang.reflect.Executable;
import java.util.Arrays;

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
        System.out.println("Enter "+origin+"... depth = "+  MyAdvices.UNTRUSTED_CALL_DEPTH.get().incrementAndGet());
    }

    @Advice.OnMethodExit(inline = false, onThrowable = Throwable.class)
    public static void exit(@Advice.Origin String origin,
                      @Advice.Origin("#t #m") String detaildOrigin,
                      @Advice.Origin Class<?> originClazz,
                      @Advice.This(optional = true) Object target,
                      @Advice.Origin Executable originExecutable,
                      @Advice.AllArguments Object[] ary,
                      @Advice.Return(typing = Typing.DYNAMIC) Object result) {
        System.out.println("Exit "+origin+"... depth = "+  MyAdvices.UNTRUSTED_CALL_DEPTH.get().decrementAndGet());
    }


    public static void enterConstructor() {
        System.out.println("Enter constructor ... depth = " + MyAdvices.UNTRUSTED_CALL_DEPTH.get().incrementAndGet());
    }

    public static void exitConstructor() {
        System.out.println("Exit constructor ... depth = "+ MyAdvices.UNTRUSTED_CALL_DEPTH.get().decrementAndGet());
    }


    // This mus be the last static initializer inside MyUntrustedClassAdvices:
    static {
        initialized = true;
    }



}