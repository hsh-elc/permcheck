package main;

import java.awt.Desktop;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.StackWalker.Option;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.runner.*;
import org.junit.runner.notification.Failure;

import de.hsh.permcheck.Start;
import grader.Grader;
import grader.TestRunner;

/**
 * This class runs test cases.
 */
public class TestMain {

    private static final String PASSWORD = Long.toUnsignedString(new Random().nextLong());

    public static void main(String[] args) throws Exception {
        String tempFolderForBootstrapInjection = null;
        String policy = "";

        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "--permcheck.tmpfolder": {
                    String val = args[i+1];
                    try {
                        tempFolderForBootstrapInjection = Paths.get(val).toString();
                        i++;
                    } catch (InvalidPathException e) {
                        System.err.println("Illegal argument '"+val+"'");
                        return;
                    }
                }
                break;
                case "--permcheck.policy": {
                    String val = args[i+1];
                    String policyPath;
                    try {
                        policyPath = Paths.get(val).toString();
                        i++;
                    } catch (InvalidPathException e) {
                        System.err.println("Illegal argument '"+val+"'");
                        return;
                    }
                    try {
                        policy = Files.readString(Path.of(policyPath), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        System.err.println("Error when reading file '"+policyPath+"': " + e.getMessage());
                        return;
                    }
                }
                break;
                default: {
                    System.err.println("Illegal argument '"+args[i]+"'");
                    usage();
                }
                break;
            }
            i++;
        }
        try {
            Start.configureByteBuddyAgentIfAny(policy, PASSWORD, tempFolderForBootstrapInjection);

            //System.out.println("permcheck is active: " + Start.isActive(PASSWORD));

            JUnitCore junit = new JUnitCore();

            gradeSubmission(junit, PASSWORD);

            List<TestCase> tests = collectTestCaseFactoryMethods();
            for (TestCase tc : tests) {
                runTestCase(junit, PASSWORD, tc);
            }
        } finally {
            // System.out.println("Enter to finish...");
            // System.in.read();
        }
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("01 java \\");
        System.out.println("02    -Djava.security.manager -Djava.security.policy==security.policy \\");
        System.out.println("03    -XX:-EnableDynamicAgentLoading -Xshare:off -Dnet.bytebuddy.safe=true -ea -javaagent:lib/byte-buddy-agent-1.15.11.jar \\");
        System.out.println("04    --add-reads java.base=ALL-UNNAMED \\");
        System.out.println("05    -cp byte-buddy-1.15.11.jar;byte-buddy-agent-1.15.11.jar;junit-4.12.jar;hamcrest-core-1.3.jar \\");
        System.out.println("06    main.Main4 \\");
        System.out.println("07    --permcheck.policy permcheck.policy \\");
        System.out.println("08    --permcheck.tmpfolder path/to/tmpfolderforbootstrapinjection");
        System.out.println();
        System.out.println("Remarks:");
        System.out.println("01: A JDK SE 17 or above is needed");
        System.out.println("02: The security manager is optional");
        System.out.println("08: The temp folder is optional");
        System.exit(1);
    }

    private static void gradeSubmission(JUnitCore junit, String password) {
        Result result = runJunitWithPermcheck(junit, Grader.class, password);
        eval(result, "grading submission");
    }

    private static Result runJunitWithPermcheck(JUnitCore junit, Class<?> clazz, String password) {
        Start.resume(password);
        // junit.addListener(new TextListener(System.out));
        Result result = junit.run(clazz);
        Start.pause(password);
        return result;
    }

    private static List<TestCase> collectTestCaseFactoryMethods() {
        List<TestCase> tests = new ArrayList<>();
        Method[] factories = TestMain.class.getDeclaredMethods();
        Arrays.sort(factories, (a, b) -> a.getName().compareTo(b.getName()));
        for (Method factory : factories) {
            if (factory.isAnnotationPresent(TestCaseFactory.class)) {
                if (!Modifier.isStatic(factory.getModifiers())) {
                    throw new AssertionError("Internal error. @TestCaseFactory shouldn't be annotated to non-static method.");
                }
                if (!Collection.class.isAssignableFrom(factory.getReturnType())) {
                    throw new AssertionError("Internal error. @TestCaseFactory annotated method should return Collection<TestCase>.");
                }
                if (factory.getParameterCount() != 0) {
                    throw new AssertionError("Internal error. @TestCaseFactory annotated method should get 0 parameters.");
                }
                try {
                    @SuppressWarnings("unchecked")
                    Collection<TestCase> list = (Collection<TestCase>) factory.invoke(null);
                    tests.addAll(list);
                } catch (IllegalAccessException e) {
                    throw new AssertionError("Internal error. @TestCaseFactory method is not accessible.", e);
                } catch (IllegalArgumentException e) {
                    // shouldnt happen
                    throw new AssertionError("Internal error.", e);
                } catch (InvocationTargetException e) {
                    throw new AssertionError("Internal error. When invoking @TestCaseFactory method.", e);
                }
            }
        }
        return tests;
    }

    private static void runTestCase(JUnitCore junit, String password, TestCase tc) {
        TestRunner.tc = tc;

        Result result = runJunitWithPermcheck(junit, TestRunner.class, password);
        eval(result, "TestCase '" + tc.nameAndComment() + "'");
    }

    private static void eval(Result result, String what) throws AssertionError {
        StringBuilder sb = new StringBuilder();
        String prefix = "        > ";
        try (Formatter out = new Formatter(sb)) {
            for (Failure f : result.getFailures()) {
                out.format("%sFailure in %s%n", prefix, f.getTestHeader());
                if (f.getMessage() != null) out.format("%s%s%n", prefix, f.getMessage());
                out.format("%s%s%n", prefix, "Trace:");
                for (String line : f.getTrace().split("\\R")) {
                    out.format("%s%s%n", prefix, line);
                }
            }
            if (result.getFailureCount() > 0) {
                throw new AssertionError("Failure in "+what+". Test run aborted.\n" + sb.toString());
            }
        }
    }


    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private static @interface TestCaseFactory {
    }

    @TestCaseFactory
    private static List<TestCase> testNoAttack() {
        ArrayList<TestCase> result = new ArrayList<>();

        class TestCaseNoAttack extends TestCase {
            @Override public Double apply(Double x) {
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseNoAttack());

        return result;
    }

    @TestCaseFactory
    private static List<TestCase> testWrongResult() {
        ArrayList<TestCase> result = new ArrayList<>();

        class TestCaseWrongResult extends TestCase {
            public TestCaseWrongResult() {
                super(AssertionError.class, "but was:");
            }
            @Override public Double apply(Double x) {
                return 1+Math.sqrt(x);
            }
        }
        result.add(new TestCaseWrongResult());

        return result;
    }

    @TestCaseFactory
    private static List<TestCase> testAttackToPermcheck() {
        ArrayList<TestCase> result = new ArrayList<>();

        class TestCasePausePermcheckDenied extends TestCase {
            public TestCasePausePermcheckDenied() {
                super(Error.class, "env is not granted for 'PATH'");
            }
            @Override public Double apply(Double x) {
                try {
                    Start.pause("secret"); // futile attempt
                } catch (IllegalArgumentException ex) {
                    // continue. This is expected.
                }
                System.getenv("PATH"); // still denied
                // We shouldn't get here
                return 0.0;
            }
        }
        result.add(new TestCasePausePermcheckDenied());

        class TestCasePausePermcheckGranted extends TestCase {
            public TestCasePausePermcheckGranted() {
                super(null, null);
            }
            @Override public Double apply(Double x) {
                boolean isActive = Start.isActive(PASSWORD);
                try {
                    Start.pause(PASSWORD); // successful attempt
                    System.getenv("PATH"); // granted, because permcheck was successfully paused
                    return Math.sqrt(x);
                } finally {
                    if (isActive) Start.resume(PASSWORD);
                }
            }
        }
        result.add(new TestCasePausePermcheckGranted());

        return result;
    }

    @TestCaseFactory
    private static List<TestCase> testExitVm() {
        Class<? extends Throwable> expectedException = Error.class;
        String expectedMsg = "exitVm is not granted";
        ArrayList<TestCase> result = new ArrayList<>();

        class TestCaseSystemExit extends TestCase {
            public TestCaseSystemExit() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                System.exit(0);
                return 0.0;
            }
        }
        result.add(new TestCaseSystemExit());

        class TestCaseRuntimeExit extends TestCase {
            public TestCaseRuntimeExit() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                Runtime.getRuntime().exit(0);
                return 0.0;
            }
        }
        result.add(new TestCaseRuntimeExit());

        class TestCaseRuntimeHalt extends TestCase {
            public TestCaseRuntimeHalt() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                Runtime.getRuntime().halt(0);
                return 0.0;
            }
        }
        result.add(new TestCaseRuntimeHalt());

        return result;
    }

    @TestCaseFactory
    private static List<TestCase> testReflectionSetAccessible() {
        Class<? extends Throwable> expectedException = Error.class;
        String expectedMsg = "reflectionSetAccessible is not granted";
        class Dummy {
            @SuppressWarnings("unused") private int field;
            @SuppressWarnings("unused") private void method() {}
            private Dummy() {}
        }
        // trigger loading of classes, since this is only allowed in privileged code here
        @SuppressWarnings("unused") Class<?> tmp = Dummy.class; 

        ArrayList<TestCase> result = new ArrayList<>();

        class TestCaseFieldSetAccessible extends TestCase {
            public TestCaseFieldSetAccessible() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    Dummy.class.getDeclaredField("field").setAccessible(true);
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFieldSetAccessible());

        class TestCaseMethodSetAccessible extends TestCase {
            public TestCaseMethodSetAccessible() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    Dummy.class.getDeclaredMethod("method").setAccessible(true);
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodSetAccessible());

        class TestCaseConstructorSetAccessible extends TestCase {
            public TestCaseConstructorSetAccessible() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    Dummy.class.getDeclaredConstructor().setAccessible(true);
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return 0.0;
            }
        }
        result.add(new TestCaseConstructorSetAccessible());

        class TestCaseAccessibleObjectSetAccessible extends TestCase {
            public TestCaseAccessibleObjectSetAccessible() {
                super(expectedException, expectedMsg, "AccessibleObject.setAccessible(AccessibleObject[], boolean)");
            }
            @Override public Double apply(Double x) {
                try {
                    AccessibleObject[] ao = {
                        Dummy.class.getDeclaredField("field"),
                        Dummy.class.getDeclaredMethod("method")
                    };
                    AccessibleObject.setAccessible(ao, false);
                } catch (NoSuchFieldException | NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return 0.0;
            }
        }
        result.add(new TestCaseAccessibleObjectSetAccessible());

        class TestCaseAccessibleObjectTrySetAccessible extends TestCase {
            public TestCaseAccessibleObjectTrySetAccessible() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    Dummy.class.getDeclaredMethod("method").trySetAccessible();
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseAccessibleObjectTrySetAccessible());

        class TestCaseMethodHandlesReflectAs extends TestCase {
            public TestCaseMethodHandlesReflectAs() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodType mt = MethodType.methodType(void.class);
                MethodHandle mh;
                try {
                    mh = lookup.findVirtual(Dummy.class, "method", mt);
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                Method method = MethodHandles.reflectAs(Method.class, mh);
                // ^ this should throw an "expectedException"
                method.setAccessible(true);
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesReflectAs());

        class TestCaseMethodHandlesPrivateLookupIn extends TestCase {
            public TestCaseMethodHandlesPrivateLookupIn() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.privateLookupIn(Dummy.class, MethodHandles.lookup());
                    // ^ this should throw an "expectedException"
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesPrivateLookupIn());
        
        return result;
    }

    @TestCaseFactory
    private static List<TestCase> testReflectionAccessDeclaredMembers() {
        Class<? extends Throwable> expectedException = Error.class;
        String expectedMsg = "reflectionAccessDeclaredMembers is not granted";
        class Dummy {
            @SuppressWarnings("unused") private int field;
            @SuppressWarnings("unused") private void method() {}
            private Dummy() {}
            @SuppressWarnings("unused") private static int staticField;
            @SuppressWarnings("unused") private static void staticMethod() {}
            @SuppressWarnings("unused") protected String s() { return "Dummy.s"; }
        }
        class SubDummy extends Dummy {
            @Override protected String s() { return "SubDummy.s"; }
        }
        class SubObject {
            @Override public String toString() { return "SubObject.toString"; }
            static Lookup lookup() { return MethodHandles.lookup(); }
        }
        // trigger loading of classes, since this is only allowed in privileged code here
        @SuppressWarnings("unused") Class<?> tmp = Dummy.class; 
        tmp = SubDummy.class; 
        tmp = SubObject.class; 

        ArrayList<TestCase> result = new ArrayList<>();

        class TestCaseClassGetDeclaredFieldDifferentClassLoader extends TestCase {
            public TestCaseClassGetDeclaredFieldDifferentClassLoader() {
                super(expectedException, expectedMsg, "Class.getDeclaredField(String) on different classloader");
            }
            @Override public Double apply(Double x) {
                try {
                    java.awt.Point.class.getDeclaredField("x");
                    // ^ this should throw an "expectedException"
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseClassGetDeclaredFieldDifferentClassLoader());

        class TestCaseClassGetDeclaredFieldSameClassLoader extends TestCase {
            public TestCaseClassGetDeclaredFieldSameClassLoader() {
                super("Class.getDeclaredField(String) on same classloader");
            }
            @Override public Double apply(Double x) {
                try {
                    Dummy.class.getDeclaredField("field");
                    // ^ this should succeed
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseClassGetDeclaredFieldSameClassLoader());

        class TestCaseClassGetDeclaredMethodDifferentClassLoader extends TestCase {
            public TestCaseClassGetDeclaredMethodDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    java.awt.Point.class.getDeclaredMethod("getX");
                    // ^ this should throw an "expectedException"
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseClassGetDeclaredMethodDifferentClassLoader());

        class TestCaseClassGetDeclaredMethodSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                try {
                    Dummy.class.getDeclaredMethod("method");
                    // ^ this should succeed
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseClassGetDeclaredMethodSameClassLoader());

        class TestCaseClassGetDeclaredConstructorDifferentClassLoader extends TestCase {
            public TestCaseClassGetDeclaredConstructorDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    java.awt.Point.class.getDeclaredConstructor(int.class, int.class);
                    // ^ this should throw an "expectedException"
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseClassGetDeclaredConstructorDifferentClassLoader());

        class TestCaseClassGetDeclaredConstructorSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                try {
                    Dummy.class.getDeclaredConstructor();
                    // ^ this should succeed
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseClassGetDeclaredConstructorSameClassLoader());

        class TestCaseClassGetEnclosingMethodDifferentClassLoader extends TestCase {
            public TestCaseClassGetEnclosingMethodDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                java.awt.Point.class.getEnclosingMethod();
                // ^ this should throw an "expectedException"
                return 0.0;
            }
        }
        result.add(new TestCaseClassGetEnclosingMethodDifferentClassLoader());

        class TestCaseClassGetEnclosingMethodSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                Dummy.class.getEnclosingMethod();
                // ^ this should succeed
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseClassGetEnclosingMethodSameClassLoader());

        class TestCaseClassGetEnclosingConstructorDifferentClassLoader extends TestCase {
            public TestCaseClassGetEnclosingConstructorDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                java.awt.Point.class.getEnclosingConstructor();
                // ^ this should throw an "expectedException"
                return 0.0;
            }
        }
        result.add(new TestCaseClassGetEnclosingConstructorDifferentClassLoader());

        class TestCaseClassGetEnclosingConstructorSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                Dummy.class.getEnclosingConstructor();
                // ^ this should succeed
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseClassGetEnclosingConstructorSameClassLoader());

        class TestCaseClassGetDeclaredFieldsDifferentClassLoader extends TestCase {
            public TestCaseClassGetDeclaredFieldsDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                java.awt.Point.class.getDeclaredFields();
                // ^ this should throw an "expectedException"
                return 0.0;
            }
        }
        result.add(new TestCaseClassGetDeclaredFieldsDifferentClassLoader());

        class TestCaseClassGetDeclaredFieldsSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                Dummy.class.getDeclaredFields();
                // ^ this should succeed
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseClassGetDeclaredFieldsSameClassLoader());

        class TestCaseClassGetDeclaredMethodsDifferentClassLoader extends TestCase {
            public TestCaseClassGetDeclaredMethodsDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                java.awt.Point.class.getDeclaredMethods();
                // ^ this should throw an "expectedException"
                return 0.0;
            }
        }
        result.add(new TestCaseClassGetDeclaredMethodsDifferentClassLoader());

        class TestCaseClassGetDeclaredMethodsSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                Dummy.class.getDeclaredMethods();
                // ^ this should succeed
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseClassGetDeclaredMethodsSameClassLoader());        

        class TestCaseClassGetDeclaredConstructorsDifferentClassLoader extends TestCase {
            public TestCaseClassGetDeclaredConstructorsDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                java.awt.Point.class.getDeclaredConstructors();
                // ^ this should throw an "expectedException"
                return 0.0;
            }
        }
        result.add(new TestCaseClassGetDeclaredConstructorsDifferentClassLoader());

        class TestCaseClassGetDeclaredConstructorsSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                Dummy.class.getDeclaredConstructors();
                // ^ this should succeed
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseClassGetDeclaredConstructorsSameClassLoader());        

        class TestCaseClassGetDeclaredClassesDifferentClassLoader extends TestCase {
            public TestCaseClassGetDeclaredClassesDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                java.awt.Point.class.getDeclaredClasses();
                // ^ this should throw an "expectedException"
                return 0.0;
            }
        }
        result.add(new TestCaseClassGetDeclaredClassesDifferentClassLoader());

        class TestCaseClassGetDeclaredClassesSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                Dummy.class.getDeclaredClasses();
                // ^ this should succeed
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseClassGetDeclaredClassesSameClassLoader());        

        class TestCaseClassGetRecordComponentsDifferentClassLoader extends TestCase {
            public TestCaseClassGetRecordComponentsDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                java.awt.Point.class.getRecordComponents();
                // ^ this should throw an "expectedException"
                return 0.0;
            }
        }
        result.add(new TestCaseClassGetRecordComponentsDifferentClassLoader());

        class TestCaseClassGetRecordComponentsSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                Dummy.class.getRecordComponents();
                // ^ this should succeed
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseClassGetRecordComponentsSameClassLoader());        

        class TestCaseMethodHandlesLookupInClassFindStaticSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                try {
                    // this is a private static method in class Dummy, sitting on the same classloader:
                    MethodHandles.lookup().in(Dummy.class).findStatic(Dummy.class, "staticMethod", MethodType.methodType(void.class));
                    // ^ this should succeed
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindStaticSameClassLoader());

        class TestCaseMethodHandlesLookupFindStaticDifferentClassLoaderPublicMethod extends TestCase {
            @Override public Double apply(Double x) {
                try {
                    // this is a public static method in class BigInteger:
                    MethodHandles.lookup().findStatic(BigInteger.class, "valueOf", MethodType.methodType(BigInteger.class, long.class));
                    // ^ this should succeed, since lookup has full capabilities to emulate all supported bytecode behaviors of the caller
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindStaticDifferentClassLoaderPublicMethod());

        class TestCaseMethodHandlesLookupInClassFindStaticDifferentClassLoaderPublicMethod extends TestCase {
            public TestCaseMethodHandlesLookupInClassFindStaticDifferentClassLoaderPublicMethod() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    // this is a public static method in class BigInteger:
                    MethodHandles.lookup().in(BigInteger.class).findStatic(BigInteger.class, "valueOf", MethodType.methodType(BigInteger.class, long.class));
                    // ^ this should fail, since lookup().in(...) reduces the lookup object to having no full capabilities anymore
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindStaticDifferentClassLoaderPublicMethod());

        class TestCaseMethodHandlesLookupFindStaticDifferentClassLoaderPrivateMethod extends TestCase {
            public TestCaseMethodHandlesLookupFindStaticDifferentClassLoaderPrivateMethod() {
                super(expectedException, "IllegalAccessException: no such method:");
            }
            @Override public Double apply(Double x) {
                try {
                    // this is a private static method in class BigInteger:
                    MethodHandles.lookup().findStatic(BigInteger.class, "randomBits", MethodType.methodType(byte[].class, int.class, Random.class));
                    // Since MethodHandles.lookup() has full capabilities, the inserted advice won't cancel the operation.
                    // But instead, the normal access controls will prevent the access with an IllegalAccessExeption.
                    // This is the desired behaviour, since the apidocs of Methodhandles.Lookup says:
                    // "Security checks are performed after other access checks have passed."
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // should happen
                    throw new Error(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindStaticDifferentClassLoaderPrivateMethod());

        class TestCaseMethodHandlesLookupFindStaticSetterSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                MethodHandle mh = null;
                try {
                    mh = MethodHandles.lookup().in(Dummy.class).findStaticSetter(Dummy.class, "staticField", int.class);
                    // This is a non-final private static field. 
                    // ^ this should succeed
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                try {
                    mh.invokeExact(42);
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindStaticSetterSameClassLoader());    

        class TestCaseMethodHandlesLookupFindStaticSetterDifferentClassLoader extends TestCase {
            public TestCaseMethodHandlesLookupFindStaticSetterDifferentClassLoader() {
                super(expectedException, "IllegalAccessException: unexpected set of a final field:");
            }
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().findStaticSetter(java.io.File.class, "separator", String.class);
                    // This is a final public static field. Since lookup() ist fully capable, there should be a
                    // IllegalAccessException
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // should happen.
                    throw new Error(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindStaticSetterDifferentClassLoader());

        class TestCaseMethodHandlesLookupInClassFindStaticSetterDifferentClassLoader extends TestCase {
            public TestCaseMethodHandlesLookupInClassFindStaticSetterDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().in(java.io.File.class).findStaticSetter(java.io.File.class, "separator", String.class);
                    // ^ this should throw an "expectedException", since with in(File.class) the lookup is
                    // not fully capable.
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // would happen, if not denied, since separator is final
                    throw new RuntimeException("ok", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindStaticSetterDifferentClassLoader());

        class TestCaseMethodHandlesLookupFindStaticGetterSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                Dummy.staticField = 43;
                MethodHandle mh = null;
                try {
                    mh = MethodHandles.lookup().in(Dummy.class).findStaticGetter(Dummy.class, "staticField", int.class);
                    // This is a private static field. 
                    // ^ this should succeed
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                int val;
                try {
                    val = (int) mh.invokeExact();
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (val != 43) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
            return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindStaticGetterSameClassLoader());    

        class TestCaseMethodHandlesLookupFindStaticGetterDifferentClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                String s = File.separator;
                MethodHandle mh = null;
                try {
                    mh = MethodHandles.lookup().findStaticGetter(java.io.File.class, "separator", String.class);
                    // This is a final public static field. Since lookup() ist fully capable, this should succeed
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                String s2;
                try {
                    s2 = (String) mh.invokeExact();
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (!s.equals(s2)) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindStaticGetterDifferentClassLoader());

        class TestCaseMethodHandlesLookupInClassFindStaticGetterDifferentClassLoader extends TestCase {
            public TestCaseMethodHandlesLookupInClassFindStaticGetterDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().in(java.io.File.class).findStaticGetter(java.io.File.class, "separator", String.class);
                    // ^ this should throw an "expectedException", since with in(File.class) the lookup is
                    // not fully capable.
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindStaticGetterDifferentClassLoader());


        class TestCaseMethodHandlesLookupFindStaticVarHandleSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().in(Dummy.class).findStaticVarHandle(Dummy.class, "staticField", int.class).set(42);;
                    // This is a private static field. 
                    // ^ this should succeed
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindStaticVarHandleSameClassLoader());    

        class TestCaseMethodHandlesLookupFindStaticVarHandleDifferentClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                String s = File.separator;
                String s2;
                try {
                    s2 = (String)MethodHandles.lookup().findStaticVarHandle(java.io.File.class, "separator", String.class).get();
                    // This is a final public static field. Since lookup() ist fully capable, this should succeed
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (!s.equals(s2)) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindStaticVarHandleDifferentClassLoader());

        class TestCaseMethodHandlesLookupInClassFindStaticVarHandleDifferentClassLoader extends TestCase {
            public TestCaseMethodHandlesLookupInClassFindStaticVarHandleDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().in(java.io.File.class).findStaticVarHandle(java.io.File.class, "separator", String.class);
                    // ^ this should throw an "expectedException", since with in(File.class) the lookup is
                    // not fully capable.
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindStaticVarHandleDifferentClassLoader());


        
        class TestCaseMethodHandlesLookupFindSetterSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                MethodHandle mh = null;
                try {
                    mh = MethodHandles.lookup().in(Dummy.class).findSetter(Dummy.class, "field", int.class);
                    // This is a non-final private field. 
                    // ^ this should succeed
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                try {
                    Dummy d = new Dummy();
                    mh.invokeExact(d, 42);
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindSetterSameClassLoader());    

        class TestCaseMethodHandlesLookupFindSetterDifferentClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                MethodHandle mh = null;
                try {
                    mh = MethodHandles.lookup().findSetter(java.awt.Point.class, "x", int.class);
                    // This is a public field. Since lookup() is fully capable, this should succeed.
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                java.awt.Point p = new java.awt.Point();
                try {
                    mh.invokeExact(p, 42);
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (p.x != 42) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindSetterDifferentClassLoader());

        class TestCaseMethodHandlesLookupInClassFindSetterDifferentClassLoader extends TestCase {
            public TestCaseMethodHandlesLookupInClassFindSetterDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().in(java.awt.Point.class).findSetter(java.awt.Point.class, "x", int.class);
                    // ^ this should throw an "expectedException", since with in(Point.class) the lookup is
                    // not fully capable.
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindSetterDifferentClassLoader());

        
        class TestCaseMethodHandlesLookupFindGetterSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                MethodHandle mh = null;
                try {
                    mh = MethodHandles.lookup().in(Dummy.class).findGetter(Dummy.class, "field", int.class);
                    // This is a non-final private field. 
                    // ^ this should succeed
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                int v;
                try {
                    Dummy d = new Dummy();
                    d.field = 42;
                    v = (int)mh.invokeExact(d);
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (v != 42) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindGetterSameClassLoader());    

        class TestCaseMethodHandlesLookupFindGetterDifferentClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                MethodHandle mh = null;
                try {
                    mh = MethodHandles.lookup().findGetter(java.awt.Point.class, "x", int.class);
                    // This is a public field. Since lookup() is fully capable, this should succeed.
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                java.awt.Point p = new java.awt.Point(42, 0);
                int v;
                try {
                    v = (int)mh.invokeExact(p);
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (v != 42) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindGetterDifferentClassLoader());

        class TestCaseMethodHandlesLookupInClassFindGetterDifferentClassLoader extends TestCase {
            public TestCaseMethodHandlesLookupInClassFindGetterDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().in(java.awt.Point.class).findGetter(java.awt.Point.class, "x", int.class);
                    // ^ this should throw an "expectedException", since with in(Point.class) the lookup is
                    // not fully capable.
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindGetterDifferentClassLoader());



        class TestCaseMethodHandlesLookupInClassFindVirtualSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                try {
                    // this is a private instance method in class Dummy, sitting on the same classloader:
                    MethodHandles.lookup().in(Dummy.class).findVirtual(Dummy.class, "method", MethodType.methodType(void.class));
                    // ^ this should succeed
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindVirtualSameClassLoader());


        class TestCaseMethodHandlesLookupFindVirtualDifferentClassLoaderPublicMethod extends TestCase {
            @Override public Double apply(Double x) {
               MethodHandle mh = null;
                try {
                    // this is a public instance method in class BigInteger:
                    mh = MethodHandles.lookup().findVirtual(BigInteger.class, "abs", MethodType.methodType(BigInteger.class));
                    // ^ this should succeed, since lookup has full capabilities to emulate all supported bytecode behaviors of the caller
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                BigInteger bi = BigInteger.valueOf(-2L);
                BigInteger bi2;
                try {
                    bi2 = (BigInteger)mh.invokeExact(bi);
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (bi2.longValue() != 2L) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindVirtualDifferentClassLoaderPublicMethod());

        class TestCaseMethodHandlesLookupInClassFindVirtualDifferentClassLoaderPublicMethod extends TestCase {
            public TestCaseMethodHandlesLookupInClassFindVirtualDifferentClassLoaderPublicMethod() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    // this is a public instance  method in class BigInteger:
                    MethodHandles.lookup().in(BigInteger.class).findVirtual(BigInteger.class, "abs", MethodType.methodType(BigInteger.class));
                    // ^ this should fail, since lookup().in(...) reduces the lookup object to having no full capabilities anymore
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindVirtualDifferentClassLoaderPublicMethod());
        
        class TestCaseMethodHandlesLookupInClassFindConstructorSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                try {
                    // this is a private constructor in class Dummy, sitting on the same classloader:
                    MethodHandles.lookup().in(Dummy.class).findConstructor(Dummy.class, MethodType.methodType(void.class));
                    // ^ this should succeed
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindConstructorSameClassLoader());


        class TestCaseMethodHandlesLookupFindConstructorDifferentClassLoaderPublicMethod extends TestCase {
            @Override public Double apply(Double x) {
                MethodHandle mh = null;
                try {
                    // this is a public constructor of class Point:
                    mh = MethodHandles.lookup().findConstructor(java.awt.Point.class, MethodType.methodType(void.class, int.class, int.class));
                    // ^ this should succeed, since lookup has full capabilities to emulate all supported bytecode behaviors of the caller
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                java.awt.Point p;
                try {
                    p = (java.awt.Point)mh.invokeExact(42, 0);
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (p.x != 42) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindConstructorDifferentClassLoaderPublicMethod());

        class TestCaseMethodHandlesLookupInClassFindConstructorDifferentClassLoaderPublicMethod extends TestCase {
            public TestCaseMethodHandlesLookupInClassFindConstructorDifferentClassLoaderPublicMethod() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    // this is a public static method in class BigInteger:
                    MethodHandles.lookup().in(java.awt.Point.class).findConstructor(java.awt.Point.class, MethodType.methodType(void.class, int.class, int.class));
                    // ^ this should fail, since lookup().in(...) reduces the lookup object to having no full capabilities anymore
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindConstructorDifferentClassLoaderPublicMethod());

            
        class TestCaseMethodHandlesLookupInClassFindSpecialSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                MethodHandle mh = null;
                try {
                    // this is a protected instance method in class Dummy, sitting on the same classloader:
                    mh = MethodHandles.lookup().in(SubDummy.class).findSpecial(Dummy.class, "s", MethodType.methodType(String.class), SubDummy.class);
                    // ^ this should succeed
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                SubDummy d = new SubDummy();
                String s; 
                try {
                    s = (String)mh.invokeExact(d);
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (!"Dummy.s".equals(s)) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }

                MethodHandle mh2 = null;
                try {
                    // this is a protected instance method in class SubDummy, sitting on the same classloader:
                    mh2 = MethodHandles.lookup().in(SubDummy.class).findSpecial(SubDummy.class, "s", MethodType.methodType(String.class), SubDummy.class);
                    // ^ this should succeed
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                SubDummy d2 = new SubDummy();
                String s2; 
                try {
                    s2 = (String)mh2.invokeExact(d2);
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (!"SubDummy.s".equals(s2)) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindSpecialSameClassLoader());


        class TestCaseMethodHandlesLookupFindSpecialDifferentClassLoaderPublicMethod extends TestCase {
            @Override public Double apply(Double x) {
                MethodHandle mh = null;
                try {
                    mh = SubObject.lookup().findSpecial(Object.class, "toString", MethodType.methodType(String.class), SubObject.class);
                    // ^ this should succeed, since lookup has full capabilities to emulate all supported bytecode behaviors of the caller
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                SubObject so = new SubObject();
                String s;
                try {
                    s = (String)mh.invokeExact(so);
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if ("SubObject.toString".equals(s)) {
                    // shouldn't happen. It should have called the super class method Object.toString.
                    throw new AssertionError("Internal error in TestCase");
                }

                MethodHandle mh2 = null;
                try {
                    // invoke SubObject.toString
                    mh2 = SubObject.lookup().findSpecial(SubObject.class, "toString", MethodType.methodType(String.class), SubObject.class);
                    // ^ this should succeed, since lookup has full capabilities to emulate all supported bytecode behaviors of the caller
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                SubObject so2 = new SubObject();
                String s2;
                try {
                    s2 = (String)mh2.invokeExact(so2);
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (!"SubObject.toString".equals(s2)) {
                    // shouldn't happen. It should have called the sub class method SubObject.toString.
                    throw new AssertionError("Internal error in TestCase");
                }

                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindSpecialDifferentClassLoaderPublicMethod());

        class TestCaseMethodHandlesLookupInClassFindSpecialDifferentClassLoaderPublicMethod extends TestCase {
            public TestCaseMethodHandlesLookupInClassFindSpecialDifferentClassLoaderPublicMethod() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    SubObject.lookup().in(Object.class).findSpecial(Object.class, "toString", MethodType.methodType(String.class), SubObject.class);
                    // ^ this should not succeed, since lookup().in(...) reduces the lookup object to having no full capabilities anymore
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindSpecialDifferentClassLoaderPublicMethod());



        class TestCaseMethodHandlesLookupFindVarHandleSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                Dummy d = new Dummy();
                try {
                    MethodHandles.lookup().in(Dummy.class).findVarHandle(Dummy.class, "field", int.class).set(d, 42);;
                    // This is a private instance field. 
                    // ^ this should succeed
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindVarHandleSameClassLoader());


        class TestCaseMethodHandlesLookupFindVarHandleDifferentClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                java.awt.Point p = new java.awt.Point(42, 0);
                int x2;
                try {
                    x2 = (int)MethodHandles.lookup().findVarHandle(java.awt.Point.class, "x", int.class).get(p);
                    // This is a public instance field. Since lookup() ist fully capable, this should succeed
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (x2 != 42) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupFindVarHandleDifferentClassLoader());

        class TestCaseMethodHandlesLookupInClassFindVarHandleDifferentClassLoader extends TestCase {
            public TestCaseMethodHandlesLookupInClassFindVarHandleDifferentClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().in(java.awt.Point.class).findStaticVarHandle(java.awt.Point.class, "x", int.class);
                    // ^ this should throw an "expectedException", since with in(Point.class) the lookup is
                    // not fully capable.
                } catch (NoSuchFieldException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassFindVarHandleDifferentClassLoader());

        
        class TestCaseMethodHandlesLookupInClassBindSameClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                Dummy d = new Dummy();
                MethodHandle mh = null;
                try {
                    // this is a private instance method in class Dummy, sitting on the same classloader:
                    mh = MethodHandles.lookup().in(Dummy.class).bind(d, "method", MethodType.methodType(void.class));
                    // ^ this should succeed
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                try {
                    mh.invokeExact();
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassBindSameClassLoader());


        class TestCaseMethodHandlesLookupBindDifferentClassLoaderPublicMethod extends TestCase {
            @Override public Double apply(Double x) {
                BigInteger bi = BigInteger.valueOf(-2L);
                MethodHandle mh = null;
                try {
                    // this is a public instance method in class BigInteger:
                    mh = MethodHandles.lookup().bind(bi, "abs", MethodType.methodType(BigInteger.class));
                    // ^ this should succeed, since lookup has full capabilities to emulate all supported bytecode behaviors of the caller
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                BigInteger bi2;
                try {
                    bi2 = (BigInteger)mh.invokeExact();
                } catch (Throwable e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                if (bi2.longValue() != 2L) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupBindDifferentClassLoaderPublicMethod());

        class TestCaseMethodHandlesLookupInClassBindDifferentClassLoaderPublicMethod extends TestCase {
            public TestCaseMethodHandlesLookupInClassBindDifferentClassLoaderPublicMethod() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                BigInteger bi = BigInteger.valueOf(-2L);
                try {
                    // this is a public instance  method in class BigInteger:
                    MethodHandles.lookup().in(BigInteger.class).bind(bi, "abs", MethodType.methodType(BigInteger.class));
                    // ^ this should fail, since lookup().in(...) reduces the lookup object to having no full capabilities anymore
                } catch (NoSuchMethodException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupInClassBindDifferentClassLoaderPublicMethod());
        return result;
    }

    @TestCaseFactory
    private static List<TestCase> testReflectionGetStacktrace() {
        Class<? extends Throwable> expectedException = Error.class;
        String expectedMsg = "reflectionGetStackTrace is not granted";

        ArrayList<TestCase> result = new ArrayList<>();

        class TestCaseThreadGetStackTraceCurrentThread extends TestCase {
            @Override public Double apply(Double x) {
                Thread.currentThread().getStackTrace();
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseThreadGetStackTraceCurrentThread());

        class TestCaseThreadGetStackTraceOtherThread extends TestCase {
            public TestCaseThreadGetStackTraceOtherThread() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                new Thread().getStackTrace();
                return 0.0;
            }
        }
        result.add(new TestCaseThreadGetStackTraceOtherThread());

        class TestCaseThreadGetAllStackTraces extends TestCase {
            public TestCaseThreadGetAllStackTraces() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                Thread.getAllStackTraces();
                return 0.0;
            }
        }
        result.add(new TestCaseThreadGetAllStackTraces());

        return result;
    }

    @TestCaseFactory
    private static List<TestCase> testReflectionGetStackWalkerWithClassReference() {
        Class<? extends Throwable> expectedException = Error.class;
        String expectedMsg = "reflectionGetStackWalkerWithClassReference is not granted";

        ArrayList<TestCase> result = new ArrayList<>();

        class TestCaseStackWalkerGetInstanceOptionNoRetainClassReference extends TestCase {
            @Override public Double apply(Double x) {
                StackWalker.getInstance(Option.SHOW_HIDDEN_FRAMES);
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseStackWalkerGetInstanceOptionNoRetainClassReference());

        class TestCaseStackWalkerGetInstanceOptionWithRetainClassReference extends TestCase {
            public TestCaseStackWalkerGetInstanceOptionWithRetainClassReference() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);
                return 0.0;
            }
        }
        result.add(new TestCaseStackWalkerGetInstanceOptionWithRetainClassReference());

        class TestCaseStackWalkerGetInstanceSetOptionNoRetainClassReference extends TestCase {
            @Override public Double apply(Double x) {
                StackWalker.getInstance(EnumSet.of(Option.SHOW_HIDDEN_FRAMES));
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseStackWalkerGetInstanceSetOptionNoRetainClassReference());

        class TestCaseStackWalkerGetInstanceSetOptionWithRetainClassReference extends TestCase {
            public TestCaseStackWalkerGetInstanceSetOptionWithRetainClassReference() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                StackWalker.getInstance(EnumSet.of(Option.RETAIN_CLASS_REFERENCE, Option.SHOW_HIDDEN_FRAMES));
                return 0.0;
            }
        }
        result.add(new TestCaseStackWalkerGetInstanceSetOptionWithRetainClassReference());

        class TestCaseStackWalkerGetInstanceSetOptionIntNoRetainClassReference extends TestCase {
            @Override public Double apply(Double x) {
                StackWalker.getInstance(EnumSet.of(Option.SHOW_HIDDEN_FRAMES), 10);
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseStackWalkerGetInstanceSetOptionIntNoRetainClassReference());

        class TestCaseStackWalkerGetInstanceSetOptionIntWithRetainClassReference extends TestCase {
            public TestCaseStackWalkerGetInstanceSetOptionIntWithRetainClassReference() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                StackWalker.getInstance(EnumSet.of(Option.RETAIN_CLASS_REFERENCE, Option.SHOW_HIDDEN_FRAMES), 10);
                return 0.0;
            }
        }
        result.add(new TestCaseStackWalkerGetInstanceSetOptionIntWithRetainClassReference());

        return result;
    }

    @TestCaseFactory
    private static List<TestCase> testReflectionGetClassLoader() {
        Class<? extends Throwable> expectedException = Error.class;
        String expectedMsg = "reflectionGetClassLoader is not granted";

        Class<?> clazzHelloWorldFromMemClassLoader, clazzGetSystemClassLoaderFromMemClassLoader;
        try {
            clazzHelloWorldFromMemClassLoader = Class.forName("HelloWorld", true, Util.getMemoryClassLoader());
            clazzGetSystemClassLoaderFromMemClassLoader = Class.forName("GetSystemClassLoader", true, Util.getMemoryClassLoader());
        } catch (Exception e) {
            // shouldn't happen
            throw new AssertionError("Internal error in TestCase", e);
        }

        ArrayList<TestCase> result = new ArrayList<>();

        class TestCaseThreadGetContextClassLoaderCurrentThread extends TestCase {
            @Override public Double apply(Double x) {
                Thread.currentThread().getContextClassLoader();
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseThreadGetContextClassLoaderCurrentThread());

        class TestCaseThreadGetContextClassLoaderOtherThread extends TestCase {
            public TestCaseThreadGetContextClassLoaderOtherThread() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                Thread t = new Thread();
                t.setContextClassLoader(Util.getMemoryClassLoader());
                t.start();
                t.getContextClassLoader(); // should fail
                return 0.0;
            }
        }
        result.add(new TestCaseThreadGetContextClassLoaderOtherThread());

        class TestCaseClassGetClassLoaderOtherClassLoader extends TestCase {
            public TestCaseClassGetClassLoaderOtherClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                clazzHelloWorldFromMemClassLoader.getClassLoader(); // should fail
                return 0.0;
            }
        }
        result.add(new TestCaseClassGetClassLoaderOtherClassLoader());       

        class TestCaseClassLoaderGetParentChildOfOwnClassLoader extends TestCase {
            @Override public Double apply(Double x) {
                Util.getMemoryClassLoader().getParent(); // should succeed
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseClassLoaderGetParentChildOfOwnClassLoader());

        class TestCaseClassLoaderGetParentChildOfOtherClassLoader extends TestCase {
            public TestCaseClassLoaderGetParentChildOfOtherClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                ClassLoader sc = ClassLoader.getSystemClassLoader();
                sc.getParent(); // should fail
                return 0.0;
            }
        }
        result.add(new TestCaseClassLoaderGetParentChildOfOtherClassLoader());       

        class TestCaseClassLoaderGetSystemClassLoaderForClassLoadedByOtherClassLoader extends TestCase {
            public TestCaseClassLoaderGetSystemClassLoaderForClassLoadedByOtherClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    clazzGetSystemClassLoaderFromMemClassLoader.getMethod("execute").invoke(null);
                    // ^ should fail with an Error
                } catch (InvocationTargetException e) {
                    Throwable target = e.getTargetException();
                    if (target instanceof Error) throw (Error)target;
                    if (target instanceof RuntimeException) throw (RuntimeException)target;
                    throw new AssertionError("Internal error in TestCase", e);
                } catch (IllegalAccessException | IllegalArgumentException
                        | NoSuchMethodException | SecurityException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } 
                return 0.0;
            }
        }
        result.add(new TestCaseClassLoaderGetSystemClassLoaderForClassLoadedByOtherClassLoader());       

        class TestCaseClassLoaderGetPlatformClassLoaderForClassLoadedBySystemClassLoader extends TestCase {
            public TestCaseClassLoaderGetPlatformClassLoaderForClassLoadedBySystemClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                ClassLoader.getPlatformClassLoader(); // should fail
                return 0.0;
            }
        }
        result.add(new TestCaseClassLoaderGetPlatformClassLoaderForClassLoadedBySystemClassLoader());       

        class TestCaseClassForName3ArgsWithNullClassLoaderArg extends TestCase {
            public TestCaseClassForName3ArgsWithNullClassLoaderArg() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    Class.forName("Any", true, null);
                } catch (ClassNotFoundException e) {
                    // shouldn't happen, since security check should prevent this
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseClassForName3ArgsWithNullClassLoaderArg());       

        class TestCaseClassForName2ArgsWithModuleDifferentFromCallerModule extends TestCase {
            public TestCaseClassForName2ArgsWithModuleDifferentFromCallerModule() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                Class.forName(java.lang.Object.class.getModule(), "Any");
                return 0.0;
            }
        }
        result.add(new TestCaseClassForName2ArgsWithModuleDifferentFromCallerModule());       

        class TestCaseMethodHandlesLookupEnsureInitializedWithNotFullyPrivilegedLookup extends TestCase {
            public TestCaseMethodHandlesLookupEnsureInitializedWithNotFullyPrivilegedLookup() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().in(java.awt.Point.class).ensureInitialized(java.lang.Object.class);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                // ^ this should throw an "expectedException"
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupEnsureInitializedWithNotFullyPrivilegedLookup());    

        class TestCaseMethodHandlesLookupEnsureInitializedWithFullyPrivilegedLookup extends TestCase {
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().ensureInitialized(java.lang.Object.class);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                // ^ this should succeed
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupEnsureInitializedWithFullyPrivilegedLookup());    

        class TestCaseMethodHandlesLookupAccessClassWithNotFullyPrivilegedLookup extends TestCase {
            public TestCaseMethodHandlesLookupAccessClassWithNotFullyPrivilegedLookup() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().in(java.awt.Point.class).accessClass(java.lang.Object.class);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                // ^ this should throw an "expectedException"
                return 0.0;
            }
        }
        result.add(new TestCaseMethodHandlesLookupAccessClassWithNotFullyPrivilegedLookup());    

        class TestCaseMethodHandlesLookupAccessClassWithFullyPrivilegedLookup extends TestCase {
            @Override public Double apply(Double x) {
                try {
                    MethodHandles.lookup().accessClass(java.lang.Object.class);
                } catch (IllegalAccessException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                // ^ this should succeed
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseMethodHandlesLookupAccessClassWithFullyPrivilegedLookup());    

        class TestCaseMethodTypeFromMethodDescriptorStringWithNullClassLoader extends TestCase {
            public TestCaseMethodTypeFromMethodDescriptorStringWithNullClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                MethodType.fromMethodDescriptorString("()v", null);
                // ^ this should throw an "expectedException"
                return 0.0;
            }
        }
        result.add(new TestCaseMethodTypeFromMethodDescriptorStringWithNullClassLoader());    

        class TestCaseModuleGetClassLoader extends TestCase {
            public TestCaseModuleGetClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                Object.class.getModule().getClassLoader(); // should fail
                return 0.0;
            }
        }
        result.add(new TestCaseModuleGetClassLoader());    

        class TestCaseModuleLayerDefineModulesWithManyLoaders extends TestCase {
            public TestCaseModuleLayerDefineModulesWithManyLoaders() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                ModuleLayer.defineModulesWithManyLoaders(null, null, null); // should fail
                return 0.0;
            }
        }
        result.add(new TestCaseModuleLayerDefineModulesWithManyLoaders());    


        class TestCaseModuleLayerDefineModulesWithOneLoader extends TestCase {
            public TestCaseModuleLayerDefineModulesWithOneLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                ModuleLayer.defineModulesWithOneLoader(null, null, null); // should fail
                return 0.0;
            }
        }
        result.add(new TestCaseModuleLayerDefineModulesWithOneLoader());    
                
        class TestCaseModuleLayerDefineModules extends TestCase {
            public TestCaseModuleLayerDefineModules() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                ModuleLayer.defineModules(null, null, null); // should fail
                return 0.0;
            }
        }
        result.add(new TestCaseModuleLayerDefineModules());    

        class TestCaseProxyGetProxyClassWithNullClassLoader extends TestCase {
            public TestCaseProxyGetProxyClassWithNullClassLoader() {
                super(expectedException, expectedMsg);
            }
            @SuppressWarnings("deprecation")
            @Override public Double apply(Double x) {
                Proxy.getProxyClass(null, Object.class); // should fail
                return 0.0;
            }
        }
        result.add(new TestCaseProxyGetProxyClassWithNullClassLoader());    
        class TestCaseProxyNewProxyInstanceWithNullClassLoader extends TestCase {
            public TestCaseProxyNewProxyInstanceWithNullClassLoader() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                Proxy.newProxyInstance(null, new Class[]{Object.class}, null); // should fail
                return 0.0;
            }
        }
        result.add(new TestCaseProxyNewProxyInstanceWithNullClassLoader());    

        class TestCaseResourceBundleGetBundle2ArgsWithModuleDifferentFromCallerModule extends TestCase {
            public TestCaseResourceBundleGetBundle2ArgsWithModuleDifferentFromCallerModule() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                java.util.ResourceBundle.getBundle("Any", java.lang.Object.class.getModule());
                return 0.0;
            }
        }
        result.add(new TestCaseResourceBundleGetBundle2ArgsWithModuleDifferentFromCallerModule());    

        class TestCaseResourceBundleGetBundle3ArgsWithModuleDifferentFromCallerModule extends TestCase {
            public TestCaseResourceBundleGetBundle3ArgsWithModuleDifferentFromCallerModule() {
                super(expectedException, expectedMsg);
            }
            @Override public Double apply(Double x) {
                java.util.ResourceBundle.getBundle("Any", Locale.getDefault() , java.lang.Object.class.getModule());
                return 0.0;
            }
        }
        result.add(new TestCaseResourceBundleGetBundle3ArgsWithModuleDifferentFromCallerModule());    

        return result;
    }

    @TestCaseFactory
    private static List<TestCase> testFile() {
        Class<? extends Throwable> expectedException = Error.class;

        Path root = new File(System.getProperty("java.io.tmpdir")).toPath().resolve("testpermcheck");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // cleanup
                try {
                    Util.deleteRecursively(root);
                } catch (IOException e) {
                    System.err.println("Error in cleaning up for testFile().");
                    e.printStackTrace(System.err);
                }
            }));

        // Create directory with test data
        if (root.toFile().exists()) {
            try {
                Util.deleteRecursively(root);
            } catch (IOException e) {
                throw new AssertionError("Internal error in TestCase", e);
            }
        }
        if (!root.toFile().mkdir()) throw new AssertionError("Internal error in TestCase");
        String[] subdirs = {"r", "rw", "w", "rwx", "x", "rx", "d", "rd"};
        for (String subdir : subdirs) {
            if (!root.resolve(subdir).toFile().mkdir()) throw new AssertionError("Internal error in TestCase");
        }
        UserPrincipal someOwner;
        try {
            Path tmp = root.resolve("r").resolve("file");
            Util.touch(tmp.toFile());
            someOwner = Files.getOwner(tmp);
            Util.touch(root.resolve("d").resolve("file").toFile());
            Util.touch(root.resolve("d").resolve("file2").toFile());
            Util.touch(root.resolve("rd").resolve("file3").toFile()); // for Desktop.moveToTrash
        } catch (IOException e) {
            throw new AssertionError("Internal error in TestCase", e);
        }

        // prepare zip file in r and rd directory:
        for (String[] subdirNamePair : new String[][]{ {"r", "file.zip"}, {"rd", "file1.zip" } , {"rd", "file2.zip" } }) {
            try (FileOutputStream fos = new FileOutputStream(root.resolve(subdirNamePair[0]).resolve(subdirNamePair[1]).toFile());
                ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
                ZipEntry ze = new ZipEntry("hello.txt");
                zos.putNextEntry(ze);
                byte[] data = "Hello World".getBytes(StandardCharsets.UTF_8);
                zos.write(data, 0, data.length);
                zos.closeEntry();
            } catch (IOException e) {
                throw new AssertionError("Internal error in TestCase", e);
            }
        }
            

        ArrayList<TestCase> result = new ArrayList<>();

        class TestCaseFileCanExecuteDenied extends TestCase {
            public TestCaseFileCanExecuteDenied() {
                super(expectedException, "file execute is not granted");
            }
            @Override public Double apply(Double x) {
                root.resolve("r").resolve("file").toFile().canExecute();
                return 0.0;
            }
        }
        result.add(new TestCaseFileCanExecuteDenied());

        class TestCaseFilesIsExecutableDenied extends TestCase {
            public TestCaseFilesIsExecutableDenied() {
                super(expectedException, "file execute is not granted");
            }
            @Override public Double apply(Double x) {
                Files.isExecutable(root.resolve("r").resolve("file"));
                return 0.0;
            }
        }
        result.add(new TestCaseFilesIsExecutableDenied());

        class TestCaseFileCanExecuteGranted extends TestCase {
            @Override public Double apply(Double x) {
                root.resolve("x").resolve("file").toFile().canExecute();
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFileCanExecuteGranted());

        class TestCaseProcessBuilderStartExecuteDenied extends TestCase {
            public TestCaseProcessBuilderStartExecuteDenied() {
                super(expectedException, "file execute is not granted");
            }
            @Override public Double apply(Double x) {
                try {
                    File prog = root.resolve("r").resolve("file").toFile();
                    new ProcessBuilder().command(prog.getAbsolutePath()).start();
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseProcessBuilderStartExecuteDenied());

        class TestCaseProcessBuilderStartReadDenied extends TestCase {
            public TestCaseProcessBuilderStartReadDenied() {
                super(expectedException, "file read is not granted");
            }
            @Override public Double apply(Double x) {
                try {
                    File prog = root.resolve("x").resolve("file").toFile();
                    new ProcessBuilder().command(prog.getAbsolutePath()).start();
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseProcessBuilderStartReadDenied());

        class TestCaseProcessBuilderStartReadAndExecuteGranted extends TestCase {
            @Override public Double apply(Double x) {
                try {
                    File prog = root.resolve("rx").toFile();
                    List<String> command = new ArrayList<>();
                    if (Util.isWindows()) {
                        prog = prog.toPath().resolve("cmd.exe").toFile();
                        command.add(prog.getAbsolutePath());
                        Util.fileCopy(new File("C:\\Windows\\system32\\cmd.exe").toPath(), prog.toPath());
                        command.add("/c");
                        command.add("exit");
                    } else {
                        prog = prog.toPath().resolve("true").toFile();
                        command.add(prog.getAbsolutePath());
                        Util.fileCopy(new File("/bin/true").toPath(), prog.toPath());
                    }
                    new ProcessBuilder(command).start();
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseProcessBuilderStartReadAndExecuteGranted());


        /**
         * This record describes a method of the File class to be invoked.
         * @param method name of the method
         * @param parent true, if the method is to be invoked on the parent of the target dir
         * @param arg a single argument or null
         * @param argClass class of the argument, or null
         */
        record FileMethod(String method, String filename, boolean parent, Class<?>[] argClasses, Object... args) {
            public FileMethod(String method) {
                this(method, "file", false, null, (Object[])null);
            }
            void invoke(File f) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
                                       InvocationTargetException, ExceptionInInitializerError, NullPointerException {
                f = new File(f, filename);
                if (parent) f = f.getParentFile();
                if (args == null || args.length == 0) {
                    f.getClass().getMethod(method).invoke(f);
                } else {
                    f.getClass().getMethod(method, argClasses).invoke(f, args);
                }
            }
            public String toString() {
                String s = "File." + method + "(";
                if (args != null && args.length > 0) {
                    s += String.join(", ", Arrays.stream(argClasses).map(Class::getSimpleName).toList());
                }
                s += ")";
                return s;
            }
        };
        FilenameFilter fnf = (d, n) -> true;
        FileFilter ff = (f) -> true;
        FileMethod[] fileMethodsRead = {
            new FileMethod("exists"),
            new FileMethod("isFile"),
            new FileMethod("isDirectory"),
            new FileMethod("length"),
            new FileMethod("getFreeSpace"),
            new FileMethod("getUsableSpace"),
            new FileMethod("getTotalSpace"),
            new FileMethod("lastModified"),
            new FileMethod("isHidden"),
            new FileMethod("canRead"),
            new FileMethod("getAbsolutePath"),
            new FileMethod("getAbsoluteFile"),
            new FileMethod("getCanonicalPath"),
            new FileMethod("getCanonicalFile"),
            new FileMethod("list", "file", true, null, (Object[])null),
            new FileMethod("listFiles", "file", true, null, (Object[])null),
            new FileMethod("list", "file", true, new Class[]{FilenameFilter.class}, fnf),
            new FileMethod("listFiles", "file", true, new Class[]{FileFilter.class}, ff),
            new FileMethod("listFiles", "file", true, new Class[]{FilenameFilter.class}, fnf)
        };
        class TestCaseFileInvokeMethodReadGranted extends TestCase {
            private FileMethod method;
            public TestCaseFileInvokeMethodReadGranted(FileMethod method) {
                super(method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                try {
                    File f = root.resolve("r").toFile();
                    method.invoke(f);
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError | InvocationTargetException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        for (FileMethod m : fileMethodsRead)
            result.add(new TestCaseFileInvokeMethodReadGranted(m));

        class TestCaseFileInvokeMethodReadDenied extends TestCase {
            private FileMethod method;
            public TestCaseFileInvokeMethodReadDenied(FileMethod method) {
                super(expectedException, "file read is not granted", method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                try {
                    File f = root.resolve("w").toFile();
                    method.invoke(f);
                } catch (InvocationTargetException e) {
                    if (e.getCause() != null && e.getCause().getClass() == expectedException) {
                        // Expected exceptions are thrown as received from the method call
                        assert Error.class.isAssignableFrom(expectedException);
                        throw (Error)expectedException.cast(e.getCause());
                    }
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        for (FileMethod m : fileMethodsRead)
            result.add(new TestCaseFileInvokeMethodReadDenied(m));

        record FilesMethod(String method, String filename, boolean parent, Object arg, Class<?> argClass) {
            public FilesMethod(String method) {
                this(method, "file", false, null, null);
            }
            void invokeStatic(Path p) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
                                       InvocationTargetException, ExceptionInInitializerError, NullPointerException {
                p = p.resolve(filename);
                if (parent) p = p.getParent();
                if (argClass == null) {
                    Files.class.getMethod(method, Path.class).invoke(null, p);
                } else {
                    Files.class.getMethod(method, Path.class, argClass).invoke(null, p, arg);
                }
            }
            public String toString() {
                return "Files." + method + "(" +
                       (arg == null ? "" : argClass.getSimpleName()) 
                   + ")";
            }
        };
        DirectoryStream.Filter<Path> df = (p) -> true;
        FilesMethod[] filesMethodsRead = {
            new FilesMethod("size"),
            new FilesMethod("isReadable"),
            new FilesMethod("isHidden"),
            new FilesMethod("isSymbolicLink"),
            new FilesMethod("getLastModifiedTime", "file", false, new LinkOption[0], LinkOption[].class),
            new FilesMethod("getOwner", "file", false, new LinkOption[0], LinkOption[].class),
            new FilesMethod("isRegularFile", "file", false, new LinkOption[0], LinkOption[].class),
            new FilesMethod("isDirectory", "file", false, new LinkOption[0], LinkOption[].class),
            new FilesMethod("exists", "file", false, new LinkOption[0], LinkOption[].class),
            new FilesMethod("notExists", "file", false, new LinkOption[0], LinkOption[].class),
            new FilesMethod("newDirectoryStream", "file", true, null, null),
            new FilesMethod("newDirectoryStream", "file", true, "*.*", String.class),
            new FilesMethod("newDirectoryStream", "file", true, df, DirectoryStream.Filter.class),
            new FilesMethod("getFileStore"),
            new FilesMethod("isSameFile", "file", false, root.resolve("r"), Path.class)
        };

        class TestCaseFilesInvokeMethodReadGranted extends TestCase {
            private FilesMethod method;
            public TestCaseFilesInvokeMethodReadGranted(FilesMethod method) {
                super(method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                try {
                    Path p = root.resolve("r");
                    method.invokeStatic(p);
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError | InvocationTargetException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }                    
                return Math.sqrt(x);
            }
        }
        for (FilesMethod m : filesMethodsRead)
            result.add(new TestCaseFilesInvokeMethodReadGranted(m));

        class TestCaseFilesInvokeMethodReadDenied extends TestCase {
            private FilesMethod method;
            public TestCaseFilesInvokeMethodReadDenied(FilesMethod method) {
                super(expectedException, "file read is not granted", method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("w");
                try {
                    method.invokeStatic(p);
                } catch (InvocationTargetException e) {
                    if (e.getCause() != null && e.getCause().getClass() == expectedException) {
                        // Expected exceptions are thrown as received from the method call
                        assert Error.class.isAssignableFrom(expectedException);
                        throw (Error)expectedException.cast(e.getCause());
                    }
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }

                return 0.0;
            }
        }
        for (FilesMethod m : filesMethodsRead)
            result.add(new TestCaseFilesInvokeMethodReadDenied(m));

        class TestCaseFilesReadAttributesGranted extends TestCase {
            @Override public Double apply(Double x) {
                Path p = root.resolve("r").resolve("file");
                try {
                    Files.readAttributes(p, "*");
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFilesReadAttributesGranted());

        class TestCaseFilesReadAttributesDenied extends TestCase {
            public TestCaseFilesReadAttributesDenied() {
                super(expectedException, "file read is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("w").resolve("file");
                try {
                    Files.readAttributes(p, "*");
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFilesReadAttributesDenied());

        class TestCasePathRegisterGranted extends TestCase {
            @Override public Double apply(Double x) {
                Path p = root.resolve("r");
                try {
                    p.register(FileSystems.getDefault().newWatchService(), StandardWatchEventKinds.ENTRY_CREATE);
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCasePathRegisterGranted());

        class TestCasePathRegisterDenied extends TestCase {
            public TestCasePathRegisterDenied() {
                super(expectedException, "file read is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("w");
                try {
                    p.register(FileSystems.getDefault().newWatchService(), StandardWatchEventKinds.ENTRY_CREATE);
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCasePathRegisterDenied());

        class TestCaseFileInputStreamConstructorGranted extends TestCase {
            @Override public Double apply(Double x) {
                Path p = root.resolve("r").resolve("file");
                try (FileInputStream fis = new FileInputStream(p.toFile());
                     FileInputStream fis2 = new FileInputStream(p.toAbsolutePath().toString())) {
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFileInputStreamConstructorGranted());

        class TestCaseFileInputStreamConstructorFileDenied extends TestCase {
            public TestCaseFileInputStreamConstructorFileDenied() {
                super(expectedException, "file read is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("w").resolve("file");
                try (FileInputStream fis = new FileInputStream(p.toFile())) {
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFileInputStreamConstructorFileDenied());

        class TestCaseFileInputStreamConstructorStringDenied extends TestCase {
            public TestCaseFileInputStreamConstructorStringDenied() {
                super(expectedException, "file read is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("w").resolve("file");
                try (FileInputStream fis = new FileInputStream(p.toAbsolutePath().toString())) {
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFileInputStreamConstructorStringDenied());

        record ZipFileConstructor(Object[] arg, Class<?>[] allArgClasses) {
            ZipFile invoke(File f) throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
                Constructor<ZipFile> c;
                Object[] allArgs = new Object[arg.length+1];
                System.arraycopy(arg, 0, allArgs, 1, arg.length);
                if (allArgClasses[0] == File.class) {
                    allArgs[0] = f;
                } else if (allArgClasses[0] == String.class) {
                    allArgs[0] = f.getAbsolutePath();
                } else {
                    throw new IllegalArgumentException();
                }
                c = ZipFile.class.getConstructor(allArgClasses);
                return c.newInstance(allArgs);
            }
            public String toString() {
                return "new ZipFile(" + Arrays.toString(allArgClasses).replaceAll("[\\[\\]]", "") + ")";
            }
        };
        ZipFileConstructor[] zfc = {
            // The first array contains one arg less than there are classes in the second array.
            new ZipFileConstructor(new Object[0], new Class<?>[] { File.class }),
            new ZipFileConstructor(new Object[] { StandardCharsets.UTF_8 }, new Class<?>[] { File.class, Charset.class }),
            new ZipFileConstructor(new Object[] { ZipFile.OPEN_READ }, new Class<?>[]{ File.class, int.class }),
            new ZipFileConstructor(new Object[] { ZipFile.OPEN_READ, StandardCharsets.UTF_8 }, new Class<?>[]{ File.class, int.class, Charset.class }),
            new ZipFileConstructor(new Object[0], new Class<?>[] { String.class }),
            new ZipFileConstructor(new Object[] { StandardCharsets.UTF_8 }, new Class<?>[] { String.class, Charset.class }),
        };

        class TestCaseZipFileConstructorGranted extends TestCase {
            private ZipFileConstructor zfc;
            public TestCaseZipFileConstructorGranted(ZipFileConstructor zfc) {
                this.zfc = zfc;
            }
            @Override public Double apply(Double x) {
                File f = root.resolve("r").resolve("file.zip").toFile();
                try (ZipFile zf = zfc.invoke(f)) {
                } catch (IOException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        for (ZipFileConstructor z : zfc)
            result.add(new TestCaseZipFileConstructorGranted(z));

        class TestCaseZipFileConstructorDenied extends TestCase {
            private ZipFileConstructor zfc;
            public TestCaseZipFileConstructorDenied(ZipFileConstructor zfc) {
                super(expectedException, "file read is not granted", zfc.toString());
                this.zfc = zfc;
            }
            @Override public Double apply(Double x) {
                File f = root.resolve("w").resolve("file.zip").toFile();
                try (ZipFile zf = zfc.invoke(f)) {
                } catch (InvocationTargetException e) {
                    if (e.getCause() != null && e.getCause().getClass() == expectedException) {
                        // Expected exceptions are thrown as received from the method call
                        assert Error.class.isAssignableFrom(expectedException);
                        throw (Error)expectedException.cast(e.getCause());
                    }
                } catch (IOException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }

        for (ZipFileConstructor z : zfc)
            result.add(new TestCaseZipFileConstructorDenied(z));

        class TestCaseZipFileConstructorModeDeleteDenied extends TestCase {
            public TestCaseZipFileConstructorModeDeleteDenied() {
                super(expectedException, "file delete is not granted");
            }
            @Override public Double apply(Double x) {
                File f = root.resolve("r").resolve("file.zip").toFile();
                try (ZipFile zf = new ZipFile(f, ZipFile.OPEN_READ | ZipFile.OPEN_DELETE)) {
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseZipFileConstructorModeDeleteDenied());

        // We omit a "denied" test case for calling the constructor 
        // new ZipFile(".../r/file.zip", ZipFile.OPEN_READ | ZipFile.OPEN_DELETE, <CharSet>))
        // since this one is implicitly called by 
        // new ZipFile(".../r/file.zip", ZipFile.OPEN_READ | ZipFile.OPEN_DELETE))
        //
        // But the respective "granted" testcases both are included:

        class TestCaseZipFileConstructorModeDeleteGranted extends TestCase {
            @Override public Double apply(Double x) {
                File f1 = root.resolve("rd").resolve("file1.zip").toFile();
                try (ZipFile zf = new ZipFile(f1, ZipFile.OPEN_READ | ZipFile.OPEN_DELETE)) {
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                File f2 = root.resolve("rd").resolve("file2.zip").toFile();
                try (ZipFile zf = new ZipFile(f2, ZipFile.OPEN_READ | ZipFile.OPEN_DELETE, StandardCharsets.UTF_8)) {
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseZipFileConstructorModeDeleteGranted());



        class TestCaseDesktopMoveToTrashGranted extends TestCase {
            @Override public Double apply(Double x) {
                if (Desktop.isDesktopSupported()) {
                    Path p = root.resolve("rd").resolve("file3");
                    try {
                        Desktop.getDesktop().moveToTrash(p.toFile());
                    } catch (UnsupportedOperationException | NullPointerException | IllegalArgumentException e) {
                        // shouldn't happen
                        throw new AssertionError("Internal error in TestCase", e);
                    }
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseDesktopMoveToTrashGranted());

        class TestCaseDesktopMoveToTrashDenied extends TestCase {
            public TestCaseDesktopMoveToTrashDenied() {
                super(expectedException, "file delete is not granted");
            }
            @Override public Double apply(Double x) {
                if (Desktop.isDesktopSupported()) {
                    Path p = root.resolve("r").resolve("file");
                    try {
                        Desktop.getDesktop().moveToTrash(p.toFile());
                    } catch (UnsupportedOperationException | NullPointerException | IllegalArgumentException e) {
                        // shouldn't happen
                        throw new AssertionError("Internal error in TestCase", e);
                    }
                } else {
                    // Cannot test this on this platform.
                    setExpectedException(null);
                    setExpectedMsg(null);
                    return Math.sqrt(x);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseDesktopMoveToTrashDenied());

        class TestCaseFileMkdirsGranted extends TestCase {
            @Override public Double apply(Double x) {
                Path p = root.resolve("rw").resolve("sub").resolve("subsub");
                boolean created = p.toFile().mkdirs();
                if (!created) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase, File.mkdirs should return true");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFileMkdirsGranted());

        class TestCaseFileMkdirsReadDenied extends TestCase {
            public TestCaseFileMkdirsReadDenied() {
                super(expectedException, "file read is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("w").resolve("sub").resolve("subsub");
                boolean created = p.toFile().mkdirs();
                if (created) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase, File.mkdirs should return false");
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFileMkdirsReadDenied());

        class TestCaseFileMkdirsWriteDenied extends TestCase {
            public TestCaseFileMkdirsWriteDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("r").resolve("sub").resolve("subsub");
                boolean created = p.toFile().mkdirs();
                if (created) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase, File.mkdirs should return false");
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFileMkdirsWriteDenied());


        class TestCaseFilesCreateDirectoriesGranted extends TestCase {
            @Override public Double apply(Double x) {
                Path p = root.resolve("rw").resolve("sub").resolve("subsub");
                try {
                    Files.createDirectories(p);
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFilesCreateDirectoriesGranted());

        class TestCaseFilesCreateDirectoriesReadDenied extends TestCase {
            public TestCaseFilesCreateDirectoriesReadDenied() {
                super(expectedException, "file read is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("w").resolve("sub").resolve("subsub");
                try {
                    Files.createDirectories(p);
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFilesCreateDirectoriesReadDenied());

        class TestCaseFilesCreateDirectoriesWriteDenied extends TestCase {
            public TestCaseFilesCreateDirectoriesWriteDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("r").resolve("sub").resolve("subsub");
                try {
                    Files.createDirectories(p);
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFilesCreateDirectoriesWriteDenied());

        class TestCaseFilesCopyReadWriteGranted extends TestCase {
            @Override public Double apply(Double x) {
                Path from = root.resolve("r").resolve("file");
                Path to = root.resolve("w").resolve("file2");
                try {
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFilesCopyReadWriteGranted());

        class TestCaseFilesCopyReadDenied extends TestCase {
            public TestCaseFilesCopyReadDenied() {
                super(expectedException, "file read is not granted");
            }
            @Override public Double apply(Double x) {
                Path from = root.resolve("w").resolve("file");
                Path to = root.resolve("w").resolve("file2");
                try {
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFilesCopyReadDenied());

        class TestCaseFilesCopyWriteDenied extends TestCase {
            public TestCaseFilesCopyWriteDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path from = root.resolve("r").resolve("file");
                Path to = root.resolve("r").resolve("file2");
                try {
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFilesCopyWriteDenied());

        abstract class TestCaseRandomAccessFileConstructor extends TestCase {
            boolean asFile;
            String mode;
            TestCaseRandomAccessFileConstructor() {
            }
            TestCaseRandomAccessFileConstructor(Class<? extends Throwable> expectedException, String expectedMsg) {
                super(expectedException, expectedMsg);
            }
            TestCaseRandomAccessFileConstructor as(boolean asFile, String mode) {
                this.asFile = asFile;
                this.mode = mode;
                return this;
            }
            RandomAccessFile invoke(File f) throws FileNotFoundException {
                if (asFile) return new RandomAccessFile(f, mode);
                return new RandomAccessFile(f.getAbsolutePath(), mode);
            }
        }
        class TestCaseRandomAccessFileConstructorReadGranted extends TestCaseRandomAccessFileConstructor {
            @Override public Double apply(Double x) {
                File f = root.resolve("r").resolve("file").toFile();
                try (RandomAccessFile raf = invoke(f) ){
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                } 
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseRandomAccessFileConstructorReadGranted().as(false, "r"));
        result.add(new TestCaseRandomAccessFileConstructorReadGranted().as(true, "r"));

        class TestCaseRandomAccessFileConstructorReadWriteGranted extends TestCaseRandomAccessFileConstructor {
            @Override public Double apply(Double x) {
                File f = root.resolve("rw").resolve("file.raf").toFile();
                try (RandomAccessFile raf = invoke(f) ){
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseRandomAccessFileConstructorReadWriteGranted().as(false, "rw"));
        result.add(new TestCaseRandomAccessFileConstructorReadWriteGranted().as(true, "rw"));

        class TestCaseRandomAccessFileConstructorReadDenied extends TestCaseRandomAccessFileConstructor {
            public TestCaseRandomAccessFileConstructorReadDenied() {
                super(expectedException, "file read is not granted");
            }
            @Override public Double apply(Double x) {
                File f = root.resolve("w").resolve("file").toFile();
                try (RandomAccessFile raf = invoke(f) ){
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseRandomAccessFileConstructorReadDenied().as(false, "r"));        
        result.add(new TestCaseRandomAccessFileConstructorReadDenied().as(true, "r"));        

        class TestCaseRandomAccessFileConstructorReadWriteDenied extends TestCaseRandomAccessFileConstructor {
            public TestCaseRandomAccessFileConstructorReadWriteDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                File f = root.resolve("r").resolve("file.raf").toFile();
                try (RandomAccessFile raf = invoke(f) ){
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseRandomAccessFileConstructorReadWriteDenied().as(false, "rw"));        
        result.add(new TestCaseRandomAccessFileConstructorReadWriteDenied().as(true, "rw"));        
        
        class TestCaseFileOutputStreamConstructorGranted extends TestCase {
            @Override public Double apply(Double x) {
                Path p = root.resolve("w").resolve("file");
                try (FileOutputStream fos = new FileOutputStream(p.toFile());
                     FileOutputStream fos2 = new FileOutputStream(p.toAbsolutePath().toString());
                     FileOutputStream fos3 = new FileOutputStream(p.toFile(), false);
                     FileOutputStream fos4 = new FileOutputStream(p.toAbsolutePath().toString(), false);
                    ) {
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFileOutputStreamConstructorGranted());

        class TestCaseFileOutputStreamConstructorFileDenied extends TestCase {
            public TestCaseFileOutputStreamConstructorFileDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("r").resolve("file");
                try (FileOutputStream fos = new FileOutputStream(p.toFile())) {
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFileOutputStreamConstructorFileDenied());

        class TestCaseFileOutputStreamConstructorFileBooleanDenied extends TestCase {
            public TestCaseFileOutputStreamConstructorFileBooleanDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("r").resolve("file");
                try (FileOutputStream fos = new FileOutputStream(p.toFile(), false)) {
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFileOutputStreamConstructorFileBooleanDenied());

        class TestCaseFileOutputStreamConstructorStringDenied extends TestCase {
            public TestCaseFileOutputStreamConstructorStringDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("r").resolve("file");
                try (FileOutputStream fos = new FileOutputStream(p.toFile().getAbsolutePath())) {
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFileOutputStreamConstructorStringDenied());

        class TestCaseFileOutputStreamConstructorStringBooleanDenied extends TestCase {
            public TestCaseFileOutputStreamConstructorStringBooleanDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("r").resolve("file");
                try (FileOutputStream fos = new FileOutputStream(p.toFile().getAbsolutePath(), false)) {
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFileOutputStreamConstructorStringBooleanDenied());
        

        FileMethod[] fileMethodsWrite = {
            new FileMethod("setReadOnly"), // must be here before the following setWritable, since otherwise cleanup won't succeed.
            new FileMethod("setWritable", "file", false, new Class<?>[]{boolean.class}, true),
            new FileMethod("setWritable", "file", false, new Class<?>[]{boolean.class, boolean.class}, true, false),
            new FileMethod("setExecutable", "file", false, new Class<?>[]{boolean.class}, true),
            new FileMethod("setExecutable", "file", false, new Class<?>[]{boolean.class, boolean.class}, true, false),
            new FileMethod("setReadable", "file", false, new Class<?>[]{boolean.class}, true),
            new FileMethod("setReadable", "file", false, new Class<?>[]{boolean.class, boolean.class}, true, false),
            new FileMethod("setLastModified", "file", false, new Class<?>[]{long.class}, new java.util.Date().getTime()),
            new FileMethod("canWrite"),
            new FileMethod("mkdir", "subdir", false, null, (Object[])null),
            new FileMethod("createNewFile", "file", false, null, (Object[])null)
        };
        class TestCaseFileInvokeMethodWriteDenied extends TestCase {
            private FileMethod method;
            public TestCaseFileInvokeMethodWriteDenied(FileMethod method) {
                super(expectedException, "file write is not granted", method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                try {
                    File f = root.resolve("r").toFile();
                    method.invoke(f);
                } catch (InvocationTargetException e) {
                    if (e.getCause() != null && e.getCause().getClass() == expectedException) {
                        // Expected exceptions are thrown as received from the method call
                        assert Error.class.isAssignableFrom(expectedException);
                        throw (Error)expectedException.cast(e.getCause());
                    }
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        for (FileMethod m : fileMethodsWrite)
            result.add(new TestCaseFileInvokeMethodWriteDenied(m));

        class TestCaseFileInvokeMethodWriteGranted extends TestCase {
            private FileMethod method;
            public TestCaseFileInvokeMethodWriteGranted(FileMethod method) {
                super(method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                try {
                    File f = root.resolve("w").toFile();
                    method.invoke(f);
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError | InvocationTargetException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        for (FileMethod m : fileMethodsWrite)
            result.add(new TestCaseFileInvokeMethodWriteGranted(m));

        FilesMethod[] filesMethodsWrite = {
            new FilesMethod("isWritable"),
            new FilesMethod("setOwner", "file", false, someOwner, UserPrincipal.class),
            new FilesMethod("setLastModifiedTime", "file", false, FileTime.from(Instant.now()), FileTime.class),
            new FilesMethod("createDirectory", "dir", false, new FileAttribute[0], FileAttribute[].class),
            new FilesMethod("createFile", "newfile", false, new FileAttribute[0], FileAttribute[].class)
        };

        class TestCaseFilesInvokeMethodWriteGranted extends TestCase {
            private FilesMethod method;
            public TestCaseFilesInvokeMethodWriteGranted(FilesMethod method) {
                super(method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                try {
                    Path p = root.resolve("w");
                    method.invokeStatic(p);
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError | InvocationTargetException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }                    
                return Math.sqrt(x);
            }
        }
        for (FilesMethod m : filesMethodsWrite)
            result.add(new TestCaseFilesInvokeMethodWriteGranted(m));

        class TestCaseFilesInvokeMethodWriteDenied extends TestCase {
            private FilesMethod method;
            public TestCaseFilesInvokeMethodWriteDenied(FilesMethod method) {
                super(expectedException, "file write is not granted", method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("r");
                try {
                    method.invokeStatic(p);
                } catch (InvocationTargetException e) {
                    if (e.getCause() != null && e.getCause().getClass() == expectedException) {
                        // Expected exceptions are thrown as received from the method call
                        assert Error.class.isAssignableFrom(expectedException);
                        throw (Error)expectedException.cast(e.getCause());
                    }
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }

                return 0.0;
            }
        }
        for (FilesMethod m : filesMethodsWrite)
            result.add(new TestCaseFilesInvokeMethodWriteDenied(m));

        class TestCaseFilesWriteAttributesGranted extends TestCase {
            @Override public Double apply(Double x) {
                Path p = root.resolve("w").resolve("file");
                try {
                    Files.setAttribute(p, "basic:lastModifiedTime", FileTime.from(Instant.now()));
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFilesWriteAttributesGranted());

        class TestCaseFilesWriteAttributesDenied extends TestCase {
            public TestCaseFilesWriteAttributesDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("r").resolve("file");
                try {
                    Files.setAttribute(p, "basic:lastModifiedTime", FileTime.from(Instant.now()));
                } catch (IOException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFilesWriteAttributesDenied());

        class TestCaseFileRenameWriteSrcDenied extends TestCase {
            public TestCaseFileRenameWriteSrcDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path from = root.resolve("r").resolve("file2");
                Path to = root.resolve("w").resolve("file3");
                try {
                    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase");
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFileRenameWriteSrcDenied());

        class TestCaseFileRenameWriteDestDenied extends TestCase {
            public TestCaseFileRenameWriteDestDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path from = root.resolve("w").resolve("file2");
                Path to = root.resolve("r").resolve("file3");
                try {
                    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase");
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFileRenameWriteDestDenied());

        class TestCaseFileRenameGranted extends TestCase {
            @Override public Double apply(Double x) {
                Path from = root.resolve("w").resolve("file2");
                Path to = root.resolve("w").resolve("file3");
                boolean renamed = from.toFile().renameTo(to.toFile());
                if (!renamed) {
                    throw new AssertionError("Internal error in TestCase");
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFileRenameGranted());



        class TestCaseFilesMoveWriteSrcDenied extends TestCase {
            public TestCaseFilesMoveWriteSrcDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path from = root.resolve("r").resolve("file3");
                Path to = root.resolve("w").resolve("file4");
                try {
                    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFilesMoveWriteSrcDenied());

        class TestCaseFilesMoveWriteDestDenied extends TestCase {
            public TestCaseFilesMoveWriteDestDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path from = root.resolve("w").resolve("file3");
                Path to = root.resolve("r").resolve("file4");
                try {
                    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFilesMoveWriteDestDenied());

        class TestCaseFilesMoveGranted extends TestCase {
            @Override public Double apply(Double x) {
                Path from = root.resolve("w").resolve("file3");
                Path to = root.resolve("w").resolve("file4");
                try {
                    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFilesMoveGranted());

        class TestCaseFileCreateTempFileDenied extends TestCase {
            public TestCaseFileCreateTempFileDenied() {
                super(expectedException, "file write is not granted");
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("r");
                try {
                    File.createTempFile("pfx", ".sf", p.toFile());
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        result.add(new TestCaseFileCreateTempFileDenied());

        class TestCaseFileCreateTempFileGranted extends TestCase {
            @Override public Double apply(Double x) {
                Path p = root.resolve("w");
                try {
                    File.createTempFile("pfx", ".sf", p.toFile());
                } catch (IOException e) {
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseFileCreateTempFileGranted());

        FileMethod[] fileMethodsDelete = {
            new FileMethod("delete"),
            new FileMethod("deleteOnExit")
        };
        class TestCaseFileInvokeMethodDeleteDenied extends TestCase {
            private FileMethod method;
            public TestCaseFileInvokeMethodDeleteDenied(FileMethod method) {
                super(expectedException, "file delete is not granted", method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                try {
                    File f = root.resolve("w").toFile();
                    method.invoke(f);
                } catch (InvocationTargetException e) {
                    if (e.getCause() != null && e.getCause().getClass() == expectedException) {
                        // Expected exceptions are thrown as received from the method call
                        assert Error.class.isAssignableFrom(expectedException);
                        throw (Error)expectedException.cast(e.getCause());
                    }
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return 0.0;
            }
        }
        for (FileMethod m : fileMethodsDelete)
            result.add(new TestCaseFileInvokeMethodDeleteDenied(m));

        class TestCaseFileInvokeMethodDeleteGranted extends TestCase {
            private FileMethod method;
            public TestCaseFileInvokeMethodDeleteGranted(FileMethod method) {
                super(method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                try {
                    File f = root.resolve("d").toFile();
                    method.invoke(f);
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError | InvocationTargetException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }
                return Math.sqrt(x);
            }
        }
        for (FileMethod m : fileMethodsDelete)
            result.add(new TestCaseFileInvokeMethodDeleteGranted(m));

        FilesMethod[] filesMethodsDelete = {
            new FilesMethod("delete", "file2", false, null, null),
            new FilesMethod("deleteIfExists")
        };

        class TestCaseFilesInvokeMethodDeleteGranted extends TestCase {
            private FilesMethod method;
            public TestCaseFilesInvokeMethodDeleteGranted(FilesMethod method) {
                super(method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                try {
                    Path p = root.resolve("d");
                    method.invokeStatic(p);
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError | InvocationTargetException e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }                    
                return Math.sqrt(x);
            }
        }
        for (FilesMethod m : filesMethodsDelete)
            result.add(new TestCaseFilesInvokeMethodDeleteGranted(m));

        class TestCaseFilesInvokeMethodDeleteDenied extends TestCase {
            private FilesMethod method;
            public TestCaseFilesInvokeMethodDeleteDenied(FilesMethod method) {
                super(expectedException, "file delete is not granted", method.toString());
                this.method = method;
            }
            @Override public Double apply(Double x) {
                Path p = root.resolve("w");
                try {
                    method.invokeStatic(p);
                } catch (InvocationTargetException e) {
                    if (e.getCause() != null && e.getCause().getClass() == expectedException) {
                        // Expected exceptions are thrown as received from the method call
                        assert Error.class.isAssignableFrom(expectedException);
                        throw (Error)expectedException.cast(e.getCause());
                    }
                } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | SecurityException | NullPointerException | ExceptionInInitializerError e) {
                    // shouldn't happen
                    throw new AssertionError("Internal error in TestCase", e);
                }

                return 0.0;
            }
        }
        for (FilesMethod m : filesMethodsDelete)
            result.add(new TestCaseFilesInvokeMethodDeleteDenied(m));



        return result;

    }

    @TestCaseFactory
    private static List<TestCase> testProperty() {
        Class<? extends Throwable> expectedException = Error.class;
        ArrayList<TestCase> result = new ArrayList<>();

        class TestCaseSystemGetPropertyDenied extends TestCase {
            public TestCaseSystemGetPropertyDenied() {
                super(expectedException, "property read is not granted");
            }
            @Override public Double apply(Double x) {
                System.getProperty("allowed.to.write.a"); // denied
                return 0.0;
            }
        }
        result.add(new TestCaseSystemGetPropertyDenied());

        class TestCaseSystemGetPropertyWithDefaultDenied extends TestCase {
            public TestCaseSystemGetPropertyWithDefaultDenied() {
                super(expectedException, "property read is not granted");
            }
            @Override public Double apply(Double x) {
                System.getProperty("allowed.to.write.a", "value"); // denied
                return 0.0;
            }
        }
        result.add(new TestCaseSystemGetPropertyWithDefaultDenied());

        class TestCaseSystemGetPropertyGranted extends TestCase {
            @Override public Double apply(Double x) {
                System.getProperty("java.version");
                System.getProperty("java.version", "17"); // with default value
                System.getProperty("allowed.to.read.a"); // test allowed.to.read.*
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseSystemGetPropertyGranted());

        class TestCaseSystemGetPropertiesDenied extends TestCase {
            public TestCaseSystemGetPropertiesDenied() {
                super(expectedException, "is not granted for '*'");
            }
            @Override public Double apply(Double x) {
                System.getProperties();
                return 0.0;
            }
        }
        result.add(new TestCaseSystemGetPropertiesDenied());

        class TestCaseSystemSetPropertiesDenied extends TestCase {
            public TestCaseSystemSetPropertiesDenied() {
                super(expectedException, "is not granted for '*'");
            }
            @Override public Double apply(Double x) {
                System.setProperties(new Properties());
                return 0.0;
            }
        }
        result.add(new TestCaseSystemSetPropertiesDenied());

        class TestCaseSystemSetPropertyDenied extends TestCase {
            public TestCaseSystemSetPropertyDenied() {
                super(expectedException, "property write is not granted");
            }
            @Override public Double apply(Double x) {
                System.setProperty("allowed.to.read.a", "42"); // denied
                return 0.0;
            }
        }
        result.add(new TestCaseSystemSetPropertyDenied());

        class TestCaseSystemSetPropertyGranted extends TestCase {
            @Override public Double apply(Double x) {
                System.setProperty("allowed.to.write.a", "value"); // test allowed.to.write.*
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseSystemSetPropertyGranted());

        class TestCaseSystemClearPropertyDenied extends TestCase {
            public TestCaseSystemClearPropertyDenied() {
                super(expectedException, "property write is not granted");
            }
            @Override public Double apply(Double x) {
                System.clearProperty("allowed.to.read.a"); // denied
                return 0.0;
            }
        }
        result.add(new TestCaseSystemClearPropertyDenied());

        class TestCaseSystemClearPropertyGranted extends TestCase {
            @Override public Double apply(Double x) {
                System.clearProperty("allowed.to.write.a"); // test allowed.to.write.*
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseSystemClearPropertyGranted());

        class TestCaseTimeZoneSetDefaultDenied extends TestCase {
            public TestCaseTimeZoneSetDefaultDenied() {
                super(expectedException, "property write is not granted for 'user.timezone'");
            }
            @Override public Double apply(Double x) {
                TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
                return 0.0;
            }
        }
        result.add(new TestCaseTimeZoneSetDefaultDenied());

        class TestCaseLocaleSetDefaultDenied extends TestCase {
            public TestCaseLocaleSetDefaultDenied() {
                super(expectedException, "property write is not granted for 'user.language'");
            }
            @Override public Double apply(Double x) {
                Locale.setDefault(Locale.GERMANY);
                return 0.0;
            }
        }
        result.add(new TestCaseLocaleSetDefaultDenied());

        class TestCaseLocaleSetDefaultForCategoryDenied extends TestCase {
            public TestCaseLocaleSetDefaultForCategoryDenied() {
                super(expectedException, "property write is not granted for 'user.language'");
            }
            @Override public Double apply(Double x) {
                Locale.setDefault(Locale.Category.DISPLAY, Locale.GERMANY);
                return 0.0;
            }
        }
        result.add(new TestCaseLocaleSetDefaultForCategoryDenied());

        return result;
    }

    @TestCaseFactory
    private static List<TestCase> testEnv() {
        Class<? extends Throwable> expectedException = Error.class;
        ArrayList<TestCase> result = new ArrayList<>();

        class TestCaseSystemGetenvDenied extends TestCase {
            public TestCaseSystemGetenvDenied() {
                super(expectedException, "env is not granted for 'PATH'");
            }
            @Override public Double apply(Double x) {
                System.getenv("PATH"); // denied
                return 0.0;
            }
        }
        result.add(new TestCaseSystemGetenvDenied());

        class TestCaseSystemGetenvGranted extends TestCase {
            @Override public Double apply(Double x) {
                System.getenv("OS"); 
                return Math.sqrt(x);
            }
        }
        result.add(new TestCaseSystemGetenvGranted());

        class TestCaseSystemGetenvAllDenied extends TestCase {
            public TestCaseSystemGetenvAllDenied() {
                super(expectedException, "env is not granted for '*'");
            }
            @Override public Double apply(Double x) {
                System.getenv(); // denied
                return 0.0;
            }
        }
        result.add(new TestCaseSystemGetenvAllDenied());

        class TestCaseProcessBuilderEnvironmentAllDenied extends TestCase {
            public TestCaseProcessBuilderEnvironmentAllDenied() {
                super(expectedException, "env is not granted for '*'");
            }
            @Override public Double apply(Double x) {
                new ProcessBuilder("echo").environment();
                return 0.0;
            }
        }
        result.add(new TestCaseProcessBuilderEnvironmentAllDenied());

        

        return result;
    }
}
