package de.hsh.permcheck.internal;

import java.lang.module.Configuration;
import java.util.List;

public class DenyReflectionCreateClassLoader extends AbstractDenyCheck {

    public DenyReflectionCreateClassLoader() {
        super("reflectionCreateClassLoader", "deny.reflectionCreateClassLoader");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        registry.put(
                ClassLoader.class.getDeclaredConstructor(),
                deny());
        registry.put(
                ClassLoader.class.getDeclaredConstructor(ClassLoader.class),
                deny());
        registry.put(
                ClassLoader.class.getDeclaredConstructor(String.class, ClassLoader.class),
                deny());

        registry.put(
                ModuleLayer.class.getDeclaredMethod("defineModulesWithManyLoaders", Configuration.class, List.class, ClassLoader.class),
                deny());
        registry.put(
                ModuleLayer.class.getDeclaredMethod("defineModulesWithOneLoader", Configuration.class, List.class, ClassLoader.class),
                deny());
    }
}
