package de.hsh.permcheck.internal;

import java.util.concurrent.ForkJoinPool;

public class DenyThreadModifyCheck extends AbstractDenyCheck {

    public DenyThreadModifyCheck() {
        super("threadModify", "deny.threadModify");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
 
        registry.put(
                ForkJoinPool.class.getDeclaredConstructor(int.class),
                deny() );
        registry.put(
                ForkJoinPool.class.getDeclaredMethod("shutdown"),
                deny() );
        registry.put(
                ForkJoinPool.class.getDeclaredMethod("shutdownNow"),
                deny() );
    }


}
