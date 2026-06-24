package de.hsh.permcheck.internal;

import java.util.Objects;

import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

/**
 * This class is loaded into the app class loader.
 */
public class ConstructorTryCatchFinallyVisitorNeu extends MethodVisitor {
    private final Label tryStart = new Label();
    private final Label tryEnd = new Label();
    private final Label catchStart = new Label();
    private final Label exitLabel = new Label(); // Gemeinsamer Exit-Punkt
    
    private boolean superCalled = false;

    private String classDelegate;
    private String owner; // classname, e. g. java/io/FileInputStream
    private String ownerSuperClass; // classname, e. g. java/io/InputStream
    private String methodDescriptor; // parameters

    /**
     * 
     * @param methodVisitor
     * @param classDelegate
     * @param owner
     * @param ownerSuperClass
     * @param methodDescriptor if this is null, then the classDelegate's enterConstructor() and exitConstructor() methods are
     *     invoked without passing parameters.
     */
    public ConstructorTryCatchFinallyVisitorNeu(MethodVisitor methodVisitor, String classDelegate, String owner, String ownerSuperClass, String methodDescriptor) {
        super(Opcodes.ASM9, methodVisitor);
        this.classDelegate = classDelegate;
        this.owner = (owner == null ? null : owner.replace(".", "/"));
        this.ownerSuperClass = (ownerSuperClass == null ? null : ownerSuperClass.replace(".", "/"));
        this.methodDescriptor = methodDescriptor;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        // Reiche den Aufruf zuerst an die originale Implementierung weiter
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

        // Erkennen, wann der Super-Konstruktor aufgerufen wurde
        if (!superCalled && opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")) {

            // The following prevents inserting Code at the wrong point after the new File(...), 
            // if the constructor has something like this:
            //    public RandomAccessFile(String name, String mode) {
            //        this(name != null ? new File(name) : null, mode);
            //    } 
            // Sadly, this also means, that we cannot include the full call of 
            // this(name != null ? new File(name) : null, mode);
            // in our try block.
System.out.println("in visitMethodInsn("+opcode+", "+owner+", "+name+", "+descriptor+", "+isInterface+")");            
System.out.println("    this.owner="+this.owner);            
System.out.println("    this.ownerSuperClass="+this.ownerSuperClass);            
            if (Objects.equals(owner, this.owner) || Objects.equals(owner, this.ownerSuperClass)) {
                superCalled = true;

                // Aufruf enterConstructor
                invokeEnter();

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
System.out.println("in visitMaxs("+maxStack+", "+maxLocals+")");            
System.out.println("    this.owner="+this.owner);            
            // Das Ende des Try-Blocks markieren
            super.visitLabel(tryEnd);

            // --- Normaler Exit Pfad ---
            super.visitLabel(exitLabel);
            invokeExit();
            super.visitInsn(Opcodes.RETURN);

            // --- Exception Pfad ---
            super.visitLabel(catchStart);
            super.visitVarInsn(Opcodes.ASTORE, maxLocals + 1); // Exception zwischenspeichern
            invokeExit();
            super.visitVarInsn(Opcodes.ALOAD, maxLocals + 1);
            super.visitInsn(Opcodes.ATHROW);            
        }
        // Byte Buddy / ASM die Stack-Größen neu berechnen lassen (+2 Sicherheits-Puffer für unseren Code)
        super.visitMaxs(maxStack + 2, maxLocals + 2);
    }

    private void invokeEnter() {
        if (methodDescriptor == null) {
            super.visitMethodInsn(Opcodes.INVOKESTATIC, classDelegate, "enterConstructor", 
                            "()V", false);
        } else {
            super.visitLdcInsn(this.owner.replace("/", "."));
            pushParametersToArray(); 
            pushParameterTypesToArray();
            super.visitMethodInsn(Opcodes.INVOKESTATIC, classDelegate, "enterConstructor", 
                            "(Ljava/lang/String;[Ljava/lang/Object;[Ljava/lang/String;)V", false);
        }
    }

    private void invokeExit() {
        if (methodDescriptor == null) {
            super.visitMethodInsn(Opcodes.INVOKESTATIC, classDelegate, "exitConstructor", 
                            "()V", false);
        } else {
            super.visitLdcInsn(this.owner.replace("/", "."));
            pushParametersToArray(); 
            pushParameterTypesToArray();
            super.visitMethodInsn(Opcodes.INVOKESTATIC, classDelegate, "exitConstructor", 
                            "(Ljava/lang/String;[Ljava/lang/Object;[Ljava/lang/String;)V", false);
        }
    }

    private void pushParametersToArray() {
        // Analysieren des Descriptors, um die Anzahl der Parameter zu bestimmen
        Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor);
        int paramCount = argumentTypes.length;

        // 1. Array erzeugen
        super.visitIntInsn(Opcodes.BIPUSH, paramCount);
        super.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

        // 2. Parameter in das Array laden
        int localIndex = 1; // 0 ist 'this'
        for (int i = 0; i < paramCount; i++) {
            super.visitInsn(Opcodes.DUP); // Array-Referenz kopieren
            super.visitIntInsn(Opcodes.BIPUSH, i); // Index
            
            // Parameter laden (Boxen falls Primitiv)
            loadAndBox(localIndex, argumentTypes[i]);
            
            super.visitInsn(Opcodes.AASTORE); // Speichern in Array
            localIndex += argumentTypes[i].getSize();
        }
    }

    private void loadAndBox(int localIndex, Type type) {
        // 1. Wert auf den Stack laden
        super.visitVarInsn(type.getOpcode(Opcodes.ILOAD), localIndex);

        // 2. Falls primitiv, boxing durchführen
        switch (type.getSort()) {
            case Type.BOOLEAN:
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                break;
            case Type.CHAR:
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                break;
            case Type.BYTE:
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                break;
            case Type.SHORT:
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                break;
            case Type.INT:
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                break;
            case Type.FLOAT:
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                break;
            case Type.LONG:
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                break;
            case Type.DOUBLE:
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                break;
            case Type.OBJECT:
            case Type.ARRAY:
                // Keine Aktion nötig, ist bereits ein Objekt
                break;
            default:
                throw new IllegalArgumentException("Unbekannter Typ: " + type);
        }
    }

    private void pushParameterTypesToArray() {
        Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor);
        
        // 1. Array für die Typ-Namen erstellen
        super.visitIntInsn(Opcodes.BIPUSH, argumentTypes.length);
        super.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");

        // 2. Namen der Typen einfügen
        for (int i = 0; i < argumentTypes.length; i++) {
            super.visitInsn(Opcodes.DUP);
            super.visitIntInsn(Opcodes.BIPUSH, i);
            super.visitLdcInsn(argumentTypes[i].getClassName()); // z.B. "int", "java.lang.String"
            super.visitInsn(Opcodes.AASTORE);
        }
    }
}
