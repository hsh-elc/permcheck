package de.hsh.permcheck.internal;

import java.lang.StackWalker.Option;
import java.lang.reflect.Executable;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class DenyReflectionGetStackWalkerWithClassReferenceCheck extends AbstractDenyCheck {
    public DenyReflectionGetStackWalkerWithClassReferenceCheck() {
        super("reflectionGetStackWalkerWithClassReference", "deny.reflectionGetStackWalkerWithClassReference");
    }

    @Override
    protected void registerImpl(Map<Executable, Insert> registry) throws Exception {
        registry.put(
                StackWalker.class.getDeclaredMethod("getInstance", Option.class),
                denyDependingOnFirstArg(Option.class, option -> !option.equals(StackWalker.Option.RETAIN_CLASS_REFERENCE)));
        registry.put(
                StackWalker.class.getDeclaredMethod("getInstance", Set.class),
                denyDependingOnFirstArg(Set.class, set -> !set.contains(StackWalker.Option.RETAIN_CLASS_REFERENCE)));
        registry.put(
                StackWalker.class.getDeclaredMethod("getInstance", Set.class, int.class),
                denyDependingOnFirstArg(Set.class, set -> !set.contains(StackWalker.Option.RETAIN_CLASS_REFERENCE)));
    }


    private class DenyDependingOnFirstArg<T> extends Insert {

        private Class<T> argClass;
        private Predicate<T> isGranted;
        
        DenyDependingOnFirstArg(Class<T> argClass, Predicate<T> isGranted) {
            this.argClass = argClass;
            this.isGranted = isGranted;
        }
        @Override
        public void onEnterImpl(Hook hook) {
            T arg = getFirstArg(hook, argClass);
            if (isGranted.test(arg)) {
                String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
                log(VerboseCategory.PERMIT, msg);
                return;
            }

            String msg = getCheckName()+ " is not granted";
            Helper.denyInvocation(hook, null, msg, this);
        }
    }
    public <T> DenyDependingOnFirstArg<T> denyDependingOnFirstArg(Class<T> argClass, Predicate<T> isGranted) {
        return new DenyDependingOnFirstArg<T>(argClass, isGranted);
    }

}
