package de.hsh.permcheck.internal;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.StackWalker.StackFrame;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Scanner;

public class Helper {

    

    public static void denyInvocation(Hook hook, Logger log) throws PermcheckException {
        denyInvocation(hook, null, null, log);
    }

    public static void denyInvocation(Hook hook, String explanation, Logger log) throws PermcheckException {
        denyInvocation(hook, explanation, null, log);
    }

    public static void denyInvocation(Hook hook, String explanation, String cause, Logger log) throws PermcheckException {
        StringBuilder msg = new StringBuilder();
        msg.append("Denied invocation of ");
        msg.append(hook.pretty());
        if (explanation != null) {
            msg.append(" (").append(explanation).append(")");
        }
        if (cause != null) {
            msg.append(" Cause: ").append(cause);
        }

        if (log != null) {
            VerboseCategory level= VerboseCategory.TRACE;
            if (log.include(level)) {
                log.log(level, "[PERMCHECK] " + cause);
                StringWriter sw = new StringWriter();
                new Throwable().printStackTrace(new PrintWriter(sw));
                String s = sw.toString();
                try (Scanner sc = new Scanner(s)) {
                    sc.nextLine();
                    log.log(level, "[PERMCHECK] Trace");
                    while (sc.hasNextLine()) {
                        log.log(level, "[PERMCHECK] " + sc.nextLine());
                    }
                }
            }
        }

        throw new PermcheckException(msg.toString());

    }

    public static Class<?> getCallerClass() throws IllegalCallerException {
        String myPkg = Helper.class.getPackageName();
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

        StackFrame frame = walker.walk(streamOfStackframes -> {
            Optional<StackFrame> innerFrame = 
                streamOfStackframes
                .dropWhile(sf -> sf.getDeclaringClass().getPackageName().equals(myPkg))
                .skip(1)
                .findFirst();
            if (innerFrame.isPresent()) return innerFrame.get();
            return null;
        });
        if (frame == null) throw new IllegalCallerException();
        return frame.getDeclaringClass();
    }

    static String replaceSystemProperties(String str) {
        String origStr = str;
        int i0, i1;
        while ((i0 = str.indexOf("${{")) >= 0) {
            i1 = str.indexOf("}}", i0);
            if (i1 < i0) {
                throw new IllegalArgumentException("Illegal property placeholder in spec '"+origStr+"'");
            }
            String propKey = str.substring(i0+3, i1);
            if (propKey.isBlank()) {
                throw new IllegalArgumentException("Illegal property placeholder in spec '"+origStr+"'");
            }
            String propVal = System.getProperty(propKey);
            if (propVal == null) {
                throw new IllegalArgumentException("Unresolved property placeholder '"+propKey+"' in spec '"+origStr+"'");
            }
            str = str.substring(0, i0) + propVal + str.substring(i1+2);
        }
        return str;
    }

    public static Method getDeclaredMethodOfClassOrSuperClass(Class<?> clazz, String name, Class<?> ... parameterTypes) throws NoSuchMethodException {
        try {
            return clazz.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            Class<?> superClazz = clazz.getSuperclass();
            if (superClazz == null) throw new NoSuchMethodException("No superclass found for '"+clazz+"'");
            return superClazz.getDeclaredMethod(name, parameterTypes);
        }
    }

}
