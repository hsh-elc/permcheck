package de.hsh.permcheck.internal;

import java.awt.Desktop;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitStrategy;

import javax.swing.JFrame;

public class DenyShutdownExitVmCheck extends AbstractDenyCheck {

    public DenyShutdownExitVmCheck() {
        super("shutdownExitVm", "deny.shutdownExitVm");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        registry.put(System.class.getDeclaredMethod("exit", int.class), deny());
        registry.put(Runtime.class.getDeclaredMethod("exit", int.class), deny());
        registry.put(Runtime.class.getDeclaredMethod("halt", int.class), deny());

        registry.put(JFrame.class.getDeclaredMethod("setDefaultCloseOperation", int.class), denyOnExitOnClose());
        
        registry.put(Desktop.class.getDeclaredMethod("enableSuddenTermination"), deny());
        registry.put(Desktop.class.getDeclaredMethod("disableSuddenTermination"), deny());
        registry.put(Desktop.class.getDeclaredMethod("setQuitHandler", QuitHandler.class), deny());
        registry.put(Desktop.class.getDeclaredMethod("setQuitStrategy", QuitStrategy.class), deny());

    }

    private class DenyOnExitOnCloseInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            int operation = getFirstArg(hook, int.class);
            if (operation != JFrame.EXIT_ON_CLOSE) {
                String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
                log(VerboseCategory.PERMIT, msg);
                return;
            }
            String msg = getCheckName()+ " is not granted";
            Helper.denyInvocation(hook, null, msg, this);
        }
    }
    public DenyOnExitOnCloseInsert denyOnExitOnClose() {
        return new DenyOnExitOnCloseInsert();
    }



}
