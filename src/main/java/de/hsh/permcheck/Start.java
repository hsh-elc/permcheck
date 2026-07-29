package de.hsh.permcheck;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import de.hsh.permcheck.internal.Insert;
import de.hsh.permcheck.internal.Spec;
import de.hsh.permcheck.internal.Specs;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

public class Start {

    /**
     * 
     * @param policy specification of denials and permissions, separated by line breaks 
     * @param tempFolderForBootstrapInjection folder, where temporary class files may be saved. If the folder does not exist,
     *      it will be created automatically. If tempPath is null, the default temp folder as specified
     *      by the system property java.io.tmpdir is used.
     * @throws IllegalArgumentException if the tempPath parameter specifies an existing regular file.
     * @throws IOException if the tempPath folder cannot be created
     * @throws Error if the byte buddy agent cannot be found or installed.
     */
    public static void configureByteBuddyAgentIfAny(String policy, String tempFolderForBootstrapInjection) throws IllegalArgumentException, IOException, Error {
        Instrumentation instrumentation = null;
        try {
            instrumentation = ByteBuddyAgent.getInstrumentation();
        } catch (IllegalStateException ex) {
            try {
                instrumentation = ByteBuddyAgent.install();
            } catch (IllegalStateException ex2) {
                throw new Error("Cannot find installed byte buddy agent.", ex2);
            }
        }

        String internalPkgPrefix = Start.class.getPackageName() + ".internal.";

        // Quelle: https://stackoverflow.com/questions/69267044/insert-a-custom-class-into-bootstrap-classloader-into-java-base-module
        TypePool typePool = TypePool.Default.ofSystemLoader();
        TypeDescription myAdvicesTd = typePool.describe(internalPkgPrefix+"MyAdvices").resolve();

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

        if (Stream.of(policy.split("\\R")).map(String::trim).filter(s -> s.equals("verbose.install")).count() > 0) {
            System.out.println("[PERMCHECK] Using temp folder for class injection to bootstrap classloader: " + temp);
        }
        ClassInjector injector = ClassInjector.UsingInstrumentation.of(temp, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, instrumentation);
        //ClassInjector injector = ClassInjector.UsingUnsafe.ofBootLoader();

        String[] classNames = {
                "MyAdvices", 
                "Hook", 
                "Helper", 
                "Logger", 
                "Spec",
                "Specs",
                "Action",
                "Insert",
                "ActionInsert",
                "DenyAllInsert",

                "AbstractCheck",

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
                "DenyReflectionCreateClassLoader",
                "DenyReflectionAccessClassInNonExportedBootLayerPackage",
                "DenyReflectionAccessClassInNonExportedBootLayerPackage$DenyFirstArgOnNonExportedBootLayerPackageInsert",
                //"DenyReflectionAccessClassInNonExportedBootLayerPackage$DenyResultClassArrayOnNonBootToBootClassLoaderAndNonExportedBootLayerPackageInsert",

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

        for (String className : classNames) {
            Map<TypeDescription, byte[]> types = new ByteBuddy()
                    .redefine(
                            typePool.describe(internalPkgPrefix+className).resolve(),
                            ClassFileLocator.ForClassLoader.ofSystemLoader())
                    .make()
                    .getAllTypes();
    
            injector.inject(types);
        }

        // This doesn't work on Windows, since the jar files, that were created and loaded above, cannot
        // be deleted from the running JVM.
        for (File f : temp.listFiles()) {
            f.deleteOnExit();
        }
        temp.deleteOnExit();

        Specs.setup(policy);

        HashMap<Class<?>, List<Spec>> map = new HashMap<>();
        for (Executable e : Specs.getExecutables()) {
            Class<?> c = e.getDeclaringClass();
            if (!map.containsKey(c)) map.put(c, new ArrayList<>());
            Insert insert = Specs.getInsert(e);
            map.get(c).add(new Spec(e, insert));
        }

        AgentBuilder agentBuilder = new AgentBuilder.Default()
            .disableClassFormatChanges();
        
        if (Specs.verboseInstall()) {
            agentBuilder = agentBuilder
                .with(AgentBuilder.InstallationListener.StreamWriting.toSystemError());
        }
        if (Specs.verboseTransform()) {
            agentBuilder = agentBuilder
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly());
        }
        agentBuilder = agentBuilder
            .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
            .with(AgentBuilder.RedefinitionStrategy.REDEFINITION)
            .ignore(ElementMatchers.none());
        
        for (Class<?> c : map.keySet()) {
            //System.out.println("c.getName(): " + c.getName());
            Narrowable narrowable = agentBuilder.type( 
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
            
            agentBuilder = narrowable.transform(transformer).asTerminalTransformation();
        }
        agentBuilder.installOn(instrumentation);
    }
}
