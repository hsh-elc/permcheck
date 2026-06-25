package de.hsh.permcheck.internal;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;

/**
 * <p>This class represents a specification like
 * <pre>
 * permit.XX NAME [ | ACTIONS]
 * </pre>
 * where the "| ACTIONS" are optional.</p>
 * <p>XX is the "checkName"</p>
 * <p>
 * The NAME can be any string and it must be matched by subclasses.
 * As part of the NAME you can specify placeholders for system properties. The syntax is
 * <code>${{property.name}}</code>. 
 * Properties will be replaced before matching. If the referenced system property cannot be resolved, an
 * exception is thrown.
 * </p>
 * <p>
 * The ACTIONS is a comma-separated list of actions from the 
 * set { read, write, execute, delete }.
 * </p>
 */
public abstract class AbstractPermitCheck extends AbstractCheck {

    private String permitKey;
    private UnaryOperator<String> nameMapper;
    private LinkedHashMap<String, Integer> permissions;

    public AbstractPermitCheck(String checkName, String activationKey, String permitKey, UnaryOperator<String> nameMapper) {
        super(checkName, activationKey);
        this.permitKey = permitKey;
        this.nameMapper = nameMapper;
    }

    /**
     * 
     * @param permission the NAME from the spec after property replacements
     * @param query a value that needs to be matched
     * @return
     */
    protected abstract boolean matches(String permission, String query);


    @Override
    public String getSpecName() {
        return "permit."+getCheckName();
    }

    @Override
    protected boolean processSpecImpl(String key, String value) {
        if (key.equals(permitKey)) {
            processPermit(value);
            return true;
        }
        return false;
    }

    protected void processPermit(String value) {
        if (!isActivated()) {
            throw new IllegalArgumentException(
                            "Should specify '"+getActivationKey()+"' before any '"+getActivationKey()+" ...'");            
        }

        String[] parts = value.split("\\|");
        if (parts.length < 1 || parts.length > 2) {
            throw new IllegalArgumentException("Illegal "+getSpecName()+" spec '"+value+"'. Should be separated into up to two fields with | as field separator.");
        }
        String name = nameMapper.apply(Helper.replaceSystemProperties(parts[0].trim()));
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Illegal "+getSpecName()+" spec '"+value+"'. first part NAME is missing.");
        }
        int actions;
        if (parts.length == 2) {
            String actionsStr = parts[1].trim();
            if (actionsStr.isEmpty()) {
                throw new IllegalArgumentException("Illegal "+getSpecName()+" spec '"+value+"'. second part ACTIONS is missing.");
            }
            actions = getMask(actionsStr);
            log(VerboseCategory.INSTALL, "[PERMCHECK] permitting "+getCheckName()+" actions "+actionsStr+" to "+name);
        } else {
            actions = Action.ACCESS.value();
            log(VerboseCategory.INSTALL, "[PERMCHECK] permitting "+getCheckName()+" to "+name);
        }
        addPermission(name, actions);
    }
    

    private void addPermission(String name, int mask) {
        if (permissions == null) permissions = new LinkedHashMap<>();
        if (permissions.containsKey(name)) {
            mask = mask | permissions.get(name);
        }
        permissions.put(name, mask);
    }
    protected void addPermission(String name, String actions) {
        addPermission(name, getMask(actions));
    }

    protected void printPermissions(PrintStream out) {
        for (String p : permissions.keySet()) {
            out.println(p + " -> " + permissions.get(p));
        }
    }


    private int getMask(String actions) {
        int mask = Action.NONE.value();
        String[] a = actions.split(",");
        for (String action : a) {
            action = action.trim();
            mask |= Action.of(action).value();
        }
        return mask;
    }


    private int getPermissions(String query) {
        int result = Action.NONE.value();
        if (this.permissions != null) {
            for (Entry<String, Integer> entry : this.permissions.entrySet()) {
                boolean match = false;
                String key = entry.getKey();
                match = matches(key, query);
                if (match) {
                    result |= entry.getValue();
                }
            }
        }
        return result;
    }

    private boolean isPermitted(String name, int singleAction) {
        int permit = getPermissions(name);
        return (permit & singleAction) != 0;
    }



    protected void check(String name, Hook hook) {
        checkAction(name, Action.ACCESS, hook, null);
    }


    protected void checkRead(String name, Hook hook) {
        checkAction(name, Action.READ, hook, null);
    }

    protected void checkWrite(String name, Hook hook) {
        checkAction(name, Action.WRITE, hook, null);
    }

    protected void checkDelete(String name, Hook hook) {
        checkAction(name, Action.DELETE, hook, null);
    }

    protected void checkExecute(String name, Hook hook) {
        checkAction(name, Action.EXECUTE, hook, null);
    }


    protected void check(String name, Hook hook, String explanation) {
        checkAction(name, Action.ACCESS, hook, explanation);
    }


    protected void checkRead(String name, Hook hook, String explanation) {
        checkAction(name, Action.READ, hook, explanation);
    }

    protected void checkWrite(String name, Hook hook, String explanation) {
        checkAction(name, Action.WRITE, hook, explanation);
    }

    protected void checkDelete(String name, Hook hook, String explanation) {
        checkAction(name, Action.DELETE, hook, explanation);
    }

    protected void checkExecute(String name, Hook hook, String explanation) {
        checkAction(name, Action.EXECUTE, hook, explanation);
    }

    protected void checkAction(String name, Action action, Hook hook) {
        checkAction(name, action, hook, null);
    }

    protected void checkAction(String name, Action action, Hook hook, String explanation) {
        if (!isPermitted(name, action.value())) {
            String msg = getCheckName()+(action == Action.ACCESS ? "" : " "+action.spec())+" is not granted for '"+name+"'";
            Helper.denyInvocation(hook, explanation, msg, this);
        } else {
            log(VerboseCategory.PERMIT, "[PERMCHECK] " + getCheckName()+(action == Action.ACCESS ? "" : " "+action.spec())+" is granted for "+name);
        }
    }
}
