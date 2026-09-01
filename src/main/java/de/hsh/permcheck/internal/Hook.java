package de.hsh.permcheck.internal;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;

public record Hook(Class<?> originClazz, Object target, Executable originExecutable, Object[] args) {

    public String pretty() {
        StringBuilder msg = new StringBuilder();
        msg.append(originClazz.getName());
        if (target != null) {
            msg.append("(").append(String.valueOf(target)).append(")");
        }
        msg.append(".");
        if (originExecutable instanceof Constructor) {
            msg.append("<init>");
        } else {
            msg.append(originExecutable.getName());
        }
        msg.append("(");
        if (args != null && args.length > 0) {
            msg.append(prettyArg(args[0]));
            for (int i = 1; i < args.length; i++) {
                msg.append(", ").append(prettyArg(args[i]));
            }
        }
        msg.append(")");
        return msg.toString();
    }

    private static String prettyArg(Object arg) {
        if (arg == null) return "null";
        Class<?> clazz = arg.getClass();
        clazz = MethodType.methodType(clazz).unwrap().returnType();
        if (clazz.equals(char.class)) return "'" + arg + "'";
        if (clazz.equals(Character.class)) return "'" + arg + "'";
        if (clazz.equals(String.class)) return "\"" + arg + "\"";
        if (clazz.equals(Class.class)) return ((Class<?>)arg).getName()+".class";
        if (clazz.isPrimitive()) return String.valueOf(arg);
        return clazz.getName() + "(" + String.valueOf(arg) + ")";
    }

    public String getStringFromFirstArg() {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Missing first argument of "+originExecutable);
        }
        if (arg(0) instanceof String) {
            return (String)arg(0);
        }
        throw new IllegalArgumentException("Unexpected argument of "+originExecutable);
    }

    public Object arg(int i) {
        return args[i];
    }

}