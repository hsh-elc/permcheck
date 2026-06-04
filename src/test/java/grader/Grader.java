package grader;

import java.lang.reflect.InvocationTargetException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class Grader {

    public static Class<?> submission;

    @BeforeClass
    public static void init() throws ClassNotFoundException {
        submission = Class.forName("subm.Submission");
    }

    @Test
    public void test() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException {
        double x = 5.0;
        double expected = Math.sqrt(x);
        double observed = (double)submission.getMethod("sqrt", double.class).invoke(null, x);
        Assert.assertEquals(expected, observed, 0.00000001);
    }
}
