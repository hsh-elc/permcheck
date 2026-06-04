package de.hsh.permcheck.internal;

import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.util.Map;

public abstract class AbstractCheck implements Logger {
    private String checkName;
    private String activationKey;
    private boolean activated;


    public AbstractCheck(String checkName, String activationKey) {
        this.checkName = checkName;
        this.activationKey = activationKey;
    }

    public String getCheckName() {
        return checkName;
    }

    public abstract String getSpecName();

    public void log(VerboseCategory vc, String msg) {
        if (Specs.include(vc)) {
            System.out.println(msg);
        }
    }

    protected String getActivationKey() {
        return activationKey;
    }

    protected void processActivate() {
        if (isActivated()) {
            throw new IllegalArgumentException(
                    "Shouldn't specify twice '"+activationKey+"'");
        }
        String msg = "[PERMCHECK] denying " + getCheckName();
        log(VerboseCategory.PERMIT, msg);
        activated = true;
    }

    protected boolean isActivated() {
        return activated;
    }

    abstract boolean processSpecImpl(String key, String value);

    protected boolean processSpec(String key, String value) {
        if (key.equals(getActivationKey())) {
            processActivate();
            return true;
        }
        return processSpecImpl(key, value);
    }

    
    protected abstract void registerImpl(Map<Executable, Insert> registry) throws Exception;


    void register(Map<Executable, Insert> registry) throws Exception {
        if (isActivated()) {
            registerImpl(registry);
        }
    }

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

    protected static <T> T getFirstArg(Hook hook, Class<T> clazz) {
            if (hook.args() == null) {
                throw new IllegalArgumentException("Expected args, but found null");
            }
            if (hook.args().length < 1) {
                throw new IllegalArgumentException("Expected at least 1 arg, but found 0");
            }
            Object arg = hook.args()[0];
            if (arg == null) {
                throw new IllegalArgumentException("Expected first arg of type "+clazz+", but found null");
            }
            if (! (wrap(clazz).isAssignableFrom(wrap(arg.getClass())))) {
                throw new IllegalArgumentException("Expected first arg of type "+clazz+", but found object of type '"+arg.getClass()+"'");
            }
            @SuppressWarnings("unchecked")
            T result = (T)arg;
            return result;

    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> wrap(Class<T> c) {
        return (Class<T>) MethodType.methodType(c).wrap().returnType();
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> unwrap(Class<T> c) {
        return (Class<T>) MethodType.methodType(c).unwrap().returnType();
    }

}
