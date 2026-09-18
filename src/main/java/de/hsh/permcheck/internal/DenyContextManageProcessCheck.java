package de.hsh.permcheck.internal;

import java.io.File;

public class DenyContextManageProcessCheck extends AbstractDenyCheck {

    public DenyContextManageProcessCheck() {
        super("contextManageProcess", "deny.contextManageProcess");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        // This process should work on any platform:
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder pb = new ProcessBuilder(javaBin, "--version");
        Process process = pb.start();
        Class<? extends Process> processClass = process.getClass();
        ProcessHandle processHandle = process.toHandle();
        Class<? extends ProcessHandle> processHandleClass = processHandle.getClass();

        registry.put(
            processClass.getDeclaredMethod("toHandle"), deny());

        registry.put(
            ProcessHandle.class.getDeclaredMethod("of", long.class), deny()); 
        registry.put(
            ProcessHandle.class.getDeclaredMethod("allProcesses"), deny());

        registry.put(
            processHandleClass.getDeclaredMethod("current"), deny());
        registry.put(
            processHandleClass.getDeclaredMethod("parent"), deny());
        registry.put(
            processHandleClass.getDeclaredMethod("children"), deny());
        registry.put(
            processHandleClass.getDeclaredMethod("descendants"), deny());
    }


}
