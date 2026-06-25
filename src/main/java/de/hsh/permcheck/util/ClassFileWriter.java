package de.hsh.permcheck.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import de.hsh.permcheck.internal.Specs;
import de.hsh.permcheck.internal.VerboseCategory;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

public class ClassFileWriter implements AgentBuilder.Listener {
    private Map<String,String> mapClassNameToDirectory;
    public ClassFileWriter(Map<String,String> mapClassNameToDirectory) {
        this.mapClassNameToDirectory = mapClassNameToDirectory;
    }
    @Override
    public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, 
                                 JavaModule module, boolean loaded, DynamicType dynamicType) {
        
        if (mapClassNameToDirectory.containsKey(typeDescription.getName())) {
            String directory = mapClassNameToDirectory.get(typeDescription.getName());

            byte[] bytecode = dynamicType.getBytes();
            
            // Bytecode mit ASM als Text ausgeben
            File dirAsFile = new File(directory);
            Path dest = dirAsFile.toPath().toAbsolutePath().resolve(typeDescription.getName() + ".class");
            Specs.log(VerboseCategory.TRANSFORM, "[PERMCHECK] writing transformed class "+typeDescription.getName()+" to "+dest);
            try {
                dirAsFile.mkdirs();
                Files.write(dest, bytecode);
            } catch (IOException e) {
                Specs.log(VerboseCategory.INSTALL, "[PERMCHECK] Error when writing transformed class: "+e.getMessage());
                throw new Error("Error when writing transformed class "+typeDescription.getName()+" to "+dest, e);
            }
        }
    }
    @Override public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}
    @Override public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded) {}
    @Override public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {}
    @Override public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}
}