package de.hsh.permcheck.internal;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.pool.TypePool;

/**
 * This class is loaded into the app class loader.
 */
public class ConstructorVisitorWrapper implements AsmVisitorWrapper.ForDeclaredMethods.MethodVisitorWrapper {
    private String classDelegate;
    public ConstructorVisitorWrapper(String classDelegate) {
        this.classDelegate = classDelegate;
    }
    @Override
    public MethodVisitor wrap(TypeDescription instrumentedType, MethodDescription instrumentedMethod,
                                MethodVisitor methodVisitor, Implementation.Context implementationContext,
                                TypePool typePool, int writerFlags, int readerFlags) {
        String owner = instrumentedType.getName();
        String ownerSuperClass = instrumentedType.getSuperClass().getTypeName();
        return new ConstructorTryCatchFinallyVisitor(methodVisitor, classDelegate, owner, ownerSuperClass);
    }
}