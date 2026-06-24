package grader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class Grader {

    public static Class<?> clazz;

    @BeforeClass
    public static void init() throws ClassNotFoundException, NoSuchMethodException, SecurityException {
        clazz = Class.forName("subm.Submission");
    }

    @Test
    public void testBooleanParamConstructor() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException {
        System.out.println("testBooleanParamConstructor(true)");
        Constructor<?> booleanParamConstructor = clazz.getConstructor(boolean.class);        
        try {
            booleanParamConstructor.newInstance(true); // should throw RuntimeException
            throw new AssertionError("Submission(true) should throw RuntimeException");
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                System.out.println("Received an expected RuntimeException");
                // expected
            } else {
                throw new AssertionError("Submission(true) should throw RuntimeException. Observed: "+e.getCause());
            }
        }
        System.out.println("testBooleanParamConstructor(false)");
        Object submission = booleanParamConstructor.newInstance(false); 
        test(submission);
    }

    @Test
    public void testDoubleParamConstructor() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException {
        System.out.println("testDoubleParamConstructor");
        Constructor<?> doubleParamConstructor = clazz.getConstructor(double.class);        
        Object submission = doubleParamConstructor.newInstance((double)1.0);
        test(submission);
    }

    @Test
    public void testByteParamConstructor() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException {
        System.out.println("testByteParamConstructor");
        Constructor<?> byteParamConstructor = clazz.getConstructor(byte.class);        
        Object submission = byteParamConstructor.newInstance((byte)1);
        test(submission);
    }

    @Test
    public void testShortParamConstructor() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException {
        System.out.println("testShortParamConstructor");
        Constructor<?> shortParamConstructor = clazz.getConstructor(short.class);        
        Object submission = shortParamConstructor.newInstance((short)1);
        test(submission);
    }

    @Test
    public void testLongParamConstructor() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException {
        System.out.println("testLongParamConstructor");
        Constructor<?> longParamConstructor = clazz.getConstructor(long.class);        
        Object submission = longParamConstructor.newInstance(1L);
        test(submission);
    }

    

    @Test
    public void testStringParamConstructor() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException {
        System.out.println("testStringParamConstructor");
        Constructor<?> stringParamConstructor = clazz.getConstructor(String.class);        
        Object submission = stringParamConstructor.newInstance("1");
        test(submission);
    }

    @Test
    public void testDefaultConstructor() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException {
        System.out.println("testDefaultConstructor");
        Constructor<?> defaultConstructor = clazz.getConstructor();
        Object submission = defaultConstructor.newInstance();
        test(submission);
    }

    private void test(Object submission) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        double x = 5.0;
        double expected = 1.0+Math.sqrt(x);
        double observed = (double)submission.getClass().getMethod("sqrt", double.class).invoke(submission, x);
        Assert.assertEquals(expected, observed, 0.00000001);
    }
}
