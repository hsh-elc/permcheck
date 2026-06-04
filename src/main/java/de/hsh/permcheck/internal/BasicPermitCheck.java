package de.hsh.permcheck.internal;

/**
 * <p>This class resembles a permission of the kind java.security.BasicPermission, i. e. a specification like
 * <pre>
 * permit.XX NAME [ | ACTIONS]
 * </pre>
 * where the "| ACTIONS" are optional.</p>
 * <p>XX is the "checkName"</p>
 * <p>
 * The NAME can contain wildcards. An asterisk may appear by itself, or if immediately preceded by a 
 * "." may appear at the end of the name, to signify a wildcard match. For example, "*" and 
 * "java.*" signify a wildcard match, while "*java", "a*b", and "java*" do not. 
 * </p>
 * <p>
 * The ACTIONS is a comma-separated list of actions from the 
 * set { read, write, execute, delete }.
 * </p>
 */
public abstract class BasicPermitCheck extends AbstractPermitCheck {

    public BasicPermitCheck(String checkName, String activationKey, String permitKey) {
        super(checkName, activationKey, permitKey, name -> name);
    }


    /**
     * @param name
     * @return an array of length 2 with a the following meaning: element 0 is a boolean, which s true, if it is a wildcard,
     *         element 1 is a String containing the path
     */
    private static Object[] getWp(String name) {
        // This code is a modified version of code from java.security.BasicPermission
        int len = name.length();
        // Is wildcard or ends with ".*"?
        String path = name;
        boolean wildcard = name.charAt(len - 1) == '*' 
                           && (len == 1 || name.charAt(len - 2) == '.');
        if (wildcard) {
            path = name.substring(0, len - 1);
        } else {
            path = name;
        }
        return new Object[]{wildcard, path};
    }

    @Override
    protected boolean matches(String permission, String query) {
        // This code is a modified version of code from java.security.BasicPermission
        Object[] wpPermission = getWp(permission);
        Object[] wpQuery = getWp(query);

        boolean permissionWildcard = (boolean)wpPermission[0];
        boolean queryWildcard = (boolean)wpQuery[0];
        String permissionPath = (String)wpPermission[1];
        String queryPath = (String)wpQuery[1];
        if (permissionWildcard) {
            if (queryWildcard) {
                return queryPath.startsWith(permissionPath);
            } else {
                return (queryPath.length() > permissionPath.length()) &&
                    queryPath.startsWith(permissionPath);
            }
        } else {
            if (queryWildcard) {
                // a non-wildcard can't imply a wildcard
                return false;
            } else {
                return permissionPath.equals(queryPath);
            }
        }
    }

    private class FirstArgInsert extends ActionInsert {
        private FirstArgInsert(Action ... actions) {
            super(BasicPermitCheck.this, actions);
        }
        @Override
        protected String getName(Hook hook) {
            return hook.getStringFromFirstArg();
        }
    };
    public FirstArgInsert firstArg(Action ... actions) {
        return new FirstArgInsert(actions);
    }

    private class AnyInsert extends ActionInsert {
        private AnyInsert(Action ... actions) {
            super(BasicPermitCheck.this, actions);
        }
        @Override
        protected String getName(Hook hook) {
            return "*";
        }
    };

    public AnyInsert any(Action ... actions) {
        return new AnyInsert(actions);
    }


}
