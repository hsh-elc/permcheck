package de.hsh.permcheck.internal;


import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup.ClassOption;

public class DenyReflectionDefineClassCheck extends AbstractDenyCheck {

    public DenyReflectionDefineClassCheck() {
        super("reflectionDefineClass", "deny.reflectionDefineClass");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
 
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("defineClass", byte[].class),
                new DenyOnMethodHandlesLessThanFullPrivilegesInsert() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("defineHiddenClass", byte[].class, boolean.class, ClassOption[].class),
                new DenyOnMethodHandlesLessThanFullPrivilegesInsert() );
        registry.put(
                MethodHandles.Lookup.class.getDeclaredMethod("defineHiddenClassWithClassData", byte[].class, Object.class, boolean.class, ClassOption[].class),
                new DenyOnMethodHandlesLessThanFullPrivilegesInsert() );
    }

    private class DenyOnMethodHandlesLessThanFullPrivilegesInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            MethodHandles.Lookup lookup = getTarget(hook, MethodHandles.Lookup.class);
            check(hook, lookup);
        }
    }

    private void check(Hook hook, MethodHandles.Lookup lookup) {
        boolean granted = false;
        granted = (lookup != null && lookup.hasFullPrivilegeAccess());

        if (granted) {
            String msg = "[PERMCHECK] " + getCheckName()+ " is granted";
            log(VerboseCategory.PERMIT, msg);
            return;
        }

        String msg = getCheckName()+ " is not granted";
        Helper.denyInvocation(hook, null, msg, this);
    }


}
