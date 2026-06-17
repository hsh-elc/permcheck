package de.hsh.permcheck.internal;

public interface Logger {
    void log(VerboseCategory vc, String msg);
    boolean include(VerboseCategory vc);
}
