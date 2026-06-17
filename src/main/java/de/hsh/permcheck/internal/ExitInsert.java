package de.hsh.permcheck.internal;

public abstract class ExitInsert extends Insert {

    @Override
    public void onEnterImpl(Hook hook) {
        throw new UnsupportedOperationException("unexpected onEnter on an ExitInsert");
    }

    @Override
    public abstract void onExitImpl(Hook hook, Object result);
}
