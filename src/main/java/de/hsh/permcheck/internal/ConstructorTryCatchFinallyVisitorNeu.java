package de.hsh.permcheck.internal;

// import net.bytebuddy.jar.asm.Label;
// import net.bytebuddy.jar.asm.MethodVisitor;
// import net.bytebuddy.jar.asm.Opcodes;

// public class ConstructorTryCatchFinallyAdapter extends MethodVisitor   neu: extends LocalVariablesSorter {
//     private final Label tryStart = new Label();
//     private final Label tryEnd = new Label();
//     private final Label catchStart = new Label();
    
//     private boolean superCalled = false;
//     neu private int booleanLocalVariableIndex = -1; // Hier speichern wir den dynamisch generierten Slot
//     s. https://share.google/aimode/bTBuqO7Xg0XGoUYWg


//     public ConstructorTryCatchFinallyAdapter(MethodVisitor methodVisitor) {
//         super(Opcodes.ASM9, methodVisitor);
//     }

//     neu:
//     public ConstructorTryCatchFinallyAdapter(int access, String descriptor, MethodVisitor methodVisitor) {
//         // Super-Aufruf initialisiert den LocalVariablesSorter (benötigt ASM9)
//         super(Opcodes.ASM9, access, descriptor, methodVisitor);
//     }


//     @Override
//     public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
//         // Reiche den Aufruf zuerst an die originale Implementierung weiter
//         super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

//         // Erkennen, wann der Super-Konstruktor aufgerufen wurde
//         if (!superCalled && opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")) {
//             superCalled = true;

//             // 1. Neuen Slot für eine int/boolean Variable reservieren
//             neu this.booleanLocalVariableIndex = newLocal(net.bytebuddy.jar.asm.Type.BOOLEAN_TYPE);



//             // A. Zähler inkrementieren (Direkt nach dem Super-Aufruf)
//             super.visitMethodInsn(Opcodes.INVOKESTATIC, "de/hsh/permcheck/internal/MyUntrustedClassConstructorInterceptor", "enter", "()V", false);

//             neu:
//             super.visitMethodInsn(Opcodes.INVOKESTATIC, "util/Util", "enter", "()Z", false);
//             // 3. Den boolean-Wert vom Stack in unsere neue lokale Variable speichern
//             super.visitVarInsn(Opcodes.ISTORE, booleanLocalVariableIndex);



//             // B. Definition der Try-Catch-Grenzen im Bytecode registrieren
//             super.visitTryCatchBlock(tryStart, tryEnd, catchStart, null); // null bedeutet "catch Throwable"

//             // C. Start-Label für den Try-Block setzen
//             super.visitLabel(tryStart);
//         }
//     }

//     @Override
//     public void visitInsn(int opcode) {
//         // Jedes RETURN im originalen Code abfangen (Normales Verlassen des Konstruktors)
//         if (opcode == Opcodes.RETURN && superCalled) {
//             // Dekrementieren vor dem regulären Verlassen
//             decrementCounter();
//         }
//         super.visitInsn(opcode);
//     }

//     @Override
//     public void visitMaxs(int maxStack, int maxLocals) {
//         // D. Am Ende der Methode den Catch-Block (Finally-Logik für Exceptions) anhängen
//         if (superCalled) {
//             // Das Ende des Try-Blocks markieren
//             super.visitLabel(tryEnd);

//             // Hier startet der Catch-Block, falls eine Exception fliegt
//             super.visitLabel(catchStart);
            
//             // Exception liegt aktuell oben auf dem Stack. Wir sichern sie in einer lokalen Variable (Index 1)
//             super.visitVarInsn(Opcodes.ASTORE, 1);
//             // Dekrementieren im Exception-Fall
//             decrementCounter();
//             // Exception wieder laden und werfen (rethrow), um das originale Verhalten beizubehalten
//             super.visitVarInsn(Opcodes.ALOAD, 1);
//             super.visitInsn(Opcodes.ATHROW);

//             Neu für die beiden vorherigen Zeilen:
//             // Exception liegt aktuell oben auf dem Stack. Wir sichern sie temporär auf dem Stack 
//             // oder in einer lokalen Variable, um Platz für unsere exit-Logik zu machen.
//             // LocalVariablesSorter findet auch hierfür sicher einen freien Slot.
//             int exceptionSlot = newLocal(net.bytebuddy.jar.asm.Type.getType(Throwable.class));
//             super.visitVarInsn(Opcodes.ASTORE, exceptionSlot);
//             // util.Util.exit(boolean) aufrufen im Exception-Fall
//             decrementCounter();
//             // Exception wieder laden und werfen (rethrow), um das originale Verhalten beizubehalten
//             super.visitVarInsn(Opcodes.ALOAD, exceptionSlot);
//             super.visitInsn(Opcodes.ATHROW);

//         }
//         // Byte Buddy / ASM die Stack-Größen neu berechnen lassen (+4 Sicherheits-Puffer für unseren Code)
//         super.visitMaxs(maxStack + 2, maxLocals + 2);
//     }

//     private void decrementCounter() {
//         super.visitMethodInsn(Opcodes.INVOKESTATIC, "de/hsh/permcheck/internal/MyUntrustedClassConstructorInterceptor", "exit", "()V", false);

//         neu:
//         // Gelagerten boolean-Wert (0 oder 1 im Bytecode) aus der lokalen Variable auf den Stack laden
//         super.visitVarInsn(Opcodes.ILOAD, booleanLocalVariableIndex);
        
//         // util.Util.exit(boolean) aufrufen. Deskriptor "(Z)V" bedeutet: Nimmt boolean (Z), gibt void (V) zurück
//         super.visitMethodInsn(Opcodes.INVOKESTATIC, "util/Util", "exit", "(Z)V", false);
//     }
// }
