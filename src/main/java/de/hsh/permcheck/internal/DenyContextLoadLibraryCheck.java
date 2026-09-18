package de.hsh.permcheck.internal;

public class DenyContextLoadLibraryCheck extends AbstractDenyCheck {

    public DenyContextLoadLibraryCheck() {
        super("contextLoadLibrary", "deny.contextLoadLibrary");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        registry.put(
            System.class.getDeclaredMethod("load", String.class), deny());
        registry.put(
            System.class.getDeclaredMethod("loadLibrary", String.class), deny());
        registry.put(
            Runtime.class.getDeclaredMethod("load", String.class), deny());
        registry.put(
            Runtime.class.getDeclaredMethod("loadLibrary", String.class), deny());

    }


}
