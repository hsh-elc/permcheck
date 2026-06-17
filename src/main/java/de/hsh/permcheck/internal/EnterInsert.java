package de.hsh.permcheck.internal;

public abstract class EnterInsert extends Insert {

    @Override
    public abstract void onEnterImpl(Hook hook);

    @Override
    public void onExitImpl(Hook hook, Object result) {
        throw new UnsupportedOperationException("unexpected onExit on an EnterInsert");
    }
}
