package de.hsh.permcheck;

import java.beans.MethodDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Stream;

import de.hsh.permcheck.internal.ConstructorVisitorWrapper;
import de.hsh.permcheck.internal.EnterInsert;
import de.hsh.permcheck.internal.ExitInsert;
import de.hsh.permcheck.internal.Insert;
import de.hsh.permcheck.internal.MyUntrustedClassAdvices;
import de.hsh.permcheck.internal.Spec;
import de.hsh.permcheck.internal.Specs;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Extendable;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.MethodDelegationBinder;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

public class Start {

    private static boolean byteBuddyIsConfigured = false;
    
    // static {
    //     System.out.println("Klasse " + Start.class + " geladen durch: " + Start.class.getClassLoader());
    //     new Exception().printStackTrace(System.out);
    // }

    /**
     * 
     * @param policy specification of denials and permissions, separated by line breaks 
     * @param password an arbitrary password, that must be passed to {@link #pause(String)}
     *      and {@link #resume(String)}.
     * @param tempFolderForBootstrapInjection folder, where temporary class files may be saved. If the folder does not exist,
     *      it will be created automatically. If tempPath is null, the default temp folder as specified
     *      by the system property java.io.tmpdir is used.
     * @throws IllegalArgumentException if the tempPath parameter specifies an existing regular file.
     * @throws IOException if the tempPath folder cannot be created
     * @throws Error if the byte buddy agent cannot be found or installed.
     */
    public static void configureByteBuddyAgentIfAny(String policy, String password, String tempFolderForBootstrapInjection) throws IllegalArgumentException, IOException, Error {
        Instrumentation instrumentation = null;
        try {
            instrumentation = ByteBuddyAgent.getInstrumentation();
            //System.out.println("Bytebuddy.getInstrumentation() succeeded!");
        } catch (IllegalStateException ex) {
            //System.out.println("Bytebuddy.getInstrumentation() failed!");
            try {
                instrumentation = ByteBuddyAgent.install();
                //System.out.println("Bytebuddy.install() succeeded!");
            } catch (IllegalStateException ex2) {
                throw new Error("Cannot find installed byte buddy agent.", ex2);
            }
        }

        String internalPkgPrefix = Start.class.getPackageName() + ".internal.";

        // Quelle: https://stackoverflow.com/questions/69267044/insert-a-custom-class-into-bootstrap-classloader-into-java-base-module
        TypePool typePool = TypePool.Default.ofSystemLoader();
        TypeDescription myAdvicesTd = typePool.describe(internalPkgPrefix+"MyAdvices").resolve();
        TypeDescription myUntrustedClassAdvicesTd = typePool.describe(internalPkgPrefix+"MyUntrustedClassAdvices").resolve();
        // TypeDescription myUntrustedClassConstructorInterceptorTd = typePool.describe(internalPkgPrefix+"MyUntrustedClassConstructorInterceptor").resolve();
        

        File temp;
        if (tempFolderForBootstrapInjection == null) {
            temp = Files.createTempDirectory("tmp").toFile();
        } else {
            temp = new File(tempFolderForBootstrapInjection);
            if (!temp.isDirectory()) {
                if (temp.exists()) {
                    throw new IllegalArgumentException("Cannot create dir "+temp);
                }
                temp.mkdirs();
            }
        }
        if (Stream.of(policy.split("\\R")).map(String::trim).filter(s -> 
                s.startsWith("verbose ") && s.substring(8).contains("install") && !s.substring(8).contains("no-install")
            ).count() > 0) {
            System.out.println("[PERMCHECK] Using temp folder for class injection to bootstrap classloader: " + temp);
        }
        // Scanner console = new Scanner(System.in);
        // console.nextLine();

        ClassInjector injector = ClassInjector.UsingInstrumentation.of(temp, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, instrumentation);
        //ClassInjector injector = ClassInjector.UsingUnsafe.ofBootLoader();

        String[] classNames = {
                "MyAdvices", 
                "MyUntrustedClassAdvices", 
                "Hook", 
                "Helper", 
                "PermcheckException",
                "Logger", 
                "Spec",
                "Specs",
                "Action",
                "Insert",
                "EnterInsert",
                "ExitInsert",
                "ActionInsert",
                "DenyAllInsert",

                "AbstractCheck",
                "AbstractCheck$Registry",

                "AbstractDenyCheck",
                "DenyExitVmCheck",
                "DenyReflectionSetAccessibleCheck",
                "DenyReflectionAccessDeclaredMembersCheck",
                "DenyReflectionAccessDeclaredMembersCheck$DenyTargetOnDifferentClassLoadersInsert",
                "DenyReflectionAccessDeclaredMembersCheck$DenyFirstArgOnDifferentClassLoadersInsert",
                "DenyReflectionAccessDeclaredMembersCheck$DenyFirstArgsClassOnDifferentClassLoadersInsert",
                "DenyReflectionGetStackTraceCheck",
                "DenyReflectionGetStackTraceCheck$DenyNotCurrentThread",
                "DenyReflectionGetStackWalkerWithClassReferenceCheck",
                "DenyReflectionGetStackWalkerWithClassReferenceCheck$DenyDependingOnFirstArg",
                "DenyReflectionGetClassLoader",
                "DenyReflectionGetClassLoader$DenyOnExit",
                "DenyReflectionGetClassLoader$DenyOnUnprivilegedMethodHandlesLookup",
                "DenyReflectionGetClassLoader$DenyOnMethodTypeFromMethodDescriptorString",
                "DenyReflectionGetClassLoader$DenySpecifiedLoaderIsNullAndCallerLoaderIsNotNull",
                "DenyReflectionGetClassLoader$DenyCallerModuleDifferentFromSpecifiedModule",

                "AbstractPermitCheck",
                "BasicPermitCheck",
                "BasicPermitCheck$FirstArgInsert",
                "BasicPermitCheck$AnyInsert",
                "PermitPropertyCheck",
                "PermitPropertyCheck$TimeZoneSetDefaultInsert",
                "PermitPropertyCheck$LocaleSetDefaultInsert",
                "PermitEnvCheck",
                "PermitFileCheck",
                "PermitFileCheck$TargetInsert",
                "PermitFileCheck$FirstArgInsert",
                "PermitFileCheck$TargetMkdirs",
                "PermitFileCheck$FirstArgMkdirs",
                "PermitFileCheck$TwoArgsReadWriteInsert",
                "PermitFileCheck$TwoArgsReadInsert",
                "PermitFileCheck$TwoArgsWriteInsert",
                "PermitFileCheck$RenameToInsert",
                "PermitFileCheck$ZipFileConstructorInsert",
                "PermitFileCheck$RandomAccessFileConstructorInsert",
                "PermitFileCheck$ProcessBuilderStartInsert",
                "PermitFileCheck$FileCreateTempFileInsert",

                "VerboseCategory"
            };

// System.out.println("Before injecting of internal permcheck classes into boottsrap classloader ...");
// Scanner console = new Scanner(System.in);
// console.nextLine();
        for (String className : classNames) {
// System.out.println("className: " + className);
            Map<TypeDescription, byte[]> types = new ByteBuddy()
                    .redefine(
                            typePool.describe(internalPkgPrefix+className).resolve(),
                            ClassFileLocator.ForClassLoader.ofSystemLoader())
                    .make()
                    .getAllTypes();
    
            Map<TypeDescription, Class<?>> classes = injector.inject(types);
// for (TypeDescription tdescr : classes.keySet()) {             
//     System.out.println("  Classloader of " + tdescr.getTypeName() + ": " + classes.get(tdescr).getClassLoader());
// }
        }

// System.out.println("After injecting of internal permcheck classes into boottsrap classloader ...");
// console.nextLine();

// try {
//     System.out.println("Specs Klasse wurde geladen von:");
//     System.out.println(Class.forName("de.hsh.permcheck.internal.Specs", true, null).getClassLoader());
// } catch (ClassNotFoundException e) {
//     // TODO Auto-generated catch block
//     e.printStackTrace();
// }
//console.nextLine();

        // This doesn't work on Windows, since the jar files, that were created and loaded above, cannot
        // be deleted from the running JVM.
        // for (File f : temp.listFiles()) {
        //     f.deleteOnExit();
        // }
        // temp.deleteOnExit();

        Specs.setup(policy, password);

        HashMap<Class<?>, List<Spec>> map = new HashMap<>();
        for (Executable e : Specs.getExecutables()) {
            Class<?> c = e.getDeclaringClass();
            if (!map.containsKey(c)) map.put(c, new ArrayList<>());
            for (Insert insert : Specs.getInserts(e)) {
                map.get(c).add(new Spec(e, insert));
            }
        }

        List<String> untustedClassRegexes = Specs.getUntrustedClassRegexes();

        AgentBuilder agentBuilderRedefine = new AgentBuilder.Default()
            .disableClassFormatChanges();

        if (Specs.verboseInstall()) {
            agentBuilderRedefine = agentBuilderRedefine
                .with(AgentBuilder.InstallationListener.StreamWriting.toSystemError());
        }
        if (Specs.verboseTransform()) {
            agentBuilderRedefine = agentBuilderRedefine
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly());
        }

        agentBuilderRedefine = agentBuilderRedefine
            .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
            .with(AgentBuilder.RedefinitionStrategy.REDEFINITION)
            .with(new AgentBuilder.Listener.WithTransformationsOnly(new ClassFileWriter()))
            .ignore(ElementMatchers.none());

        // MethodDescription myUntrustedClassConstructorInterceptorMd =
        //     myUntrustedClassConstructorInterceptorTd.getDeclaredMethods()
        //             .filter(ElementMatchers.named("intercept"))
        //             .getOnly();

        // First transform the untrusted classes:
        for (String ucRegex : untustedClassRegexes) {
            Narrowable narrowable = agentBuilderRedefine.type( 
                    ElementMatchers.nameMatches(ucRegex)
            );

            Transformer transformer = (builder, typeDescription, classLoader, module, protectionDomain) -> {
System.out.println("  transform(..., typeDescription="+typeDescription);
                for (MethodDescription md : typeDescription.getDeclaredMethods()) {
System.out.println("      md="+md);
                    // Constructors need a different approach than normal methods, because constructors 
                    // cannot start with a try { ..., they must start with a super() or this().
                    // For constructors we manually write byte code with a AsmVisitorWrapper:
                    if (md.isConstructor()) {
                        builder = builder.visit(new AsmVisitorWrapper.ForDeclaredMethods()
                                                    .writerFlags(net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES) 
                                                    .constructor(ElementMatchers.is(md), new ConstructorVisitorWrapper()));
                    } else {
                        builder = builder.visit(Advice.to(myUntrustedClassAdvicesTd).on(ElementMatchers.is(md)));
                    }
                }
                return builder;
            };
            
            agentBuilderRedefine = narrowable.transform(transformer).asTerminalTransformation();
        }

        // Now transform standard library classes:
        
        for (Class<?> c : map.keySet()) {
            //System.out.println("c.getName(): " + c.getName());
            Narrowable narrowable = agentBuilderRedefine.type( 
                    ElementMatchers.named(c.getName())
            );

            Transformer transformer = (builder, typeDescription, classLoader, module, protectionDomain) -> {
                for (Spec s : map.get(c)) {
                    Executable e = s.executable();
                    MethodDescription.InDefinedShape md;
                    if (e == null) {
                        throw new Error("Unexpected Executable null");
                    } else if (e instanceof Constructor<?>) {
                        md = new MethodDescription.ForLoadedConstructor((Constructor<?>) e);
                    } else if (e instanceof Method) {
                        md = new MethodDescription.ForLoadedMethod((Method) e);
                    } else {
                        throw new Error("Unexpected Executable of type " + e.getClass());
                    }

                    builder = builder.visit(Advice.to(myAdvicesTd).on(ElementMatchers.is(md)));
                }
                return builder;
            };
            
            Extendable extendable = narrowable.transform(transformer);
            agentBuilderRedefine = extendable.asTerminalTransformation();
        }
        
        agentBuilderRedefine.installOn(instrumentation);

        // try {
        //     instrumentation.retransformClasses(Class.forName("main.TestMain$1TestCaseSystemGetenvDenied"));
        // } catch (ClassNotFoundException e1) {
        //     throw new Error(e1);
        // } catch (UnmodifiableClassException e1) {
        //     throw new Error(e1);
        // }
        byteBuddyIsConfigured = true;
    }

    public static void pause(String password) {
        Specs.pause(password);
    }

    public static void resume(String password) {
        Specs.resume(password);
    }

    public static Boolean isActive(String password) {
        return Specs.isActive(password);
    }
    
    public static boolean isInitialized() {
        return byteBuddyIsConfigured;
    }


    private static class ClassFileWriter implements AgentBuilder.Listener {
        @Override
        public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, 
                                        JavaModule module, boolean loaded, DynamicType dynamicType) {
            
            String[] cn = { "subm.Submission", "java.io.File", "main.TestMain$1TestCaseSystemGetenvDenied"};
            for (String c : cn) {
                if (typeDescription.getName().equals(c)) {
                    // Hier liegt der transformierte Bytecode vor!
                    byte[] bytecode = dynamicType.getBytes();
                    
                    // Bytecode mit ASM als Text ausgeben
                    System.out.println("Transformierte Klasse: " + typeDescription.getName());
                    try {
                        Files.write(Path.of("D:\\ws\\github_hsh-elc_permcheck\\"+c+".class"), bytecode);
                    } catch (IOException e) {
                        throw new Error(e);
                    }
                }
            }
        }
        @Override public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}
        @Override public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded) {}
        @Override public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {}
        @Override public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}
    }
    
}
