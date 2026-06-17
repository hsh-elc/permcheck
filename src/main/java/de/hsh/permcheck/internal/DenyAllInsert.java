package de.hsh.permcheck.internal;

public class DenyAllInsert extends EnterInsert {

    private String checkName;
    
    DenyAllInsert(String checkName) {
        this.checkName = checkName;
    }

    @Override
    public void onEnterImpl(Hook hook) {
        String msg = checkName+ " is not granted";
        Helper.denyInvocation(hook, null, msg, this);
    }


}
