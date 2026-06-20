package subm;
public class Submission {
    private static final double CLASS_OFFSET;
    private double offset;

    static {
        System.out.println("Submission static initalizer");
        CLASS_OFFSET = 0.1;
    }

    private void init(double offset) {
        this.offset = add(offset, add(CLASS_OFFSET,  -0.1));
    }
    public Submission(boolean shouldThrow) {
        //super();
        System.out.println("Submission boolean param constructor");
        if (shouldThrow)
            throw new RuntimeException("Runtimeexception forced in Submission double param constructor");
        init(1.0);
    }
    public Submission(double offset) {
        //super();
        System.out.println("Submission double param constructor");
        init(offset);
    }
    public Submission() {
        this(0.0);
        System.out.println("Submission default constructor");
        init(1.0);
    }
    public double sqrt(double x) {
        // Uncomment one of the following lines to make the grading fail:
        //System.getProperty("user.dir"); // attack
        //System.exit(0); // attack
        return add(offset, Math.sqrt(x));
    }

    private static double add(double a, double b) {
        return a+b;
    }
}
