package main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;

class Util {

    private static HashMap<String,String> source;
    private static HashMap<String,byte[]> byteCode;
    private static ClassLoader memoryClassloader;

    static {
        source = new HashMap<>();
        source.put("HelloWorld", 
                    """
                    public class HelloWorld { 
                    }
                    """);
        source.put("GetSystemClassLoader", 
                    """
                    public class GetSystemClassLoader { 
                        public static void execute() { 
                            ClassLoader.getSystemClassLoader();
                        } 
                    }
                    """);

        byteCode = new HashMap<>();
        
        JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager stdFileManager = compiler.getStandardFileManager(diagnostics, null, null);

        for (String className : source.keySet()) {
            String code = source.get(className);
            // Quellcode-Objekt erstellen
            JavaFileObject sourceFile = new SimpleJavaFileObject(URI.create("string:///" + className + ".java"),
                    JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return code;
                }
            };

            // Custom FileManager, der Bytecode in Maps schreibt statt auf Disk
            ForwardingJavaFileManager<JavaFileManager> fileManager = new ForwardingJavaFileManager<>(stdFileManager) {
                @Override
                public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                        FileObject sibling) {
                    return new SimpleJavaFileObject(URI.create("mem://" + className + kind.extension), kind) {
                        @Override
                        public OutputStream openOutputStream() {
                            return new ByteArrayOutputStream() {
                                @Override
                                public void close() throws IOException {
                                    super.close();
                                    byteCode.put(className, toByteArray());
                                }
                            };
                        }
                    };
                }
            };

            // Kompilieren
            @SuppressWarnings("unused")
            Boolean success = compiler.getTask(null, fileManager, diagnostics, null, null, List.of(sourceFile)).call();
        }

        // Custom ClassLoader, der die Bytes aus unserer Map liest
        memoryClassloader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = byteCode.get(name);
                if (b == null)
                    return super.findClass(name);
                return defineClass(name, b, 0, b.length);
            }
        };
    }

    public static ClassLoader getMemoryClassLoader() {
        return memoryClassloader;
    }

    public static void deleteRecursively(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void touch(File file) throws IOException{
        if (!file.exists()) {
            new FileOutputStream(file).close();
        }
        file.setLastModified(System.currentTimeMillis());
    }

    public static boolean isWindows() {
		String filesep= System.getProperty("file.separator");
		if (filesep != null) return filesep.equals("\\");
		String osname= System.getProperty("os.name");
		if (osname != null) {
			return osname.toLowerCase().startsWith("windows");
		}
		System.out.println(false);
		return false;
	}

    /**
     * Trusted call of Files.copy
     */
    public static void fileCopy(Path src, Path dest) throws IOException {
        Files.copy(src, dest);
    }

}
