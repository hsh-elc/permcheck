package de.hsh.permcheck.internal;

import java.lang.reflect.Executable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

public class Specs {

    private static Specs mySpecs;

    private LinkedHashMap<Executable, Insert> registry;

    private LinkedHashSet<String> untrustedClasses;

    private String[] privilege = new String[10];
    private int numPrivilege = 0;

    private int verbose;

    private void specVerbose(String val) {
        String[] specs = val.split(",");
        for (String spec : specs) {
            spec = spec.trim();
            try {
                if (spec.startsWith("no-")) {
                    int v = VerboseCategory.valueOf(spec.substring(3).toUpperCase()).val();
                    mySpecs.verbose &= ~v;
                } else {
                    int v = VerboseCategory.valueOf(spec.toUpperCase()).val();
                    mySpecs.verbose |= v;
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Illegal spec verbose with action '"+spec+"'");
            }
        }
    }

    private void addPrivilege(String spec) {
        if (numPrivilege == privilege.length) {
            String[] arr = new String[privilege.length * 2];
            System.arraycopy(privilege, 0, arr, 0, privilege.length);
            privilege = arr;
        }
        privilege[mySpecs.numPrivilege] = spec;
        numPrivilege++;
    }



    private Specs() {
        registry = new LinkedHashMap<>();
        untrustedClasses = new LinkedHashSet<>();
    }

    /**
     * 
     * @param policy
     * @throws IllegalArgumentException in case of an illegal policy format
     * @throws Error in case of an internal error
     */
    public static void setup(String policy) throws IllegalArgumentException {
        mySpecs = new Specs();

        AbstractCheck[] checks = {
            new DenyExitVmCheck(),
            new DenyReflectionSetAccessibleCheck(),
            new DenyReflectionAccessDeclaredMembersCheck(),
            new DenyReflectionGetStackTraceCheck(),
            new DenyReflectionGetStackWalkerWithClassReferenceCheck(),
            new DenyReflectionGetClassLoader(),
            new DenyReflectionCreateClassLoader(),
            new DenyReflectionAccessClassInNonExportedBootLayerPackage(),
            new PermitFileCheck(),
            new PermitPropertyCheck(),
            new PermitEnvCheck()
        };

        String[] rows = policy.split("\\R");

        int lineNo = 0;
        for (String row : rows) {
            lineNo++;
            if (row.startsWith("#") || row.startsWith("!"))
                continue;
            row = row.trim();
            if (row.isEmpty())
                continue;
            int index = row.indexOf(' ');
            if (index < 0)
                index = row.length();
            if (index == 0)
                throw new IllegalArgumentException("Illegal format of policy line " + lineNo + " '" + row + "'");
            String key = row.substring(0, index);
            String value = null;
            if (index < row.length())
                value = row.substring(index + 1);
            switch (key) {
                case "verbose":
                    mySpecs.specVerbose(value);
                    break;
                case "distrust.plain":
                    mySpecs.untrustedClasses.add(value);
                    break;
                case "distrust.regex":
                    mySpecs.untrustedClasses.add("^"+value+"$");
                    break;
                case "privilege":
                    mySpecs.addPrivilege(value);
                    break;
                default:
                    boolean processed = false;
                    for (AbstractCheck c : checks) {
                        if (processed = c.processSpec(key, value)) break;
                    }
                    if (!processed) {
                        throw new IllegalArgumentException("Unknown spec key '" + key + "'");
                    }
            }
        }

        for (AbstractCheck c : checks) {
            try {
                c.register(mySpecs.registry);
            } catch (Exception e) {
                throw new Error("Internal error in permcheck registration of '" + c.getCheckName() + "'");
            }
        }
    }       

    public static Iterable<Executable> getExecutables() {
        return mySpecs.registry.keySet();
    }

    public static Insert getInsert(Executable executable) {
        return mySpecs.registry.get(executable);
    }

    public static boolean isUntrustedClass(String clazz) {
        for (String uc : mySpecs.untrustedClasses) {
            if (uc.startsWith("^") && uc.endsWith("$")) {
                if (Pattern.matches(uc, clazz)) {
                    //System.out.println("Class "+clazz+" is untrusted");                    
                    return true;
                }
            } else if (uc.equals(clazz)) {
                //System.out.println("Class "+clazz+" is untrusted");                    
                return true;
            }
        }
        return false;
        //return mySpecs.untrustedClasses.contains(clazz);
    }

    public static boolean isPrivileged(String mcm) {
        for (String t : mySpecs.privilege) {
            if (t == null) break; // end of array
            if (t.equals(mcm)) {
                return true;
            }
        }
        return false;
    }

    public static boolean verboseInstall() {
        return include(VerboseCategory.INSTALL);
    }

    public static boolean verboseTransform() {
        return include(VerboseCategory.TRANSFORM);
    }

    public static boolean verbosePermit() {
        return include(VerboseCategory.PERMIT);
    }

    public static boolean verboseTrace() {
        return include(VerboseCategory.TRACE);
    }

    public static boolean include(VerboseCategory vc) {
        return (mySpecs.verbose & vc.val()) != 0;
    }

    public static String[] getPrivilege() {
        return mySpecs.privilege;
    }

}
