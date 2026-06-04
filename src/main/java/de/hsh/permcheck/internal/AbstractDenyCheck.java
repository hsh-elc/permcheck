package de.hsh.permcheck.internal;

public abstract class AbstractDenyCheck extends AbstractCheck {

    private DenyAllInsert insert;


    public AbstractDenyCheck(String checkName, String activationKey) {
        super(checkName, activationKey);
        String msg = "[PERMCHECK] denying " + checkName;
        log(VerboseCategory.PERMIT, msg);
        insert = new DenyAllInsert(checkName);
    }

    @Override
    public String getSpecName() {
        return "deny."+getCheckName();
    }

    public Insert deny() {
        return insert;
    }

    @Override
    protected boolean processSpecImpl(String key, String value) {
        // do nothing, since value is null.
        return false;
    }

}
