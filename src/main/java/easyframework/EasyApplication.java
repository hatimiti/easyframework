package easyframework;

import java.lang.annotation.Annotation;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Closeable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.concurrent.Callable;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import javax.annotation.Resource;

public class EasyApplication {

    /** KEY=the class of the component, VALUE= A instance of the component */
    private final static Map<Class<?>, Object> components = new HashMap<>();

    /** KEY=path, VALUE= A instance and methods */
    private final static Map<String, HTTPController> controllers = new HashMap<>();

    public static void run(Class<?> clazz, String... args) {
        scanComponents(clazz);
        injectDependencies();
        registerHTTPPaths();
        startHTTPServer();
    }

    /**
     * Class -> https://docs.oracle.com/javase/jp/8/docs/api/java/lang/Class.html
     * Package -> https://docs.oracle.com/javase/jp/8/docs/api/java/lang/Package.html
     */
    private static void scanComponents(Class<?> source) {
        List<Class<?>> classes = scanClassesUnder(source.getPackage().getName());
        classes.stream()
            .filter(clazz -> hasComponentAnnotation(clazz))
            .forEach(clazz -> uncheck(() -> {
                Object instance = clazz.newInstance();
                // Registers by own type.
                components.put(clazz, instance);
                // Registers by type of super class.
                components.put(clazz.getSuperclass(), instance);
                // Registers by type of interfaces.
                Arrays.stream(clazz.getInterfaces())
                    .forEach(intf -> components.put(intf, instance));
            }));
        components.remove(Object.class);
        System.out.println("Registered Components => " + components);
    }

    private static boolean hasComponentAnnotation(Class<?> clazz) {
        return clazz.isAnnotationPresent(Component.class)
                || clazz.isAnnotationPresent(Controller.class);
    }

    /**
     * References: http://etc9.hatenablog.com/entry/2015/03/31/001620
     */
    private static List<Class<?>> scanClassesUnder(String packageName) {
        String packagePath = packageName.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL root = cl.getResource(packagePath);

        File[] files = new File(root.getFile())
            .listFiles((dir, name) -> name.endsWith(".class"));
        return Arrays.asList(files).stream()
                .map(file -> packageName + "." + file.getName().replaceAll(".class$", ""))
                .map(fullName -> uncheck(() -> Class.forName(fullName)))
                .collect(Collectors.toList());
    }

    /**
     * Field -> https://docs.oracle.com/javase/jp/8/docs/api/java/lang/reflect/Field.html
     */
    private static void injectDependencies() {
        components.forEach((clazz, component) -> {
            Arrays.asList(clazz.getDeclaredFields()).stream()
                .filter(field -> field.isAnnotationPresent(Resource.class))
                .forEach(field -> uncheck(() -> { 
                    field.setAccessible(true);
                    field.set(component, components.get(field.getType()));
                }));
        });
    }

    /**
     * Method -> https://docs.oracle.com/javase/jp/8/docs/api/java/lang/reflect/Method.html
     */
    private static void registerHTTPPaths() {
        components.entrySet().stream()
            .filter(kv -> kv.getKey().isAnnotationPresent(Controller.class))
            .forEach(kv ->
                Arrays.asList(kv.getKey().getMethods()).stream()
                    .filter(m -> m.isAnnotationPresent(RequestMapping.class))
                    .forEach(m -> {

                        RequestMapping rm = m.getAnnotation(RequestMapping.class);
                        HTTPController c = new HTTPController(rm.value(), kv.getValue(), m);
                        controllers.put(rm.value(), c);
                        System.out.println("Registered Controller => " + rm.value() + " - " + c);
                    })
            );
    }

    private static void startHTTPServer() {
        EasyHttpServer server = new EasyHttpServer(8080);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            // for Debug.
            //try (java.io.PrintWriter pw = new java.io.PrintWriter(new File("./info.log"))) {
            //    pw.println("Shutdown server.");
            //} catch (IOException e) {
            //}
        }));
        server.start();
    }
    
    private static class HTTPController {
        private final String path;
        private final Object instance;
        private final Method method;

        public HTTPController(String path, Object instance, Method method) {
            this.path = path;
            this.instance = instance;
            this.method = method;
        }

        @Override
        public String toString() {
            return "{ "
                + "\"path\": \"" + path + "\", "
                + "\"instance\": " + instance + "\", "
                + "\"method\": " + method + "\""
                + " }";
        }
    }

    private static <T> T uncheck(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void uncheck(ThrowsRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private static interface ThrowsRunnable {
        void run() throws Exception;
    }

    private static class EasyHttpServer implements Closeable {

        private final ServerSocket server;

        public EasyHttpServer(int port) {
            try {
                this.server = new ServerSocket(port);
            } catch (IOException e) {
                close();
                throw new IllegalStateException(e);
            }
        }

        public void start() {
            while (true) {
                acceptRequest:
                try (Socket socket = server.accept();
                        BufferedReader br = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

                    // br.readLine() => GET / HTTP/1.1
                    String path = br.readLine().split(" ")[1];
                    try (PrintStream os = new PrintStream(socket.getOutputStream())) {
                        HTTPController c = controllers.get(path);
                        if (c == null) {
                            os.println("404 Not Found (path = " + path + " ).");
                            break acceptRequest;
                        }
                        os.println(c.method.invoke(c.instance));
                    }

                } catch (IOException | IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(e);
                }
            } 
        }

        @Override
        public void close() {
            try {
                if (server != null) {
                    server.close();
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
