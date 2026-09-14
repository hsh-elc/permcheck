package de.hsh.permcheck.internal;

public class DenyThreadModifyRootOrMainGroupCheck extends AbstractDenyCheck {

    public DenyThreadModifyRootOrMainGroupCheck() {
        super("threadModifyRootOrMainGroup", "deny.threadModifyRootOrMainGroup");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        int feature = Runtime.version().feature();
        if (feature <= 22) {
            registry.put(
                    ThreadGroup.class.getDeclaredMethod("suspend"),
                    denyTargetIsRootOrMainThreadGroup() );
            registry.put(
                    ThreadGroup.class.getDeclaredMethod("resume"),
                    denyTargetIsRootOrMainThreadGroup() );
            registry.put(
                    ThreadGroup.class.getDeclaredMethod("stop"),
                    denyTargetIsRootOrMainThreadGroup() );
        }
        registry.put(
                ThreadGroup.class.getDeclaredMethod("interrupt"),
                denyTargetIsRootOrMainThreadGroup() );
        registry.put(
                ThreadGroup.class.getDeclaredMethod("destroy"),
                denyTargetIsRootOrMainThreadGroup() );
        registry.put(
                ThreadGroup.class.getDeclaredMethod("getParent"),
                denyTargetIsRootOrMainThreadGroup() );
        registry.put(
                ThreadGroup.class.getDeclaredMethod("setMaxPriority", int.class),
                denyTargetIsRootOrMainThreadGroup() );
        registry.put(
                ThreadGroup.class.getDeclaredMethod("setDaemon", boolean.class),
                denyTargetIsRootOrMainThreadGroup() );
        registry.put(
                ThreadGroup.class.getDeclaredMethod("enumerate", Thread[].class),
                denyTargetIsRootOrMainThreadGroup() );
        registry.put(
                ThreadGroup.class.getDeclaredMethod("enumerate", Thread[].class, boolean.class),
                denyTargetIsRootOrMainThreadGroup() );
        registry.put(
                ThreadGroup.class.getDeclaredMethod("enumerate", ThreadGroup[].class),
                denyTargetIsRootOrMainThreadGroup() );
        registry.put(
                ThreadGroup.class.getDeclaredMethod("enumerate", ThreadGroup[].class, boolean.class),
                denyTargetIsRootOrMainThreadGroup() );
        registry.put(
                ThreadGroup.class.getDeclaredConstructor(ThreadGroup.class, String.class),
                denyFirstArgIsRootOrMainThreadGroup() );
    }

    private static ThreadGroup rootGroup, mainGroup;
    static {
        rootGroup = Helper.getRootThreadGroup();
        mainGroup = Helper.getMainThreadGroup();
    }

    private class DenyTargetIsRootOrMainThreadGroupInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            ThreadGroup group = getTarget(hook, ThreadGroup.class);
            check(group, hook);
        }
    }
    public DenyTargetIsRootOrMainThreadGroupInsert denyTargetIsRootOrMainThreadGroup() {
        return new DenyTargetIsRootOrMainThreadGroupInsert();
    }

    private class DenyFirstArgIsRootOrMainThreadGroupInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            ThreadGroup group = getFirstArg(hook, ThreadGroup.class);
            check(group, hook);
        }
    }
    public DenyFirstArgIsRootOrMainThreadGroupInsert denyFirstArgIsRootOrMainThreadGroup() {
        return new DenyFirstArgIsRootOrMainThreadGroupInsert();
    }

    private void check(ThreadGroup group, Hook hook) {
        if (group != rootGroup && group != mainGroup) {
            String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
            log(VerboseCategory.PERMIT, msg);
            return;
        }

        String msg = getCheckName()+ " is not granted";
        Helper.denyInvocation(hook, null, msg, this);
    }

}
