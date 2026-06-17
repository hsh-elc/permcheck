package de.hsh.permcheck.internal;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.Configuration;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;

public class DenyReflectionGetClassLoader extends AbstractDenyCheck {
    public DenyReflectionGetClassLoader() {
        super("reflectionGetClassLoader", "deny.reflectionGetClassLoader");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        registry.put(
                Class.class.getDeclaredMethod("forName", String.class, boolean.class, ClassLoader.class),
                denySpecifiedLoaderIsNullAndCallerLoaderIsNotNull());
        registry.put(
                Class.class.getDeclaredMethod("forName", Module.class, String.class),
                denyCallerModuleDifferentFromSpecifiedModule());

        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("ensureInitialized", Class.class),
                denyOnUnprivilegedMethodHandlesLookup());
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("accessClass", Class.class),
                denyOnUnprivilegedMethodHandlesLookup());

        registry.put(
                MethodType.class.getDeclaredMethod("fromMethodDescriptorString", String.class, ClassLoader.class),
                denyOnMethodTypeFromMethodDescriptorString());

        registry.put(
                Module.class.getDeclaredMethod("getClassLoader"),
                deny());
        registry.put(
                ModuleLayer.class.getDeclaredMethod("defineModulesWithManyLoaders", Configuration.class, List.class, ClassLoader.class),
                deny());
        registry.put(
                ModuleLayer.class.getDeclaredMethod("defineModulesWithOneLoader", Configuration.class, List.class, ClassLoader.class),
                deny());
        registry.put(
                ModuleLayer.class.getDeclaredMethod("defineModules", Configuration.class, List.class, Function.class),
                deny());

        registry.put(
                Proxy.class.getDeclaredMethod("getProxyClass", ClassLoader.class, Class[].class),
                denySpecifiedLoaderIsNullAndCallerLoaderIsNotNull());
        registry.put(
                Proxy.class.getDeclaredMethod("newProxyInstance", ClassLoader.class, Class[].class, InvocationHandler.class),
                denySpecifiedLoaderIsNullAndCallerLoaderIsNotNull());

        registry.put(
                ResourceBundle.class.getDeclaredMethod("getBundle", String.class, Module.class),
                denyCallerModuleDifferentFromSpecifiedModule());
        registry.put(
                ResourceBundle.class.getDeclaredMethod("getBundle", String.class, Locale.class, Module.class),
                denyCallerModuleDifferentFromSpecifiedModule());


        // registries on exit:

        registry.put(
                Thread.class.getDeclaredMethod("getContextClassLoader"),
                denyOnExit());

        registry.put(
                Class.class.getDeclaredMethod("getClassLoader"),
                denyOnExit());
        registry.put(
                ClassLoader.class.getDeclaredMethod("getParent"),
                denyOnExit());
        registry.put(
                ClassLoader.class.getDeclaredMethod("getPlatformClassLoader"),
                denyOnExit());
        registry.put(
                ClassLoader.class.getDeclaredMethod("getSystemClassLoader"),
                denyOnExit());
    }
    

    private class DenyOnExit extends ExitInsert {
        @Override
        public void onExitImpl(Hook hook, Object result) {
            boolean granted = false;
            if (result != null) {
                Class<?> caller = Helper.getCallerClass();
                if (caller != null) {
                    ClassLoader ccl = caller.getClassLoader();
                    granted = ccl == null || ccl == result;
                } else {
                    granted = true;
                }
            } else {
                granted = true;
            }

            if (granted) {
                String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
                log(VerboseCategory.PERMIT, msg);
            } else {
                String msg = getCheckName()+ " is not granted";
                Helper.denyInvocation(hook, null, msg, this);
            }
        }
    }
    public DenyOnExit denyOnExit() {
        return new DenyOnExit();
    }

    

    private class DenyOnUnprivilegedMethodHandlesLookup extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            MethodHandles.Lookup lookup = (MethodHandles.Lookup)hook.target();
            if (lookup.hasFullPrivilegeAccess()) {
                String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
                log(VerboseCategory.PERMIT, msg);
                return;
            }
            String msg = getCheckName()+ " is not granted";
            Helper.denyInvocation(hook, null, msg, this);
        }
    }
    public DenyOnUnprivilegedMethodHandlesLookup denyOnUnprivilegedMethodHandlesLookup() {
        return new DenyOnUnprivilegedMethodHandlesLookup();
    }
    private class DenyOnMethodTypeFromMethodDescriptorString extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            ClassLoader cl = (ClassLoader)hook.arg(1);
            if (cl == null) {
                String msg = getCheckName()+ " is not granted";
                Helper.denyInvocation(hook, null, msg, this);
                return;
            }
            String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
            log(VerboseCategory.PERMIT, msg);
        }
    }
    public DenyOnMethodTypeFromMethodDescriptorString denyOnMethodTypeFromMethodDescriptorString() {
        return new DenyOnMethodTypeFromMethodDescriptorString();
    }

    

    private class DenyCallerModuleDifferentFromSpecifiedModule extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            Class<?> caller = Helper.getCallerClass();
            if (caller == null) return;

            Module module = null;
            for (Object arg : hook.args()) {
                if (arg != null && arg instanceof Module) {
                    module = (Module)arg;
                    break;
                }
            }

            if (caller.getModule() != module) {
                String msg = getCheckName()+ " is not granted";
                Helper.denyInvocation(hook, null, msg, this);
                return;
            }
            String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
            log(VerboseCategory.PERMIT, msg);
        }
    }
    public DenyCallerModuleDifferentFromSpecifiedModule denyCallerModuleDifferentFromSpecifiedModule() {
        return new DenyCallerModuleDifferentFromSpecifiedModule();
    }


    private class DenySpecifiedLoaderIsNullAndCallerLoaderIsNotNull extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            ClassLoader loader = null;
            for (Object arg : hook.args()) {
                if (arg != null && arg instanceof ClassLoader) {
                    loader = (ClassLoader)arg;
                    break;
                }
            }

            if (loader == null) {
                Class<?> caller = Helper.getCallerClass();
                if (caller == null) return;
                ClassLoader ccl = caller.getClassLoader();
                if (ccl != null) {
                    String msg = getCheckName()+ " is not granted";
                    Helper.denyInvocation(hook, null, msg, this);
                    return;
                }
            }
            String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
            log(VerboseCategory.PERMIT, msg);
        }
    }
    public DenySpecifiedLoaderIsNullAndCallerLoaderIsNotNull denySpecifiedLoaderIsNullAndCallerLoaderIsNotNull() {
        return new DenySpecifiedLoaderIsNullAndCallerLoaderIsNotNull();
    }



}
