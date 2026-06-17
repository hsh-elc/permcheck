package de.hsh.permcheck.internal;


import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class DenyReflectionAccessDeclaredMembersCheck extends AbstractDenyCheck {

    public DenyReflectionAccessDeclaredMembersCheck() {
        super("reflectionAccessDeclaredMembers", "deny.reflectionAccessDeclaredMembers");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        registry.put(
                Class.class.getDeclaredMethod("getDeclaredField", String.class),
                denyTargetOnDifferentClassLoaders() );
        registry.put(
                Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class),
                denyTargetOnDifferentClassLoaders() );
        registry.put(
                Class.class.getDeclaredMethod("getDeclaredConstructor", Class[].class),
                denyTargetOnDifferentClassLoaders() );
        registry.put(
                Class.class.getDeclaredMethod("getEnclosingMethod"),
                denyTargetOnDifferentClassLoaders() );
        registry.put(
                Class.class.getDeclaredMethod("getEnclosingConstructor"),
                denyTargetOnDifferentClassLoaders() );

        registry.put(
                Class.class.getDeclaredMethod("getDeclaredFields"),
                denyTargetOnDifferentClassLoaders() );
        registry.put(
                Class.class.getDeclaredMethod("getDeclaredMethods"),
                denyTargetOnDifferentClassLoaders() );
        registry.put(
                Class.class.getDeclaredMethod("getDeclaredConstructors"),
                denyTargetOnDifferentClassLoaders() );
        registry.put(
                Class.class.getDeclaredMethod("getDeclaredClasses"),
                denyTargetOnDifferentClassLoaders() );
        registry.put(
                Class.class.getDeclaredMethod("getRecordComponents"),
                denyTargetOnDifferentClassLoaders() );

        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findStatic", Class.class, String.class, MethodType.class),
                denyFirstArgOnDifferentClassloaders() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findStaticSetter", Class.class, String.class, Class.class),
                denyFirstArgOnDifferentClassloaders() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findStaticGetter", Class.class, String.class, Class.class),
                denyFirstArgOnDifferentClassloaders() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findStaticVarHandle", Class.class, String.class, Class.class),
                denyFirstArgOnDifferentClassloaders() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findSetter", Class.class, String.class, Class.class),
                denyFirstArgOnDifferentClassloaders() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findGetter", Class.class, String.class, Class.class),
                denyFirstArgOnDifferentClassloaders() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findVirtual", Class.class, String.class, MethodType.class),
                denyFirstArgOnDifferentClassloaders() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findConstructor", Class.class, MethodType.class),
                denyFirstArgOnDifferentClassloaders() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findSpecial", Class.class, String.class, MethodType.class, Class.class),
                denyFirstArgOnDifferentClassloaders() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("findVarHandle", Class.class, String.class, Class.class),
                denyFirstArgOnDifferentClassloaders() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("bind", Object.class, String.class, MethodType.class),
                denyFirstArgsClassOnDifferentClassloaders() );
    }
    
    
    private class DenyTargetOnDifferentClassLoadersInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            Class<?> clazz = getTarget(hook, Class.class);
            check(hook, null, clazz);

            // if (hook.target() == null) {
            //     throw new IllegalArgumentException("Expected object of type Class, but found null");
            // }
            // if (! (hook.target() instanceof Class)) {
            //     throw new IllegalArgumentException("Expected object of type Class, but found object of type '"+hook.target().getClass()+"'");
            // }
            // Class<?> clazz = (Class<?>)hook.target();
            // ClassLoader cl2 = clazz.getClassLoader();
            // Class<?> caller = Helper.getCallerClass();
            // ClassLoader cl1 = caller.getClassLoader();
            // if (cl1 == cl2) {
            //     String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
            //     log(VerboseCategory.PERMIT, msg);
            //     return;
            // }

            // String msg = getCheckName()+ " is not granted";
            // log(VerboseCategory.PERMIT, "[PERMCHECK] " + msg);
            // Helper.denyInvocation(hook, null, msg);
        }
    }

    public DenyTargetOnDifferentClassLoadersInsert denyTargetOnDifferentClassLoaders() {
        return new DenyTargetOnDifferentClassLoadersInsert();
    }

    private class DenyFirstArgOnDifferentClassLoadersInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            Class<?> clazz = getFirstArg(hook, Class.class);
            MethodHandles.Lookup lookup = getTarget(hook, MethodHandles.Lookup.class);
            check(hook, lookup, clazz);
            // boolean granted = false;
            // if (lookup.hasFullPrivilegeAccess()) {
            //     granted = true;
            // } else {
            //     ClassLoader cl2 = clazz.getClassLoader();
            //     Class<?> caller = Helper.getCallerClass();
            //     ClassLoader cl1 = caller.getClassLoader();
            //     granted = cl1 == cl2;
            // }
            // if (granted) {
            //     String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
            //     log(VerboseCategory.PERMIT, msg);
            //     return;
            // }

            // String msg = getCheckName()+ " is not granted";
            // log(VerboseCategory.PERMIT, "[PERMCHECK] " + msg);
            // Helper.denyInvocation(hook, null, msg);
        }
    }

    public DenyFirstArgOnDifferentClassLoadersInsert denyFirstArgOnDifferentClassloaders() {
        return new DenyFirstArgOnDifferentClassLoadersInsert();
    }

    private class DenyFirstArgsClassOnDifferentClassLoadersInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            Object arg = getFirstArg(hook, Object.class);
            Class<?> clazz = (Class<?>)arg.getClass();
            MethodHandles.Lookup lookup = getTarget(hook, MethodHandles.Lookup.class);
            check(hook, lookup, clazz);
            // boolean granted = false;
            // if (lookup.hasFullPrivilegeAccess()) {
            //     granted = true;
            // } else {
            //     ClassLoader cl2 = clazz.getClassLoader();
            //     Class<?> caller = Helper.getCallerClass();
            //     ClassLoader cl1 = caller.getClassLoader();
            //     granted = cl1 == cl2;
            // }

            // if (granted) {
            //     String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
            //     log(VerboseCategory.PERMIT, msg);
            //     return;
            // }

            // String msg = getCheckName()+ " is not granted";
            // log(VerboseCategory.PERMIT, "[PERMCHECK] " + msg);
            // Helper.denyInvocation(hook, null, msg);
        }
    }

    public DenyFirstArgsClassOnDifferentClassLoadersInsert denyFirstArgsClassOnDifferentClassloaders() {
        return new DenyFirstArgsClassOnDifferentClassLoadersInsert();
    }

    private void check(Hook hook, MethodHandles.Lookup lookup, Class<?> clazz) {
        boolean granted = false;
        if (lookup != null && lookup.hasFullPrivilegeAccess()) {
            granted = true;
        } else {
            ClassLoader cl2 = clazz.getClassLoader();
            Class<?> caller = Helper.getCallerClass();
            ClassLoader cl1 = caller.getClassLoader();
            granted = cl1 == cl2;
        }

        if (granted) {
            String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
            log(VerboseCategory.PERMIT, msg);
            return;
        }

        String msg = getCheckName()+ " is not granted";
        Helper.denyInvocation(hook, null, msg, this);
    }


}
