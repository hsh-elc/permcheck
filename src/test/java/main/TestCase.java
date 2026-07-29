package main;

import java.util.function.UnaryOperator;

/**
 * A TestCase mimicks as student submission with code, that is potentially malicious.
 * Each testcase's apply method will be invoked by the {@link grader.Grader} class, which implements
 * a single test method {@link grader.Grader#test()}.
 */
public abstract class TestCase implements UnaryOperator<Double> {
    private String comment;
    private Class<? extends Throwable> expectedException;
    private String expectedMsgPattern;


    /**
     * leaves all attributes null
     */
    public TestCase() {
    }

    /**
     * 
     * @param expectedException declares, which Exception type is expected (or null, if none is expected)
     * @param expectedMsgPattern declares a regex of the expected exception message (or null, if none is expected)
    */
    public TestCase(Class<? extends Throwable> expectedException, String expectedMsgPattern) {
        this.expectedException = expectedException;
        this.expectedMsgPattern = expectedMsgPattern;
    }

    /**
     * 
     * @param comment comment for the test case
    */
    public TestCase(String comment) {
        this.comment = comment;
    }

    /**
     * 
     * @param comment comment for the test case
     * @param expectedException declares, which Exception type is expected (or null, if none is expected)
     * @param expectedMsgPattern declares a regex of the expected exception message (or null, if none is expected)
    */
    public TestCase(Class<? extends Throwable> expectedException, String expectedMsgPattern, String comment) {
        this.comment = comment;
        this.expectedException = expectedException;
        this.expectedMsgPattern = expectedMsgPattern;
    }

    /**
     * implements the submission
     */
    public abstract Double apply(Double d);

    public String nameAndComment() {
        return getClass().getName() 
            + (comment == null ? "" : " [" + comment + "]");
    }
    
    public Class<? extends Throwable> expectedException() { 
        return expectedException; 
    }
    
    public String expectedMsgPattern() { 
        return expectedMsgPattern; 
    }

    protected void setExpectedException(Class<? extends Throwable> e) {
        expectedException = e;
    }

    protected void setExpectedMsgPattern(String m) {
        expectedMsgPattern= m;
    }

}
