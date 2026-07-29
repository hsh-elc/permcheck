package de.hsh.permcheck.internal;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Specs {

    private static Specs mySpecs;

    private LinkedHashMap<Executable, ArrayList<Insert>> registry;

    /**
     * Contains either compiled Patterns or Strings:
     */
    private LinkedHashSet<Object> untrustedClasses;

    private LinkedHashMap<String, String> transformedClassesToBeWrittenToDirectory;

    private String password;
    private boolean isActive;

    /**
     * Contains either compiled Patterns or Strings:
     */
    private Object[] privilege = new Object[10];
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

    private void specDebugWriteTransformedClass(String key, String val) {
        String[] parts = val.split("\\|");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Illegal "+key+" spec '"+val+"'. Should be separated into two fields with | as field separator.");
        }
        String className = parts[0].trim();
        if (className.isEmpty()) {
            throw new IllegalArgumentException("Illegal "+key+" spec '"+val+"'. first part CLASSNAME is missing.");
        }
        String directory = parts[1].trim();
        if (directory.isEmpty()) {
            throw new IllegalArgumentException("Illegal "+key+" spec '"+val+"'. second part DIRECTORY is missing.");
        }
        if (transformedClassesToBeWrittenToDirectory.containsKey(className)) {
            throw new IllegalArgumentException("Illegal "+key+" spec '"+val+"'. first part CLASSNAME is duplicated.");
        }
        directory = Helper.replaceSystemProperties(directory);
        transformedClassesToBeWrittenToDirectory.put(className, directory);
    }

    private void addPrivilege(String spec) {
        if (numPrivilege == privilege.length) {
            Object[] arr = new Object[privilege.length * 2];
            System.arraycopy(privilege, 0, arr, 0, privilege.length);
            privilege = arr;
        }
        
        if (spec.startsWith("^") && spec.endsWith("$")) {
            privilege[mySpecs.numPrivilege] = Pattern.compile(spec);
        } else {
            privilege[mySpecs.numPrivilege] = spec;
        }
        numPrivilege++;
    }

    private void addUntrustedClass(String uc) {
        if (uc.startsWith("^") && uc.endsWith("$")) {
            untrustedClasses.add(Pattern.compile(uc));
        } else {
            untrustedClasses.add(uc);
        }
    }



    private Specs(String password) {
        registry = new LinkedHashMap<>();
        untrustedClasses = new LinkedHashSet<>();
        transformedClassesToBeWrittenToDirectory = new LinkedHashMap<>();
        isActive = true;
        this.password = password;
    }

    /**
     * 
     * @param policy
     * @param password an arbitrary password, that must be passed to {@link #pause(String)}
     *     and {@link #resume(String)}.
     * @throws IllegalArgumentException in case of an illegal policy format
     * @throws Error in case of an internal error
     */
    public static void setup(String policy, String password) throws IllegalArgumentException {
        mySpecs = new Specs(password);

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
                case "debug.writeTransformedClass":
                    mySpecs.specDebugWriteTransformedClass(key, value);
                    break;
                case "distrust.plain":
                    mySpecs.addUntrustedClass(value);
                    break;
                case "distrust.regex":
                    mySpecs.addUntrustedClass("^"+value+"$");
                    break;
                case "privilege.plain":
                    mySpecs.addPrivilege(value);
                    break;
                case "privilege.regex":
                    mySpecs.addPrivilege("^"+value+"$");
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

    public static ArrayList<Insert> getInserts(Executable executable) {
        return mySpecs.registry.get(executable);
    }

    public static List<String> getUntrustedClassRegexes() {
        ArrayList<String> result = new ArrayList<>();
        for (Object o : mySpecs.untrustedClasses) {
            if (o instanceof Pattern) {
                result.add(((Pattern)o).pattern());
            } else if (o instanceof String) {
                result.add("^"+(String)o+"$");
            } else {
                throw new Error("Internal error in permcheck: unexpected untrusteClass object of type " + (o==null?null:o.getClass()));
            }
        }
        return result;
    }

    public static boolean isUntrustedClass(String clazz) {
        for (Object uc : mySpecs.untrustedClasses) {
            if (uc instanceof Pattern) {
                if (((Pattern)uc).matcher(clazz).matches()) {
                    //System.out.println("Class "+clazz+" is untrusted");                    
                    return true;
                }
            } else if (uc.equals(clazz)) {
                //System.out.println("Class "+clazz+" is untrusted");                    
                return true;
            }
        }
        return false;
    }

    public static Map<String, String> getTransformedClassesToBeWrittenToDirectory() {
        return mySpecs.transformedClassesToBeWrittenToDirectory;
    }

    public static boolean isPrivileged(String mcm) {
        for (Object t : mySpecs.privilege) {
            if (t == null) break; // end of array
            if (t instanceof Pattern) {
                if (((Pattern)t).matcher(mcm).matches()) {
                    return true;
                }
            } else if (t.equals(mcm)) {
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
        if (mySpecs == null) throw new Error("Internal error in permcheck library: mySpecs is null");
        return (mySpecs.verbose & vc.val()) != 0;
    }

    public static void log(VerboseCategory vc, String msg) {
        if (Specs.include(vc)) {
            System.out.println(msg);
        }
    }

    public static Object[] getPrivilege() {
        return mySpecs.privilege;
    }

    /**
     * Deactivates permcheck functionality
     * @param password must be specified equal to the password passed to {@link #setup(String, String)}.
     */
    public static void pause(String password) throws IllegalArgumentException {
        if (mySpecs != null) {
            if (!mySpecs.isActive) return;
            if (mySpecs.password != null && mySpecs.password.equals(password))  {
                mySpecs.isActive = false;
            } else {
                throw new IllegalArgumentException("Illegal password passed to permcheck's pause method");
            }
        }
    }

    /**
     * Reactivates permcheck functionality
     * @param password must be specified equal to the password passed to {@link #setup(String, String)}.
     */
    public static void resume(String password) throws IllegalArgumentException{
        if (mySpecs != null) {
            if (mySpecs.isActive) return;
            if (mySpecs.password != null && mySpecs.password.equals(password))  {
                mySpecs.isActive = true;
            } else {
                throw new IllegalArgumentException("Illegal password passed to permcheck's resume method");
           }
        }
    }

    static boolean isActive() {
        if (mySpecs == null) return false;
        return mySpecs.isActive;
    }

    public static Boolean isActive(String password) throws IllegalArgumentException {
        if (mySpecs != null) {
            if (mySpecs.password != null && mySpecs.password.equals(password))  {
                return mySpecs.isActive;
            } else {
                throw new IllegalArgumentException("Illegal password passed to permcheck's isActive method");
           }
        }
        return null;
    }

}
