package de.hsh.permcheck.internal;

public class DenyThreadStopCheck extends AbstractDenyCheck {

    public DenyThreadStopCheck() {
        super("threadStop", "deny.threadStop");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
 
        registry.put(
                Thread.class.getDeclaredMethod("stop"),
                denyOnTargetIsNotCurrentThread() );
    }
    private class DenyOnTargetIsNotCurrentThreadInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            Thread target = getTarget(hook, Thread.class);
            if (target == Thread.currentThread()) {
                String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
                log(VerboseCategory.PERMIT, msg);
                return;
            }
            String msg = getCheckName()+ " is not granted";
            Helper.denyInvocation(hook, null, msg, this);
        }
    }
    public DenyOnTargetIsNotCurrentThreadInsert denyOnTargetIsNotCurrentThread() {
        return new DenyOnTargetIsNotCurrentThreadInsert();
    }


}
