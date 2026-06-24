package subm;

import java.util.concurrent.Callable;

public class Submission extends SuperSubmission  {
    private static final double CLASS_OFFSET;
    private double offset;
    private static int staticVar;

    static {
        staticVar = new java.util.Random().nextInt();
        System.out.println("Submission static initalizer 1");
        CLASS_OFFSET = 0.1;
    }

    private void init(double offset) {
        this.offset = add(offset, add(CLASS_OFFSET,  -0.1));
    }
    public Submission(boolean shouldThrow) {
        super();
        System.out.println("Submission boolean param constructor");
        if (shouldThrow)
            throw new RuntimeException("Runtimeexception forced in Submission double param constructor");
        init(1.0);
    }
    public Submission(double offset) {
        super();
        System.out.println("Submission double param constructor");
        init(offset);
    }
    private Submission(int offset) {
        System.out.println("Submission int param constructor");
        init(offset);
    }
    public Submission(byte offset) {
        this(
            // attack:
            //System.getProperty("user.dir").length()/1000000+offset
            (double)offset
        );
        System.out.println("Submission byte param constructor");
    }
    public Submission(short offset) {
        this(Integer.parseInt(new String(makeStringFrom(offset))));
        System.out.println("Submission short param constructor");
    }
    public Submission(long offset) {
        this(new Callable<Double>() {
            @Override
            public Double call() {
                // Uncomment one of the following lines to make the grading fail:
                //System.getProperty("user.dir"); // attack
                return (double)offset;
            }
        }.call());
        System.out.println("Submission short param constructor");
    }
    public Submission(String offset) {
        this(Integer.parseInt(offset));
        System.out.println("Submission String param constructor");
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

    private static String makeStringFrom(short s) {
        // System.getProperty("user.dir"); // attack, hidden in a private static method that is called via constructor chaining
        return "" + s;
    }

    private static double add(double a, double b) {
        return a+b;
    }

    static {
        System.out.println("Submission static initalizer 2");
        if (Math.random() > 1) throw new RuntimeException("Should never occur");
    }


}
