package de.hsh.permcheck.internal;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.StackWalker.StackFrame;
import java.util.Optional;
import java.util.Scanner;

public class Helper {

    

    public static void denyInvocation(Hook hook, Logger log) {
        denyInvocation(hook, null, null, log);
    }

    public static void denyInvocation(Hook hook, String explanation, Logger log) {
        denyInvocation(hook, explanation, null, log);
    }

    public static void denyInvocation(Hook hook, String explanation, String cause, Logger log) {
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

        throw new Error(msg.toString());

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

}
