package de.hsh.permcheck.internal;

public abstract class Insert implements Logger {


    /**
     * This method will be called only, if there is an untrusted class on the call stack and
     * no privilege method in between.
     * @param hook
     */
    public final void onEnter(Hook hook) {
        if (Specs.verboseTrace()) {
            System.out.println("[PERMCHECK] ----PermCheckLogger------------------------------");
            StringBuilder msg = new StringBuilder();
            msg.append(hook.pretty());
            System.out.println("[PERMCHECK] "+ msg.toString());

            System.out.println("[PERMCHECK] Stacktrace:");
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                System.out.println("[PERMCHECK] \t" + e);
            }
        }
        onEnterImpl(hook);
    }

    public abstract void onEnterImpl(Hook hook);

    public final void onExit(Hook hook, Object result) {
        onExitImpl(hook, result);
    }

    public abstract void onExitImpl(Hook hook, Object result);

    @Override
    public void log(VerboseCategory vc, String msg) {
        if (Specs.include(vc)) {
            System.out.println(msg);
        }
    }

    @Override
    public boolean include(VerboseCategory vc) {
        return Specs.include(vc);
    }
}
