package de.hsh.permcheck.internal;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Executable;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipFile;
import java.awt.Desktop;

public class PermitFileCheck extends AbstractPermitCheck {


    
    private static final Random random = new Random();

    public PermitFileCheck() {
        super("file", "deny.fileExceptSpecifiedPermissions", "permit.file", PermitFileCheck::getCanonicalPath);
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        // execute:

        registry.put(File.class.getDeclaredMethod("canExecute"), targetExecute());
        registry.put(Files.class.getDeclaredMethod("isExecutable", Path.class), firstArgExecute());

        // read and execute:

        registry.put(ProcessBuilder.class.getDeclaredMethod("start"), processBuilderStart());

        // read:

        registry.put(File.class.getDeclaredMethod("exists"), targetRead());
        registry.put(File.class.getDeclaredMethod("isFile"), targetRead());
        registry.put(File.class.getDeclaredMethod("isDirectory"), targetRead());
        registry.put(File.class.getDeclaredMethod("length"), targetRead());
        registry.put(File.class.getDeclaredMethod("getFreeSpace"), targetRead());
        registry.put(File.class.getDeclaredMethod("getUsableSpace"), targetRead());
        registry.put(File.class.getDeclaredMethod("getTotalSpace"), targetRead());
        registry.put(File.class.getDeclaredMethod("lastModified"), targetRead());
        registry.put(File.class.getDeclaredMethod("isHidden"), targetRead());
        registry.put(File.class.getDeclaredMethod("canRead"), targetRead());
        registry.put(File.class.getDeclaredMethod("getAbsolutePath"), targetRead());
        registry.put(File.class.getDeclaredMethod("getCanonicalPath"), targetRead());
        registry.put(File.class.getDeclaredMethod("getAbsoluteFile"), targetRead());
        registry.put(File.class.getDeclaredMethod("getCanonicalFile"), targetRead());
        registry.put(File.class.getDeclaredMethod("list"), targetRead());
        registry.put(File.class.getDeclaredMethod("listFiles"), targetRead());
        registry.put(File.class.getDeclaredMethod("list", FilenameFilter.class), targetRead());
        registry.put(File.class.getDeclaredMethod("listFiles", FilenameFilter.class), targetRead());
        registry.put(File.class.getDeclaredMethod("listFiles", FileFilter.class), targetRead());

        registry.put(Files.class.getDeclaredMethod("size", Path.class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("isReadable", Path.class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("isHidden", Path.class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("isSymbolicLink", Path.class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("getLastModifiedTime", Path.class, LinkOption[].class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("getOwner", Path.class, LinkOption[].class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("isRegularFile", Path.class, LinkOption[].class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("isDirectory", Path.class, LinkOption[].class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("exists", Path.class, LinkOption[].class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("notExists", Path.class, LinkOption[].class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("newDirectoryStream", Path.class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("newDirectoryStream", Path.class, String.class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("newDirectoryStream", Path.class, DirectoryStream.Filter.class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("getFileStore", Path.class), firstArgRead());
        registry.put(Files.class.getDeclaredMethod("isSameFile", Path.class, Path.class), twoArgsRead());
        registry.put(Files.class.getDeclaredMethod("readAttributes", Path.class, String.class, LinkOption[].class), firstArgRead());

        registry.put(Path.class.getDeclaredMethod("register", WatchService.class, WatchEvent.Kind[].class), targetRead());

        registry.put(FileInputStream.class.getDeclaredConstructor(File.class), firstArgRead());
        registry.put(FileInputStream.class.getDeclaredConstructor(String.class), firstArgRead());

        // read and delete:

        registry.put(ZipFile.class.getDeclaredConstructor(File.class), zipFileConstructor());
        registry.put(ZipFile.class.getDeclaredConstructor(File.class, Charset.class), zipFileConstructor());
        registry.put(ZipFile.class.getDeclaredConstructor(String.class), zipFileConstructor());
        registry.put(ZipFile.class.getDeclaredConstructor(String.class, Charset.class), zipFileConstructor());
        registry.put(ZipFile.class.getDeclaredConstructor(File.class, int.class), zipFileConstructor());
        registry.put(ZipFile.class.getDeclaredConstructor(File.class, int.class, Charset.class), zipFileConstructor());

        registry.put(Desktop.class.getDeclaredMethod("moveToTrash", File.class), firstArgReadDelete());

        // read and write:
        registry.put(File.class.getDeclaredMethod("mkdirs"), targetMkdirs());
        registry.put(Files.class.getDeclaredMethod("createDirectories", Path.class, FileAttribute[].class), firstArgMkdirs());
        registry.put(Files.class.getDeclaredMethod("copy", Path.class, Path.class, CopyOption[].class), twoArgsReadWrite());
        registry.put(RandomAccessFile.class.getDeclaredConstructor(File.class, String.class), randomAccessFileConstructor());
        registry.put(RandomAccessFile.class.getDeclaredConstructor(String.class, String.class), randomAccessFileConstructor());

        // write:

        registry.put(FileOutputStream.class.getDeclaredConstructor(String.class), firstArgWrite());
        registry.put(FileOutputStream.class.getDeclaredConstructor(String.class, boolean.class), firstArgWrite());
        registry.put(FileOutputStream.class.getDeclaredConstructor(File.class), firstArgWrite());
        registry.put(FileOutputStream.class.getDeclaredConstructor(File.class, boolean.class), firstArgWrite());

        registry.put(File.class.getDeclaredMethod("setWritable", boolean.class), targetWrite());
        registry.put(File.class.getDeclaredMethod("setWritable", boolean.class, boolean.class), targetWrite());
        registry.put(File.class.getDeclaredMethod("setExecutable", boolean.class), targetWrite());
        registry.put(File.class.getDeclaredMethod("setExecutable", boolean.class, boolean.class), targetWrite());
        registry.put(File.class.getDeclaredMethod("setReadable", boolean.class), targetWrite());
        registry.put(File.class.getDeclaredMethod("setReadable", boolean.class, boolean.class), targetWrite());
        registry.put(File.class.getDeclaredMethod("setLastModified", long.class), targetWrite());
        registry.put(File.class.getDeclaredMethod("setReadOnly"), targetWrite());
        registry.put(File.class.getDeclaredMethod("canWrite"), targetWrite());
        registry.put(File.class.getDeclaredMethod("mkdir"), targetWrite());
        registry.put(File.class.getDeclaredMethod("createNewFile"), targetWrite());

        registry.put(Files.class.getDeclaredMethod("isWritable", Path.class), firstArgWrite());
        registry.put(Files.class.getDeclaredMethod("setLastModifiedTime", Path.class, FileTime.class), firstArgWrite());
        registry.put(Files.class.getDeclaredMethod("setOwner", Path.class, UserPrincipal.class), firstArgWrite());
        registry.put(Files.class.getDeclaredMethod("createDirectory", Path.class, FileAttribute[].class), firstArgWrite());
        registry.put(Files.class.getDeclaredMethod("createFile", Path.class, FileAttribute[].class), firstArgWrite());
        // omit Files.createTempDirectory and Files.createTempFile, since these methods finally invoke
        // Files.createDirectory and Files.createFile.
        registry.put(Files.class.getDeclaredMethod("setAttribute", Path.class, String.class, Object.class, LinkOption[].class), firstArgWrite());

        registry.put(File.class.getDeclaredMethod("renameTo", File.class), renameTo());
        registry.put(Files.class.getDeclaredMethod("move", Path.class, Path.class, CopyOption[].class), twoArgsWrite());

        registry.put(File.class.getDeclaredMethod("createTempFile", String.class, String.class), fileCreateTempFile());
        registry.put(File.class.getDeclaredMethod("createTempFile", String.class, String.class, File.class), fileCreateTempFile());

        // delete:

        registry.put(File.class.getDeclaredMethod("delete"), targetDelete());
        registry.put(File.class.getDeclaredMethod("deleteOnExit"), targetDelete());
        registry.put(Files.class.getDeclaredMethod("delete", Path.class), firstArgDelete());
        registry.put(Files.class.getDeclaredMethod("deleteIfExists", Path.class), firstArgDelete());
    }
    
    @Override
    protected boolean matches(String pattern, String pathStr) {
        // Falls das Pattern noch kein "glob:" davor hat, fügen wir es hinzu
        String syntaxAndPattern = pattern.startsWith("glob:") ? pattern : "glob:" + pattern;
        
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(syntaxAndPattern);
        Path path = Paths.get(pathStr);
        
        return matcher.matches(path);
    }
    
    private static String getCanonicalPathFromString(String p) {
        if (p == null) return null;

        // Um weiter unten File.getCanonicalPath aufrufen zu können, müssen wir vorher
        // alle Pattern-Zeichen maskieren, die in einem echten Pfad nichts zu suchen haben.

        long n = random.nextLong();
        String id = Long.toUnsignedString(n);
        final String[][] replacements = {
            { "\\*", "AS"+id },
            { "\\?", "QU"+id },
            { "\\{", "CO"+id },
            { "\\}", "CC"+id },
        };

        for (String[] kv : replacements) {
            p = p.replaceAll(kv[0], kv[1]);
        }
        p = getCanonicalPathFromFile(new File(p));

        // Und jetzt demaskieren wir die Zeichen wieder:

        for (String[] kv : replacements) {
            p = p.replaceAll(kv[1], kv[0]);
        }
        return p;
    }

    private static String getCanonicalPathFromFile(File file) {
        String path = null;
        try {
            path = file.getCanonicalPath().replaceAll("\\\\", "/");
        } catch (IOException e) {
            throw new RuntimeException("Cannot get canonical path of '"+file+"'", e);
        }
        return path;
    }

    private static String getCanonicalPathFromPath(Path path) {
        return getCanonicalPath(path.toFile());
    }

    private static String getCanonicalPath(Object obj) {
        if (obj instanceof Path) return getCanonicalPathFromPath((Path)obj);
        if (obj instanceof File) return getCanonicalPathFromFile((File)obj);
        if (obj instanceof String) return getCanonicalPathFromString((String)obj);
        throw new RuntimeException("Cannot get canonical path of '"+obj+"'");
    }



    private static String getCanonicalPathFromFirstArg(Hook hook) {
        if (hook.args() == null || hook.args().length == 0) {
            throw new IllegalArgumentException("Missing first argument of "+hook.originExecutable());
        }
        try {
            return getCanonicalPath(hook.arg(0));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unexpected first argument of "+hook.originExecutable(), e);
        }
    }

    private static String getCanonicalPathFromSecondArg(Hook hook) {
        if (hook.args() == null || hook.args().length < 2) {
            throw new IllegalArgumentException("Missing second argument of "+hook.originExecutable());
        }
        try {
            return getCanonicalPath(hook.arg(1));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unexpected second argument of "+hook.originExecutable(), e);
        }
    }

    private static String getCanonicalPathFromArg(Object arg, Executable originExecutable) {
        try {
            return getCanonicalPath(arg);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unexpected argument of "+originExecutable, e);
        }
    }



    private class TargetInsert extends ActionInsert {
        private TargetInsert(Action ... actions) {
            super(PermitFileCheck.this, actions);
        }
        @Override
        protected String getName(Hook hook) {
            return getCanonicalPath(hook.target());
        }
    };
    private class FirstArgInsert extends ActionInsert {
        private FirstArgInsert(Action ... actions) {
            super(PermitFileCheck.this, actions);
        }
        @Override
        protected String getName(Hook hook) {
            return getCanonicalPathFromFirstArg(hook);
        }
    };
    
    public EnterInsert targetExecute() {
        return new TargetInsert(Action.EXECUTE);
    }
    public EnterInsert targetRead() {
        return new TargetInsert(Action.READ);
    }
    public EnterInsert targetWrite() {
        return new TargetInsert(Action.WRITE);
    }
    public EnterInsert targetDelete() {
        return new TargetInsert(Action.DELETE);
    }

    public EnterInsert firstArgExecute() {
        return new FirstArgInsert(Action.EXECUTE);
    }
    public EnterInsert firstArgRead() {
        return new FirstArgInsert(Action.READ);
    }
    public EnterInsert firstArgWrite() {
        return new FirstArgInsert(Action.WRITE);
    }
    public EnterInsert firstArgDelete() {
        return new FirstArgInsert(Action.DELETE);
    }
    public EnterInsert firstArgReadDelete() {
        return new FirstArgInsert(Action.READ, Action.DELETE);
    }

    private class FirstArgMkdirs extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            if (hook.args() == null || hook.args().length == 0) {
                throw new IllegalArgumentException("Missing first argument of "+hook.originExecutable());
            }
            Object arg = hook.arg(0);
            if (arg instanceof Path) {
                Path p = (Path)arg;
                while (p != null) {
                    checkRead(getCanonicalPath(p), hook);
                    checkWrite(getCanonicalPath(p), hook);
                    if (!p.toFile().exists()) {
                        p = p.getParent();
                    } else {
                        break;
                    }
                }
            }
        }
    }
    public FirstArgMkdirs firstArgMkdirs() {
        return new FirstArgMkdirs();
    }

    private class TargetMkdirs extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            File f = (File)hook.target();
            while (f != null) {
                checkRead(getCanonicalPath(f), hook);
                checkWrite(getCanonicalPath(f), hook);
                if (!f.exists()) {
                    f = f.getParentFile();
                } else {
                    break;
                }
            }
        }
    }
    public TargetMkdirs targetMkdirs() {
        return new TargetMkdirs();
    }


    private class RenameToInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            checkWrite(getCanonicalPath((File)hook.target()), hook);
            checkWrite(getCanonicalPathFromFirstArg(hook), hook);        
        }
    }
    public RenameToInsert renameTo() {
        return new RenameToInsert();
    }

    private class TwoArgsReadWriteInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            checkRead(getCanonicalPathFromFirstArg(hook), hook);        
            checkWrite(getCanonicalPathFromSecondArg(hook), hook);        
        }
    }
    public TwoArgsReadWriteInsert twoArgsReadWrite() {
        return new TwoArgsReadWriteInsert();
    }

    private class TwoArgsWriteInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            checkWrite(getCanonicalPathFromFirstArg(hook), hook);        
            checkWrite(getCanonicalPathFromSecondArg(hook), hook);        
        }
    }
    public TwoArgsWriteInsert twoArgsWrite() {
        return new TwoArgsWriteInsert();
    }

    private class TwoArgsReadInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            checkRead(getCanonicalPathFromFirstArg(hook), hook);        
            checkRead(getCanonicalPathFromSecondArg(hook), hook);        
        }
    }
    public TwoArgsReadInsert twoArgsRead() {
        return new TwoArgsReadInsert();
    }

    private class ZipFileConstructorInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            checkRead(getCanonicalPathFromFirstArg(hook), hook);
            int mode = java.util.zip.ZipFile.OPEN_READ; // default mode
            if (hook.args().length > 1 && wrap(hook.arg(1).getClass()).isAssignableFrom(Integer.class)) {
                mode = (int)hook.arg(1);
            }
            if ((mode & java.util.zip.ZipFile.OPEN_DELETE) != 0) {
                checkDelete(getCanonicalPathFromFirstArg(hook), hook);
            }
        }
    }

    public ZipFileConstructorInsert zipFileConstructor() {
        return new ZipFileConstructorInsert();
    }

    private class RandomAccessFileConstructorInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            String path = getCanonicalPathFromFirstArg(hook);
            checkRead(path, hook);

            if (hook.args().length < 2) {
                throw new IllegalArgumentException("Missing 2nd argument of " + hook.originExecutable());
            }

            if (hook.arg(1) instanceof String) {
                boolean rw = ((String)hook.arg(1)).startsWith("rw");
                if (rw) {
                    checkWrite(path, hook);        
                }
            } else {
                throw new IllegalArgumentException("Illegal 2nd argument of " + hook.originExecutable());
            }
        }
    }
    public RandomAccessFileConstructorInsert randomAccessFileConstructor() {
        return new RandomAccessFileConstructorInsert();
    }

    private class ProcessBuilderStartInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            String prog = ((ProcessBuilder)hook.target()).command().get(0);
            if (prog != null) {
                prog = getCanonicalPath(prog);
                checkRead(prog, hook, "read not permitted for "+prog);
                checkExecute(prog, hook, "execute not permitted for "+prog);
            }
        }
    }
    public ProcessBuilderStartInsert processBuilderStart() {
        return new ProcessBuilderStartInsert();
    }

    private class FileCreateTempFileInsert extends EnterInsert {
        @Override
        public void onEnterImpl(Hook hook) {
            if (hook.args().length < 2) {
                throw new IllegalArgumentException("There should be at least two arguments of " + hook.originExecutable());
            }
            String prefix;
            if (hook.arg(0) instanceof String) {
                prefix = (String)hook.arg(0);
            } else {
                throw new IllegalArgumentException("Illegal first argument of " + hook.originExecutable());
            }
            String suffix;
            if (hook.arg(1) == null) {
                suffix = ".tmp";
            } else if (hook.arg(1) instanceof String) {
                suffix = (String)hook.arg(1);
            } else {
                throw new IllegalArgumentException("Illegal second argument of " + hook.originExecutable());
            }

            String dir;
            if (hook.args().length == 3 && hook.arg(2) != null) {
                dir = getCanonicalPathFromArg(hook.arg(2), hook.originExecutable());
            } else {
                dir = System.getProperty("java.io.tmpdir");
            }

            // File.createTempFile generates a random filename. We cant predict the exact filename, but we can generate our
            // own filename, and if that own filename is permitted to write, then we may assume, that another random filename
            // would also be okay.
            // In the standard implementation there is some check, if the path of the random file name is too long.
            // Here we assume, it won't be too long by cutting off many digits.
            long n = random.nextLong();
            String nus = Long.toUnsignedString(n).substring(0, 8); // 8 digits

            prefix = (new File(prefix)).getName(); // Use only the file name from the supplied prefix
            File path = new File(dir, prefix + nus + suffix);

            checkWrite(getCanonicalPath(path), hook);
        }
    }
    public FileCreateTempFileInsert fileCreateTempFile() {
        return new FileCreateTempFileInsert();
    }

}
