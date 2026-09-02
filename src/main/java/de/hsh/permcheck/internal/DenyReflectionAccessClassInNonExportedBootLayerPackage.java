package de.hsh.permcheck.internal;


import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public class DenyReflectionAccessClassInNonExportedBootLayerPackage extends AbstractDenyCheck {

    public DenyReflectionAccessClassInNonExportedBootLayerPackage() {
        super("reflectionAccessClassInNonExportedBootLayerPackage", "deny.reflectionAccessClassInNonExportedBootLayerPackage");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        ClassLoader appClassLoader = ClassLoader.getSystemClassLoader();
        Class<?> appClassLoaderClass = appClassLoader.getClass();

        registry.put(
            // In Java version 24 (and maybe some version before), the loadClass method was eliminated from AppCLassLoader.
            // So we take the method of the superclass BuiltinClassLoader:
            Helper.getDeclaredMethodOfClassOrSuperClass(appClassLoaderClass, "loadClass", String.class, boolean.class),
            denyFirstArgOnNonExportedBootLayerPackage() );

        registry.put(
            Class.forName("jdk.internal.loader.Loader").getDeclaredMethod("loadClass", String.class, boolean.class),
            denyFirstArgOnNonExportedBootLayerPackage() );

        // Class.getPermittedSubclasses() liefert nur dann ein nichtleeres Array, wenn es für eine "sealed" Class aufgerufen wird.
        // Fraglich ist, ob es überhaupt eine Klasse gibt, deren permitted subclasses in einem non exported bootlayer package wohnen.
        // Ich lasse diese Option offen als sehr unwahrscheinliches Einfallstor:
        //registry.put(
        //    Class.class.getDeclaredMethod("getPermittedSubclasses"),
        //    denyResultClassArrayOnNonBootToBootClassLoaderAndNonExportedBootLayerPackage() );

        registry.put(
            Class.forName("java.lang.Class").getDeclaredMethod("getNestMembers"),
            denyOnMultiReturnAndOnTargetsLoaderIsNullAndCallerLoaderIsNotNull() );

        registry.put(
            Class.forName("java.lang.Class").getDeclaredMethod("getNestHost"),
            denyOnThisReturnAndOnTargetsLoaderIsNullAndCallerLoaderIsNotNull() );

//    java.lang.Class.getNestHost()
//    java.lang.Class.getDeclaringClass()
//    java.lang.Class.getEnclosingClass()
//    java.lang.Class.checkMemberAccess() -> viele Methoden in java.lang.Class. und zwar
//      newInstance
// 	 getEnclosingMethod
// 	 getConstructor(Class...)
// 	 getEnclosingConstructor
// 	 getField(String)
// 	 getMethods
// 	 getConstructors
// 	 getDeclaredField(String)
// 	 getDelcaredMethods
// 	 getDeclaredClasses
// 	 getRecordComponents
// 	 getDeclaredConstructors
// 	 getFields
// 	 getDeclaredConstructor(Class...)
// 	 getClasses
// 	 getDeclaredMethod(String, Class...)
// 	 getMethod(String, Class...)
// 	 getDeclaredFields
//   Und bei ClassLoader.checkPackageAccess steht als Kommentar: Invoked by the VM after loading class with this loader.
//   D. h. wahrscheinlich muss ich beim Exit von loadClass einen Insert einhängen?
  
  
  
//   Außerdem muss ich aus Lookup ganz viele Methoden einbinden, die via MethodHandles$Lookup.checkSecurityManager auf ReflectUtil.checkPackageAccess(class) zugreifen. Die Bedingung lautet hier: lookup hat keine full privileges.
//   Das sind genau die in DenyReflectionAccessDeclaredMembersCheck registrierten Methoden, also:
//         registry.put(
//                 MethodHandles.Lookup.class.getDeclaredMethod("findStatic", Class.class, String.class, MethodType.class),
//                 denyFirstArgOnDifferentClassloaders() );
//         registry.put(
//                 MethodHandles.Lookup.class.getDeclaredMethod("findStaticSetter", Class.class, String.class, Class.class),
//                 denyFirstArgOnDifferentClassloaders() );
//         registry.put(
//                 MethodHandles.Lookup.class.getDeclaredMethod("findStaticGetter", Class.class, String.class, Class.class),
//                 denyFirstArgOnDifferentClassloaders() );
//         registry.put(
//                 MethodHandles.Lookup.class.getDeclaredMethod("findStaticVarHandle", Class.class, String.class, Class.class),
//                 denyFirstArgOnDifferentClassloaders() );
//         registry.put(
//                 MethodHandles.Lookup.class.getDeclaredMethod("findSetter", Class.class, String.class, Class.class),
//                 denyFirstArgOnDifferentClassloaders() );
//         registry.put(
//                 MethodHandles.Lookup.class.getDeclaredMethod("findGetter", Class.class, String.class, Class.class),
//                 denyFirstArgOnDifferentClassloaders() );
//         registry.put(
//                 MethodHandles.Lookup.class.getDeclaredMethod("findVirtual", Class.class, String.class, MethodType.class),
//                 denyFirstArgOnDifferentClassloaders() );
//         registry.put(
//                 MethodHandles.Lookup.class.getDeclaredMethod("findConstructor", Class.class, MethodType.class),
//                 denyFirstArgOnDifferentClassloaders() );
//         registry.put(
//                 MethodHandles.Lookup.class.getDeclaredMethod("findSpecial", Class.class, String.class, MethodType.class, Class.class),
//                 denyFirstArgOnDifferentClassloaders() );
//         registry.put(
//                 MethodHandles.Lookup.class.getDeclaredMethod("findVarHandle", Class.class, String.class, Class.class),
//                 denyFirstArgOnDifferentClassloaders() );
//         registry.put(
//                 MethodHandles.Lookup.class.getDeclaredMethod("bind", Object.class, String.class, MethodType.class),
//                 denyFirstArgsClassOnDifferentClassloaders() );  


    }
    
    private class DenyFirstArgOnNonExportedBootLayerPackageInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            String cn = getFirstArg(hook, String.class);
            check(hook, cn);
        }
    }

    public DenyFirstArgOnNonExportedBootLayerPackageInsert denyFirstArgOnNonExportedBootLayerPackage() {
        return new DenyFirstArgOnNonExportedBootLayerPackageInsert();
    }

    private class DenyOnMultiReturnAndOnTargetsLoaderIsNullAndCallerLoaderIsNotNullInsert extends ExitInsert {
        @Override
        public void onExitImpl(Hook hook, Object result) {
            Class<?>[] classes = (Class<?>[])result;
            // If the return value contains only the class itself, then no check is required:
            if (classes == null || classes.length <= 1) return;

			checkIfTargetIsRelevant(hook);
        }
    }
    public DenyOnMultiReturnAndOnTargetsLoaderIsNullAndCallerLoaderIsNotNullInsert denyOnMultiReturnAndOnTargetsLoaderIsNullAndCallerLoaderIsNotNull() {
        return new DenyOnMultiReturnAndOnTargetsLoaderIsNullAndCallerLoaderIsNotNullInsert();
    }

    private class DenyOnThisReturnAndOnTargetsLoaderIsNullAndCallerLoaderIsNotNullInsert extends ExitInsert {
        @Override
        public void onExitImpl(Hook hook, Object result) {
            Class<?> clazz = (Class<?>)result;
            // If the return value is the class itself, then no check is required:
            if (clazz == getTarget(hook, Class.class)) return;

			checkIfTargetIsRelevant(hook);
        }
    }
    public DenyOnThisReturnAndOnTargetsLoaderIsNullAndCallerLoaderIsNotNullInsert denyOnThisReturnAndOnTargetsLoaderIsNullAndCallerLoaderIsNotNull() {
        return new DenyOnThisReturnAndOnTargetsLoaderIsNullAndCallerLoaderIsNotNullInsert();
    }
	
	private void checkIfTargetIsRelevant(Hook hook) {
		Class<?> clazz = getTarget(hook, Class.class);
		ClassLoader loader = clazz.getClassLoader();

		// The following if cascade mimics ReflectUtil.needsPackageAccessCheck(ccl, cl)
		if (loader == null) {
			Class<?> caller = Helper.getCallerClass();
			if (caller == null) return;
			ClassLoader ccl = caller.getClassLoader();
			if (ccl != null) {
				// Yes, we need a check:
				check(hook, clazz.getName());
				return;
			}
		}
		String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
		log(VerboseCategory.PERMIT, msg);     
	}


    // private class DenyResultClassArrayOnNonBootToBootClassLoaderAndNonExportedBootLayerPackageInsert extends ExitInsert {
    //     @Override
    //     public void onExitImpl(Hook hook, Object result) {
    //         Class<?>[] subClasses = (Class<?>[])result;
    //         for (Class<?> c : subClasses) {
    //             check(hook, c);
    //         }
    //     }
    // }

    // public DenyResultClassArrayOnNonBootToBootClassLoaderAndNonExportedBootLayerPackageInsert denyResultClassArrayOnNonBootToBootClassLoaderAndNonExportedBootLayerPackage() {
    //     return new DenyResultClassArrayOnNonBootToBootClassLoaderAndNonExportedBootLayerPackageInsert();
    // }
    

    private void check(Hook hook, String cn) {
        if (cn != null
                && 
                (
                    cn.startsWith("com.sun.")
                    || cn.startsWith("jdk.internal") // no trailing dot is intended!
                    || cn.startsWith("sun.")
                )) {
            String msg = getCheckName()+ " is not granted";
            Helper.denyInvocation(hook, null, msg, this);
            return;
        }
        String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
        log(VerboseCategory.PERMIT, msg);
    }


    

    // private void check(Hook hook, Class<?> clazz) {
    //     if (clazz == null) return;
    //     ClassLoader cl = clazz.getClassLoader();
    //     if (cl == null) {
    //         Class<?> caller = Helper.getCallerClass();
    //         ClassLoader ccl = caller.getClassLoader();
    //         if (ccl != null) {
    //             check(hook, clazz.getName());
    //         }
    //     }
    // }

    protected static <T> T getTarget(Hook hook, Class<T> clazz) {
        if (hook.target() == null) {
            throw new IllegalArgumentException("Expected object of type "+clazz+", but found null");
        }
        if (! (clazz.isAssignableFrom(hook.target().getClass()))) {
            throw new IllegalArgumentException("Expected object of type "+clazz+", but found object of type '"+hook.target().getClass()+"'");
        }
        @SuppressWarnings("unchecked")
        T result = (T)hook.target();
        return result;
    }


}
