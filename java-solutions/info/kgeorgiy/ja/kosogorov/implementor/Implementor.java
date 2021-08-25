package info.kgeorgiy.ja.kosogorov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 * @author Evgenij Kosogorov
 * Implementation of {@link JarImpler}
 * Creates {@code .java} or {@code .jar} file as an implementation of the given class
 */
public class Implementor implements JarImpler {
    /**
     * Checks type token's full name for accessibility
     * @param token type token
     * @return {@code true}, if class can be accessed by name, {@code false} otherwise
     */
    private static boolean isInaccessible(Class<?> token) {
        if (token == null) {
            return false;
        }
        Class<?> declaring = token.getDeclaringClass();
        int modifiers = token.getModifiers();
        return Modifier.isPrivate(modifiers)
                || !Modifier.isStatic(modifiers) && declaring != null
                || isInaccessible(declaring);
    }

    /**
     * Wrapper class for {@link Method} to compare methods by signature
     */
    private static final class MethodWrapper {
        /**
         * Instance of {@link Method} for wrapper
         */
        private final Method method;

        /**
         * Constructs new instance of {@link MethodWrapper}
         * @param method inner {@link Method}
         */
        private MethodWrapper(Method method) {
            this.method = method;
        }

        /**
         * Getter for method's name
         * @return method's name
         */
        private String getName() {
            return method.getName();
        }

        /**
         * Getter for method's return type
         * @return type token method's return type
         */
        private Class<?> getReturnType() {
            return method.getReturnType();
        }

        /**
         * Getter for method's parameter types
         * @return array of parameters' type tokens
         */
        private Class<?>[] getParameterTypes() {
            return method.getParameterTypes();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            MethodWrapper other = (MethodWrapper)obj;
            return other.getName().equals(getName()) &&
                    getReturnType().equals(other.getReturnType()) &&
                    Arrays.equals(getParameterTypes(), other.getParameterTypes());
        }

        @Override
        public int hashCode() {
            return getName().hashCode() * 31 + getReturnType().hashCode() * 47
                    + Objects.hash((Object[])getParameterTypes());
        }
    }

    /**
     * Appends given implementation of the given method or constructor to a given string builder
     * @param method method or constructor, used for extracting parameters, modifiers and exceptions
     * @param builder string builder
     * @param returnType method's return type which will be appended or empty string for constructors
     * @param body implementation of the method or constructors
     * @param methodName name of the implementing method
     */
    private static void printMethod(Executable method, StringBuilder builder,
                                    String returnType, String body, String methodName) {
        builder
                .append(Modifier.toString(
                        method.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.TRANSIENT))
                .append(" ")
                .append(returnType)
                .append(" ")
                .append(methodName)
                .append(" (")
                .append(Arrays.stream(method.getParameters())
                        .map(parameter -> parameter.getType().getCanonicalName() + " " + parameter.getName())
                        .collect(Collectors.joining(", ")))
                .append(") ");
        Class<?>[] exceptions = method.getExceptionTypes();
        if (exceptions.length > 0) {
            builder
                    .append("throws ")
                    .append(Arrays.stream(exceptions).map(Class::getCanonicalName).collect(Collectors.joining(" ")))
                    .append(" ");
        }
        builder
                .append("{")
                .append(body)
                .append("; }")
                .append(System.lineSeparator());
    }

    /**
     * Get absolute path of the given class
     * @param root directory, which contains given class' package
     * @param token class' type token
     * @return absolute path to a given class
     */
    private static Path getResolvedPath(Path root, Class<?> token) {
        return root.toAbsolutePath()
                .resolve(token.getPackageName().replace(".", File.separator));
    }

    /**
     * Adds a file + given suffix to the given path.
     * @param root directory
     * @param token type token
     * @param suffix suffix to add
     * @return path to a file named {@code token.getSimpleName() + suffix} like it's in a given directory.
     */
    private static Path addFileWithSuffix(Path root, Class<?> token, String suffix) {
        return root.resolve(token.getSimpleName() + suffix);
    }

    /**
     * Converts given string to utf format
     * @param string a string to convert
     * @return converted string
     */
    private static String toUTF(String string) {
        StringBuilder res = new StringBuilder();
        for (char c : string.toCharArray()) {
            res.append((c >= 128? String.format("\\u%04x", (int)c) : c));
        }
        return res.toString();
    }

    /**
     * Implements given {@link Class} and put {@code .java} file to a given {@link Path}
     * @param token {@link Class} to implement
     * @param root destination {@link Path}
     * @throws ImplerException when implementation can not be generated
     */
    private static void implementImpl(Class<?> token, Path root) throws ImplerException {
        int tokenModifiers = token.getModifiers();
        if (Modifier.isFinal(tokenModifiers) || isInaccessible(token)) {
            throw new ImplerException("Cannot access class");
        }
        if (token.isPrimitive()) {
            throw new ImplerException("Cannot implement primitive types");
        }
        if (token.equals(Enum.class)) {
            throw new ImplerException("Cannot implement Enum");
        }

        String className = token.getSimpleName() + "Impl";
        try(BufferedWriter writer = Files.newBufferedWriter(root)) {
            final StringBuilder builder = new StringBuilder();
            Package classPackage = token.getPackage();
            builder
                    .append(classPackage == null? "" : classPackage)
                    .append(";")
                    .append(System.lineSeparator())
                    .append("public class ")
                    .append(className)
                    .append(token.isInterface()? " implements " : " extends ")
                    .append(token.getCanonicalName())
                    .append(" { ")
                    .append(System.lineSeparator());

            Class<?> curToken = token;
            Set<MethodWrapper> methods = Arrays.stream(token.getMethods())
                    .map(MethodWrapper::new).collect(Collectors.toCollection(HashSet::new));
            while (curToken != null) {
                methods.addAll(Arrays.stream(curToken.getDeclaredMethods())
                        .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                        .map(MethodWrapper::new).collect(Collectors.toList()));
                curToken = curToken.getSuperclass();
            }
            methods.stream()
                    .filter(methodWrapper -> Modifier.isAbstract(methodWrapper.method.getModifiers()))
                    .forEach(methodWrapper -> {
                        Method method = methodWrapper.method;
                        Class<?> returnType = method.getReturnType();
                        printMethod(method, builder, returnType.getCanonicalName(),
                                "return " + (returnType.isPrimitive() ?
                                        (returnType.equals(void.class) ? "" :
                                                (returnType.equals(boolean.class)) ? "false" : "0") : "null"), method.getName());
                    });
            List<Constructor<?>> constructors = Arrays.stream(token.getDeclaredConstructors())
                    .filter(constructor -> !Modifier.isPrivate(constructor.getModifiers()))
                    .collect(Collectors.toList());
            if (constructors.isEmpty() && !token.isInterface()) {
                throw new ImplerException("Cannot implements class without public and protected constructors");
            }
            constructors
                    .forEach(constructor -> {
                        Parameter[] parameters = constructor.getParameters();
                        printMethod(constructor, builder, "",
                                "super(" + Arrays.stream(parameters)
                                        .map(Parameter::getName).collect(Collectors.joining(", ")) + ")", className);
                    });
            builder
                    .append("}")
                    .append(System.lineSeparator());
            writer.write(toUTF(builder.toString()));
        } catch (IOException e) {
            throw new ImplerException("write problem", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        root = addFileWithSuffix(getResolvedPath(root, token), token, "Impl.java");
        try {
            Files.createDirectories(root.getParent());
        } catch (IOException e) {
            throw new ImplerException("problem while creating parent directory", e);
        }
        implementImpl(token, root);
    }

    /**
     * Creates {@code .class} file of given {@code .java} file
     * @param token type token
     * @param filePath destination path
     * @throws ImplerException if compiler's exit code is nonzero
     */
    private void compile(Class<?> token, Path filePath) throws ImplerException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final String classpath = getClassPath(token);
        String[] args;
        if (classpath == null) {
            args = new String[]{
                    "--patch-module",
                    token.getModule().getName() + "=" + filePath.getParent(),
                    filePath.toString()
            };
        } else {
            args = new String[]{
                    filePath.toString(),
                    "-cp",
                    filePath.getParent() + File.pathSeparator + classpath
            };
        }
        final int exitCode = compiler.run(null, null, null, args);
        if (exitCode != 0) {
            throw new ImplerException("Error while compiling implementation. Exit code: " + exitCode);
        }
    }

    /**
     * Returns class path to a given type token
     * @param token type token
     * @return {@link String} - class path
     */
    private static String getClassPath(Class<?> token) {
        try {
            CodeSource codeSource = token.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return null;
            }
            return Path.of(codeSource.getLocation().toURI()).toString();
        } catch (final URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        jarFile = jarFile.toAbsolutePath();
        final Path jarPath = jarFile.getParent().toAbsolutePath();
        final Path implementation = addFileWithSuffix(jarPath, token, "Impl.java");
        final Path compiled = addFileWithSuffix(jarPath, token, "Impl.class");
        implementImpl(token, implementation);
        compile(token, implementation);
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (final JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            jarOutputStream.putNextEntry(new ZipEntry(
                    token.getPackageName().replace('.', '/')
                            + "/" + token.getSimpleName() + "Impl.class"
            ));
            Files.copy(compiled, jarOutputStream);
        } catch (final IOException e) {
            throw new ImplerException("Error while making jar file: " + e.getMessage());
        } finally {
            try {
                Files.delete(implementation);
            } catch (IOException e) {
                System.err.println("Failed to delete .java file");
            }
            try {
                Files.delete(compiled);
            } catch (IOException e) {
                System.err.println("Failed to delete .class file");
            }
        }
    }

    /**
     * Generates an implementation of given class(optionally creates {@code .jar} file)
     * Arguments can be <ul>
     *     <li>[classname], [destination path] - generates {@code .java} file</li>
     *     <li>["-jar"], [classname], [destination path] - generates {@code .jar} file</li>
     * </ul>
     * @param args command line arguments
     */
    public static void main(String[] args) {
        if (args == null || args.length > 3 || args.length < 2 || args.length == 3 && !args[0].equals("-jar")) {
            System.err.println("Wrong argument' format. Expected: [-jar] <classname> <path>");
            return;
        }
        JarImpler impl = new Implementor();
        try {
            if (args.length == 3) {
                impl.implementJar(Class.forName(args[1]), Path.of(args[2]));
            } else {
                impl.implement(Class.forName(args[0]), Path.of(args[1]));
            }
        } catch (ImplerException e) {
            System.err.println("Error while generating class: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Class can not be found: " + e.getMessage());
        }
    }
}
