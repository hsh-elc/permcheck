package de.hsh.permcheck.internal;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.pool.TypePool;

/**
 * This class is loaded into the app class loader.
 */
public class ConstructorVisitorWrapperNeu implements AsmVisitorWrapper.ForDeclaredMethods.MethodVisitorWrapper {
    private String classDelegate;
    private boolean passArgumentsToDelegate;

    public ConstructorVisitorWrapperNeu(String classDelegate, boolean passArgumentsToDelegate) {
        this.classDelegate = classDelegate;
        this.passArgumentsToDelegate = passArgumentsToDelegate;
    }
    @Override
    public MethodVisitor wrap(TypeDescription instrumentedType, MethodDescription instrumentedMethod,
                                MethodVisitor methodVisitor, Implementation.Context implementationContext,
                                TypePool typePool, int writerFlags, int readerFlags) {
        String descriptor = null;
        if (passArgumentsToDelegate) descriptor = instrumentedMethod.getDescriptor();
        String owner = instrumentedType.getName();
        String ownerSuperClass = instrumentedType.getSuperClass().getTypeName();

        // // Berechne die Anzahl der lokalen Variablen, die der Konstruktor bereits belegt
        // // Jedes long/double belegt 2, alles andere 1. 'this' belegt 1.
        // int localCount = 0;
        // if (!instrumentedMethod.isStatic()) {
        //     localCount += 1; // 'this'
        // }
        // for (ParameterDescription param : instrumentedMethod.getParameters()) {
        //     localCount += param.getType().asErasure().getStackSize().getSize();
        // }
        return new ConstructorTryCatchFinallyVisitorNeu(methodVisitor, classDelegate, owner, ownerSuperClass, descriptor);
    }
}