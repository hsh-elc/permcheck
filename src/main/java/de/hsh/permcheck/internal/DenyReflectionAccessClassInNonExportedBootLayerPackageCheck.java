package de.hsh.permcheck.internal;


import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public class DenyReflectionAccessClassInNonExportedBootLayerPackageCheck extends AbstractDenyCheck {

    public DenyReflectionAccessClassInNonExportedBootLayerPackageCheck() {
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

        registry.put(
            Class.forName("java.lang.Class").getDeclaredMethod("getDeclaringClass"),
            denyOnReturnedClassClassloaderIsNullAndCallerLoaderIsNotNull() );

        registry.put(
            Class.forName("java.lang.Class").getDeclaredMethod("getEnclosingClass"),
            denyOnReturnedClassClassloaderIsNullAndCallerLoaderIsNotNull() );

        registry.put(
            Class.forName("java.lang.Class").getDeclaredMethod("newInstance"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );

        registry.put(
            Class.forName("java.lang.Class").getDeclaredMethod("getDeclaredField", String.class),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.forName("java.lang.Class").getDeclaredMethod("getDeclaredMethod", String.class, Class[].class),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.forName("java.lang.Class").getDeclaredMethod("getDeclaredConstructor", Class[].class),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.forName("java.lang.Class").getDeclaredMethod("getEnclosingMethod"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.forName("java.lang.Class").getDeclaredMethod("getEnclosingConstructor"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.class.getDeclaredMethod("getDeclaredFields"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.class.getDeclaredMethod("getDeclaredMethods"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.class.getDeclaredMethod("getDeclaredConstructors"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.class.getDeclaredMethod("getDeclaredClasses"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.class.getDeclaredMethod("getRecordComponents"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.class.getDeclaredMethod("getField", String.class),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );

        // Das hier führt zu StackOverflows:
        // registry.put(
        //     Class.class.getDeclaredMethod("getMethod", String.class, Class[].class),
        //     denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        // Lasse ich offen.

        registry.put(
            Class.class.getDeclaredMethod("getConstructor", Class[].class),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.class.getDeclaredMethod("getFields"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.class.getDeclaredMethod("getMethods"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.class.getDeclaredMethod("getConstructors"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );
        registry.put(
            Class.class.getDeclaredMethod("getClasses"),
            denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() );

  
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findStatic", Class.class, String.class, MethodType.class),
                denyLookupFirstArgOnNonExportedBootLayerPackage() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findStaticSetter", Class.class, String.class, Class.class),
                denyLookupFirstArgOnNonExportedBootLayerPackage() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findStaticGetter", Class.class, String.class, Class.class),
                denyLookupFirstArgOnNonExportedBootLayerPackage() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findStaticVarHandle", Class.class, String.class, Class.class),
                denyLookupFirstArgOnNonExportedBootLayerPackage() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findSetter", Class.class, String.class, Class.class),
                denyLookupFirstArgOnNonExportedBootLayerPackage() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findGetter", Class.class, String.class, Class.class),
                denyLookupFirstArgOnNonExportedBootLayerPackage() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findVirtual", Class.class, String.class, MethodType.class),
                denyLookupFirstArgOnNonExportedBootLayerPackage() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findConstructor", Class.class, MethodType.class),
                denyLookupFirstArgOnNonExportedBootLayerPackage() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findSpecial", Class.class, String.class, MethodType.class, Class.class),
                denyLookupFirstArgOnNonExportedBootLayerPackage() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findVarHandle", Class.class, String.class, Class.class),
                denyLookupFirstArgOnNonExportedBootLayerPackage() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("bind", Object.class, String.class, MethodType.class),
                denyLookupFirstArgsClassOnNonExportedBootLayerPackage() );  
    }
    
    private class DenyFirstArgOnNonExportedBootLayerPackageInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            Object o  = getFirstArg(hook, Object.class);
            if (o == null) {
                throw new IllegalArgumentException("Expected first arg of type String or Class, but found null");
            }
            if (o instanceof String) {
                check(hook, (String)o);
            } else if (o instanceof Class) {
                checkIfArgIsRelevant(hook, (Class<?>)o);
            } else {
                throw new IllegalArgumentException("Expected first arg of type String or Class, but found '" + o.getClass() + "'");
            }
        }
    }

    public DenyFirstArgOnNonExportedBootLayerPackageInsert denyFirstArgOnNonExportedBootLayerPackage() {
        return new DenyFirstArgOnNonExportedBootLayerPackageInsert();
    }

    private class DenyLookupFirstArgOnNonExportedBootLayerPackageInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            Class<?> clazz = getFirstArg(hook, Class.class);
            MethodHandles.Lookup lookup = getTarget(hook, MethodHandles.Lookup.class);
            checkLookup(hook, lookup, clazz);
        }
    }

    public DenyLookupFirstArgOnNonExportedBootLayerPackageInsert denyLookupFirstArgOnNonExportedBootLayerPackage() {
        return new DenyLookupFirstArgOnNonExportedBootLayerPackageInsert();
    }

    private class DenyLookupFirstArgsClassOnNonExportedBootLayerPackageInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            Object arg = getFirstArg(hook, Object.class);
            Class<?> clazz = (Class<?>)arg.getClass();
            MethodHandles.Lookup lookup = getTarget(hook, MethodHandles.Lookup.class);
            checkLookup(hook, lookup, clazz);
        }
    }

    public DenyLookupFirstArgsClassOnNonExportedBootLayerPackageInsert denyLookupFirstArgsClassOnNonExportedBootLayerPackage() {
        return new DenyLookupFirstArgsClassOnNonExportedBootLayerPackageInsert();
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
	
    private class DenyOnReturnedClassClassloaderIsNullAndCallerLoaderIsNotNullInsert extends ExitInsert {
        @Override
        public void onExitImpl(Hook hook, Object result) {
            Class<?> clazz = (Class<?>)result;
            if (clazz == null) return;

			checkIfArgIsRelevant(hook, clazz);
        }
    }
    public DenyOnReturnedClassClassloaderIsNullAndCallerLoaderIsNotNullInsert denyOnReturnedClassClassloaderIsNullAndCallerLoaderIsNotNull() {
        return new DenyOnReturnedClassClassloaderIsNullAndCallerLoaderIsNotNullInsert();
    }

    private class DenyOnTargetClassloaderIsNullAndCallerLoaderIsNotNullInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
			checkIfTargetIsRelevant(hook);
        }
    }
    public DenyOnTargetClassloaderIsNullAndCallerLoaderIsNotNullInsert denyOnTargetClassloaderIsNullAndCallerLoaderIsNotNull() {
        return new DenyOnTargetClassloaderIsNullAndCallerLoaderIsNotNullInsert();
    }

	private void checkIfTargetIsRelevant(Hook hook) {
		checkIfArgIsRelevant(hook, getTarget(hook, Class.class));
	}

	private void checkIfArgIsRelevant(Hook hook, Class<?> clazz) {
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

    private void checkLookup(Hook hook, MethodHandles.Lookup lookup, Class<?> clazz) {
        if (lookup != null && lookup.hasFullPrivilegeAccess()) {
            String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
            log(VerboseCategory.PERMIT, msg);
            return;
        } 
        checkIfArgIsRelevant(hook, clazz);
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
