module de.hsh.permcheck {
    requires net.bytebuddy;
    requires net.bytebuddy.agent;
    requires java.desktop; // needed to register inserts for java.awt.Desktop methods
    requires java.instrument;

    // for tests only:
    requires java.compiler; // in src/test/java/main/Util.java to create a classloader that reads bytecode from memory
    requires jdk.management.agent;
	
    exports de.hsh.permcheck;
}
