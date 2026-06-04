package de.hsh.permcheck.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class DenyReflectionSetAccessibleCheck extends AbstractDenyCheck {
    public DenyReflectionSetAccessibleCheck() {
        super("reflectionSetAccessible", "deny.reflectionSetAccessible");
    }

    @Override
    protected void registerImpl(Map<Executable, Insert> registry) throws Exception {
        registry.put(
                AccessibleObject.class.getDeclaredMethod("setAccessible", AccessibleObject[].class, boolean.class),
                deny());
        registry.put(
                AccessibleObject.class.getDeclaredMethod("setAccessible", boolean.class),
                deny());
        registry.put(
                AccessibleObject.class.getDeclaredMethod("trySetAccessible"),
                deny());
        registry.put(Method.class.getDeclaredMethod(
                "setAccessible", boolean.class), 
                deny());
        registry.put(
                Constructor.class.getDeclaredMethod("setAccessible", boolean.class),
                deny());
        registry.put(Field.class.getDeclaredMethod(
                "setAccessible", boolean.class), 
                deny());
        registry.put(
                MethodHandles.class.getDeclaredMethod("reflectAs", Class.class, MethodHandle.class),
                deny());
        registry.put(
                MethodHandles.class.getDeclaredMethod("privateLookupIn", Class.class, Lookup.class),
                deny());
    }
}
