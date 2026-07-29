package grader;

import org.junit.Assert;
import org.junit.Test;

import main.TestCase;


public class TestRunner {

    public static TestCase tc;

    @Test
    public void test() throws NoSuchMethodException, IllegalAccessException {
        double x = 5.0;
        double expected = Math.sqrt(x);
        double observed;
        Throwable observedException = null;
        String observedMsg = null;
        boolean exceptionTypeOk;
        boolean msgOk;
        try {
            observed = tc.apply(x);
            Assert.assertEquals(expected, observed, 0.00000001);

            exceptionTypeOk = tc.expectedException() == null;
            msgOk = tc.expectedMsgPattern() == null;

        } catch (Throwable e) {
            observedException = e;
            exceptionTypeOk =  tc.expectedException() == observedException.getClass();

            observedMsg = observedException.getMessage();
            if (tc.expectedMsgPattern() == null) {
                msgOk = observedMsg == null;
            } else {
                msgOk = observedMsg != null && observedMsg.matches("^"+tc.expectedMsgPattern()+"$");
            }
        }
        if (!exceptionTypeOk) {
            System.err.println(tc.nameAndComment()+": unexpected exception type '" + (observedException == null ? "null" : observedException.getClass()) + "', expected: '" + tc.expectedException() + "'");
        }
        if (!msgOk) {
            System.err.println(tc.nameAndComment()+": unexpected exception message '" + observedMsg + "', expected pattern: '^" + tc.expectedMsgPattern() + "$'");
        }

        if (exceptionTypeOk && msgOk) return;

        if (observedException != null) {
            observedException.printStackTrace(System.err);
        }
        throw new AssertionError("Test run aborted.");    
    }
}
