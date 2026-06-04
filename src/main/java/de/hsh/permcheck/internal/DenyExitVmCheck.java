package de.hsh.permcheck.internal;

import java.lang.reflect.Executable;
import java.util.Map;

public class DenyExitVmCheck extends AbstractDenyCheck {

    public DenyExitVmCheck() {
        super("exitVm", "deny.exitVm");
    }

    @Override
    protected void registerImpl(Map<Executable, Insert> registry) throws Exception {
        registry.put(System.class.getDeclaredMethod("exit", int.class), deny());
        registry.put(Runtime.class.getDeclaredMethod("exit", int.class), deny());
        registry.put(Runtime.class.getDeclaredMethod("halt", int.class), deny());
    }

}
