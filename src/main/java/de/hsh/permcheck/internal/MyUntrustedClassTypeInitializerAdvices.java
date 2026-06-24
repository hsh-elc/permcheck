package de.hsh.permcheck.internal;

import net.bytebuddy.asm.Advice;

public class MyUntrustedClassTypeInitializerAdvices {

    /**
     * The class initializing of MyUntrustedClassTypeInitializerAdvices leads to some calls of the Java standard library, which in turn call 
     * the below enter and exit methods. As long as the initialization of MyUntrustedClassTypeInitializerAdvices.class is on progress, 
     * we should not check any permissions. Hence, we set {@code MyUntrustedClassTypeInitializerAdvices.initialized} to false in the first line
     * of this class. The last line of this class is a static initializer, that sets {@code MyUntrustedClassTypeInitializerAdvices.initialized} to
     * true.
     */
    static boolean initialized;

    @Advice.OnMethodEnter(inline = false)
    public static void enter(@Advice.Origin String origin) {
        MyUntrustedClassAdvices.enterImpl(origin);
    }

    @Advice.OnMethodExit(inline = false, onThrowable = Throwable.class)
    public static void exit(@Advice.Origin String origin) {
        MyUntrustedClassAdvices.exitImpl(origin);
    }


    // This mus be the last static initializer inside MyUntrustedClassTypeInitializerAdvices:
    static {
        initialized = true;
    }

}