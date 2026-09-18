package de.hsh.permcheck.internal;


public class DenyShutdownHooksCheck extends AbstractDenyCheck {

    public DenyShutdownHooksCheck() {
        super("shutdownHooks", "deny.shutdownHooks");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        registry.put(Runtime.class.getDeclaredMethod("addShutdownHook", Thread.class), deny());
        registry.put(Runtime.class.getDeclaredMethod("removeShutdownHook", Thread.class), deny());
    }

}
