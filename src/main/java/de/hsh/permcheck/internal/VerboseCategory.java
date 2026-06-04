package de.hsh.permcheck.internal;

enum VerboseCategory {
    INSTALL(0x1),
    TRANSFORM(0x2),
    PERMIT(0x4),
    TRACE(0x8);

    private int val;

    private VerboseCategory(int val) {
        this.val = val;
    }

    public int val() {
        return val;
    }
}