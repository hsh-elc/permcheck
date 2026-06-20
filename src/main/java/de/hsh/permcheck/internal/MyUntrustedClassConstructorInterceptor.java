package de.hsh.permcheck.internal;

// import java.lang.reflect.Constructor;
// import java.lang.reflect.Executable;
// import java.lang.reflect.Method;
// import java.util.concurrent.Callable;

// import net.bytebuddy.implementation.bind.annotation.Origin;
// import net.bytebuddy.implementation.bind.annotation.Argument;
// import net.bytebuddy.implementation.bind.annotation.SuperCall;
// import net.bytebuddy.implementation.bind.annotation.This;

// public class MyUntrustedClassConstructorInterceptor {
//     /**
//      * The class initializing of MyUntrustedClassConstructorInterceptor leads to some calls of the Java standard library, which in turn call 
//      * the below enter and exit methods. As long as the initialization of MyUntrustedClassConstructorInterceptor.class is on progress, 
//      * we should not check any permissions. Hence, we set {@code MyUntrustedClassConstructorInterceptor.initialized} to false in the first line
//      * of this class. The last line of this class is a static initializer, that sets {@code MyUntrustedClassConstructorInterceptor.initialized} to
//      * true.
//      */
//     static boolean initialized;

//     // public void construct() throws Exception {
//     //     System.out.println("CALLING XTOR");
//     // }
//     // private static final ThreadLocal<Boolean> isHookActive = ThreadLocal.withInitial(() -> false);

//     // public static void intercept() {
//     //     String origin = "unknown";
//     //     if (isHookActive.get()) {
//     //         return;
//     //     }

//     //     isHookActive.set(true);
//     //     try {
//     //         System.out.println("Enter "+origin+"...");
//     //         int depth = MyAdvices.UNTRUSTED_CALL_DEPTH.get().incrementAndGet();
//     //         System.out.println("...depth = "+depth);
//     //     } catch (Exception e) {
//     //         // Hier ist der Hook, wenn der Konstruktor mit einer Exception abbricht
//     //         System.err.println("Fehler im Konstruktor: " + e.getMessage());
//     //         // Exception muss neu geworfen werden, damit das Objekt nicht instanziiert wird
//     //         throw e;
//     //     } finally {
//     //         System.out.println("Exit "+origin+"...");
//     //         int depth = MyAdvices.UNTRUSTED_CALL_DEPTH.get().decrementAndGet();
//     //         System.out.println("...depth = "+depth);
//     //         isHookActive.set(false);
//     //     }
//     // }

//     // private static final ThreadLocal<Integer> debugCnt = ThreadLocal.withInitial(() -> 0);

// //     public static Object intercept(@Origin String origin,
// //                                  @Origin Constructor<?> constructor,
// //                                  @SuperCall(nullIfImpossible = true) Callable<?> zuper,
// //                                  @net.bytebuddy.implementation.bind.annotation.AllArguments(nullIfEmpty = true) Object[] args
// //                                 ) throws Exception {
// //         debugCnt.set(debugCnt.get()+1);
// //         if (debugCnt.get() > 10) {
// //             System.out.println("CYCLE!");
// //             throw new Error("CYCLE");
// //         }

// // System.out.println("@SuperCall zuper="+zuper);
// //         if (isHookActive.get()) {
// //             if (zuper != null) zuper.call();
// //             return constructor.newInstance(args);
// //             // return;
// //         }

// //         isHookActive.set(true);
// //         try {
// //             // Führt den ursprünglichen Konstruktor aus
// //             if (zuper != null) zuper.call();
// //             System.out.println("Enter "+origin+"...");
// //             int depth = MyAdvices.UNTRUSTED_CALL_DEPTH.get().incrementAndGet();
// //             System.out.println("...depth = "+depth);
// //             return constructor.newInstance(args);
// //         } catch (Exception e) {
// //             // Hier ist der Hook, wenn der Konstruktor mit einer Exception abbricht
// //             System.err.println("Fehler im Konstruktor: " + e.getMessage());
// //             // Exception muss neu geworfen werden, damit das Objekt nicht instanziiert wird
// //             throw e;
// //         } finally {
// //             System.out.println("Exit ");//+origin+"...");
// //             int depth = MyAdvices.UNTRUSTED_CALL_DEPTH.get().decrementAndGet();
// //             System.out.println("...depth = "+depth);
// //             isHookActive.set(false);
// //         }
// //     }

//     // public static boolean tryEnter() {
//     //     System.out.println("Enter ...");
//     //     int depth = MyAdvices.UNTRUSTED_CALL_DEPTH.get().get();
//     //     if (depth > 0) {
//     //         System.out.println("  ... hook is active");
//     //         return false;
//     //     }
//     //     depth = MyAdvices.UNTRUSTED_CALL_DEPTH.get().incrementAndGet();
//     //     System.out.println("...depth = "+depth);
//     //     return true;
//     // }

//     public static void enter() {
//         System.out.println("Enter constructor ... depth = " + MyAdvices.UNTRUSTED_CALL_DEPTH.get().incrementAndGet());
//     }

//     // public static void exit(boolean entered) {
//     //     System.out.println("Exit ...");
//     //     if (entered) {
//     //         int depth = MyAdvices.UNTRUSTED_CALL_DEPTH.get().decrementAndGet();
//     //         System.out.println("...depth = "+depth);
//     //     }
//     // }

//     public static void exit() {
//         System.out.println("Exit constructor ... depth = "+ MyAdvices.UNTRUSTED_CALL_DEPTH.get().decrementAndGet());
//     }

//     // This mus be the last static initializer inside MyAdvices:
//     static {
//         initialized = true;
//     }

// }
