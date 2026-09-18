package de.hsh.permcheck.internal;

import java.io.InputStream;
import java.io.PrintStream;

public class DenyContextSetIOCheck extends AbstractDenyCheck {

    public DenyContextSetIOCheck() {
        super("contextSetIO", "deny.contextSetIO");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        registry.put(
            System.class.getDeclaredMethod("setOut", PrintStream.class), deny());
        registry.put(
            System.class.getDeclaredMethod("setErr", PrintStream.class), deny());
        registry.put(
            System.class.getDeclaredMethod("setIn", InputStream.class), deny());

    }


}
