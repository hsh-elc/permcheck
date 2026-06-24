package de.hsh.permcheck.internal;

import java.lang.reflect.Executable;
import net.bytebuddy.asm.Advice;

public class MyAdvicesConstructor {
    
    /**
     * The class initializing of MyAdvicesConstructor leads to some calls of the Java standard library, which in turn call 
     * the below enter and exit methods. As long as the initialization of MyAdvicesConstructor.class is on progress, 
     * we should not check any permissions. Hence, we set {@code MyAdvicesConstructor.initialized} to false in the first line
     * of this class. The last line of this class is a static initializer, that sets {@code MyAdvicesConstructor.initialized} to
     * true.
     */
    private static boolean initialized;


    @Advice.OnMethodExit(inline = false)
    public static void onConstructorExit(@Advice.Origin String origin,
                      @Advice.Origin("#t #m") String detaildOrigin,
                      @Advice.Origin Class<?> originClazz,
                      @Advice.This(optional = true) Object target,
                      @Advice.Origin Executable originExecutable,
                      @Advice.AllArguments Object[] ary) {
        if (!initialized) return;
        if (!Specs.isActive()) return;
        if (!MyAdvices.tryToGetInside()) return; // cycle
        try {
            MyAdvices.exitImpl(MyAdvicesConstructor.class, originClazz, target, originExecutable, ary, null);
        } finally {
            MyAdvices.leaveInside();
        }
    }


    // This must be the last static initializer inside MyAdvicesConstructor:
    static {
        MyAdvicesConstructor.initialized = true;
    }
}