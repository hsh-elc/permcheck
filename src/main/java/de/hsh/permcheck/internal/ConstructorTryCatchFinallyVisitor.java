package de.hsh.permcheck.internal;

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
    
    private boolean superCalled = false;


    public ConstructorTryCatchFinallyVisitor(MethodVisitor methodVisitor) {
        super(Opcodes.ASM9, methodVisitor);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        // Reiche den Aufruf zuerst an die originale Implementierung weiter
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

        // Erkennen, wann der Super-Konstruktor aufgerufen wurde
        if (!superCalled && opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")) {
            superCalled = true;

            // A. Zähler inkrementieren (Direkt nach dem Super-Aufruf)
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "de/hsh/permcheck/internal/MyUntrustedClassAdvices", "enterConstructor", "()V", false);

            // B. Definition der Try-Catch-Grenzen im Bytecode registrieren
            super.visitTryCatchBlock(tryStart, tryEnd, catchStart, null); // null bedeutet "catch Throwable"

            // C. Start-Label für den Try-Block setzen
            super.visitLabel(tryStart);
        }
    }

    @Override
    public void visitInsn(int opcode) {
        // Jedes RETURN im originalen Code abfangen (Normales Verlassen des Konstruktors)
        if (opcode == Opcodes.RETURN && superCalled) {
            // Dekrementieren vor dem regulären Verlassen
            decrementCounter();
        }
        super.visitInsn(opcode);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        // D. Am Ende der Methode den Catch-Block (Finally-Logik für Exceptions) anhängen
        if (superCalled) {
            // Das Ende des Try-Blocks markieren
            super.visitLabel(tryEnd);

            // Hier startet der Catch-Block, falls eine Exception fliegt
            super.visitLabel(catchStart);
            
            // Exception liegt aktuell oben auf dem Stack. Wir sichern sie in einer lokalen Variable (Index 1)
            super.visitVarInsn(Opcodes.ASTORE, 1);
            // Dekrementieren im Exception-Fall
            decrementCounter();
            // Exception wieder laden und werfen (rethrow), um das originale Verhalten beizubehalten
            super.visitVarInsn(Opcodes.ALOAD, 1);
            super.visitInsn(Opcodes.ATHROW);
        }
        // Byte Buddy / ASM die Stack-Größen neu berechnen lassen (+4 Sicherheits-Puffer für unseren Code)
        super.visitMaxs(maxStack + 2, maxLocals + 2);
    }

    private void decrementCounter() {
        super.visitMethodInsn(Opcodes.INVOKESTATIC, "de/hsh/permcheck/internal/MyUntrustedClassAdvices", "exitConstructor", "()V", false);
    }
}
