package de.hsh.permcheck.internal;

public class DenyReflectionGetStackTraceCheck extends AbstractDenyCheck {
    public DenyReflectionGetStackTraceCheck() {
        super("reflectionGetStackTrace", "deny.reflectionGetStackTrace");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        registry.put(
                Thread.class.getDeclaredMethod("getStackTrace"),
                denyNotCurrentThread());
        registry.put(
                Thread.class.getDeclaredMethod("getAllStackTraces"),
                deny());
    }


    private class DenyNotCurrentThread extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            Thread thread = getTarget(hook, Thread.class);
            if (thread == Thread.currentThread()) {
                String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
                log(VerboseCategory.PERMIT, msg);
                return;
            }

            String msg = getCheckName()+ " is not granted";
            Helper.denyInvocation(hook, null, msg, this);
        }
    }
    public DenyNotCurrentThread denyNotCurrentThread() {
        return new DenyNotCurrentThread();
    }

}
