package de.hsh.permcheck.internal;

import java.lang.reflect.Executable;
import java.util.Map;

public class PermitEnvCheck extends BasicPermitCheck {
    
    public PermitEnvCheck() {
        super("env", "deny.envExceptSpecifiedPermissions", "permit.env");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        // permit.env NAME
        registry.put(System.class.getDeclaredMethod("getenv", String.class), firstArg(Action.ACCESS));

        // permit.env *
        registry.put(System.class.getDeclaredMethod("getenv"), any(Action.ACCESS));
        registry.put(ProcessBuilder.class.getDeclaredMethod("environment"), any(Action.ACCESS));
    }
}
