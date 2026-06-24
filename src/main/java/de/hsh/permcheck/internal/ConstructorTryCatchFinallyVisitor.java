package de.hsh.permcheck.internal;

import java.util.Objects;

import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

/**
 * This class is loaded into the app class loader.
 */
public class ConstructorTryCatchFinallyVisitor extends MethodVisitor {
    private final Label tryStart = new Label();
    private final Label tryEnd = new Label();
    private final Label catchStart = new Label();
    private final Label exitLabel = new Label(); // Gemeinsamer Exit-Punkt
    
    private boolean superCalled = false;

    private String classDelegate;
    private String owner; // classname, e. g. java/io/FileInputStream
    private String ownerSuperClass; // classname, e. g. java/io/InputStream

    public ConstructorTryCatchFinallyVisitor(MethodVisitor methodVisitor, String classDelegate, String owner, String ownerSuperClass) {
        super(Opcodes.ASM9, methodVisitor);
        this.classDelegate = classDelegate;
        this.owner = (owner == null ? null : owner.replace(".", "/"));
        this.ownerSuperClass = (ownerSuperClass == null ? null : ownerSuperClass.replace(".", "/"));
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        // Reiche den Aufruf zuerst an die originale Implementierung weiter
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

        // Erkennen, wann der Super-Konstruktor aufgerufen wurde
        if (!superCalled && opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")) {

            // The following prevents inserting Code at the wrong point after the new String(...), 
            // if the constructor has something like this:
            //    public Submission(short offset) {
            //        this(Integer.parseInt(new String(""+offset)));
            //        System.out.println("Submission short param constructor");
            //    }
            // Sadly, this also means, that we cannot include the full call of 
            // this(Integer.parseInt(new String(""+offset)));
            // in our try block.
            // So this works only, if we instrument all constructors of the class.
System.out.println("in visitMethodInsn("+opcode+", "+owner+", "+name+", "+descriptor+", "+isInterface+")");            
System.out.println("    this.owner="+this.owner);            
System.out.println("    this.ownerSuperClass="+this.ownerSuperClass);            
            if (Objects.equals(owner, this.owner) || Objects.equals(owner, this.ownerSuperClass)) {
                superCalled = true;

                // A. Zähler inkrementieren (Direkt nach dem Super-Aufruf)
                super.visitMethodInsn(Opcodes.INVOKESTATIC, classDelegate, "enterConstructor", "()V", false);

                // B. Definition der Try-Catch-Grenzen im Bytecode registrieren
                super.visitTryCatchBlock(tryStart, tryEnd, catchStart, null); // null bedeutet "catch Throwable"

                // C. Start-Label für den Try-Block setzen
                super.visitLabel(tryStart);
            }
        }
    }

    @Override
    public void visitInsn(int opcode) {
        if ((opcode == Opcodes.RETURN || opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) && superCalled) {
            // Statt RETURN: Sprung zum Exit-Label
            super.visitJumpInsn(Opcodes.GOTO, exitLabel);
        } else {
            super.visitInsn(opcode);
        }
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        // D. Am Ende der Methode den Catch-Block (Finally-Logik für Exceptions) anhängen
        if (superCalled) {
            // Das Ende des Try-Blocks markieren
            super.visitLabel(tryEnd);

            // --- Normaler Exit Pfad ---
            super.visitLabel(exitLabel);
            decrementCounter();
            super.visitInsn(Opcodes.RETURN);

            // --- Exception Pfad ---
            super.visitLabel(catchStart);
            super.visitVarInsn(Opcodes.ASTORE, 1);
            decrementCounter();
            super.visitVarInsn(Opcodes.ALOAD, 1);
            super.visitInsn(Opcodes.ATHROW);            
        }
        // Byte Buddy / ASM die Stack-Größen neu berechnen lassen (+2 Sicherheits-Puffer für unseren Code)
        super.visitMaxs(maxStack + 2, maxLocals + 2);
    }

    private void decrementCounter() {
        super.visitMethodInsn(Opcodes.INVOKESTATIC, classDelegate, "exitConstructor", "()V", false);
    }
}
