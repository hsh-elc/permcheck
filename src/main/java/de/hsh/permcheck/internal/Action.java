package de.hsh.permcheck.internal;

public enum Action {
    NONE(0x0, true), 
    EXECUTE(0x1, false), 
    WRITE(0x2, false), 
    READ(0x4, false), 
    DELETE(0x8, false), 
    ACCESS(0x10, true);
    private int val;
    private boolean hidden; // true = not to be specified in Specs
    private Action(int v, boolean h) {
        this.val = v;
        this.hidden = h;
    }
    public int value() {
        return val;
    }
    public boolean isHidden() {
        return hidden;
    }
    public String spec() {
        return name().toLowerCase();
    }
    public static Action of(String spec) {
        if (spec != null) {
            for (Action a : values()) {
                if (a.spec().equals(spec.trim().toLowerCase())) {
                    if (a.isHidden()) {
                        throw new IllegalArgumentException("Illegal action '"+spec+"'");
                    }
                    return a;
                }
            }
        }
        throw new IllegalArgumentException("Illegal action '"+spec+"'");
    }
}
