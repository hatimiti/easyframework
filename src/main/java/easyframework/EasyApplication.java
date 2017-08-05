package easyframework;

import java.lang.annotation.Annotation;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
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

    /** key=classFullName, value=instance */
    private static Map<Class<?>, Object> components = new HashMap<>();
    /** key=path, value=instance and method */
    private static Map<String, HTTPController> controllers = new HashMap<>();

    public static void run(Class<?> clazz, String... args) {
        scanComponents(clazz);
        injectDependencies();
        registerHTTPPaths();
        processHTTPRequest();
    }

    /**
     * Class -> https://docs.oracle.com/javase/jp/8/docs/api/java/lang/Class.html
     * Package -> https://docs.oracle.com/javase/jp/8/docs/api/java/lang/Package.html
     */
    private static void scanComponents(Class<?> source) {
        List<Class<?>> classes = scanClasses(source.getPackage().getName());
        classes.stream()
            .filter(clazz ->
                clazz.isAnnotationPresent(Component.class)
                    || clazz.isAnnotationPresent(Controller.class))
            .forEach(clazz -> uncheck(() -> components.put(clazz, clazz.newInstance())));
    }

    /**
     * 参考元: http://etc9.hatenablog.com/entry/2015/03/31/001620
     */
    private static List<Class<?>> scanClasses(String packageName) {
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
                        System.out.println("Registerd Controller: " + rm.value() + " - " + c);
                    })
            );
    }

    private static void processHTTPRequest() {
        try (ServerSocket server = new ServerSocket(8080);
                Socket socket = server.accept();
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

            String path = br.readLine().split(" ")[1];
            HTTPController c = controllers.get(path);
            String result = (String) c.method.invoke(c.instance);

            PrintStream os = new PrintStream(socket.getOutputStream());
            os.println(result);
            os.flush();
            os.close();

        } catch (IOException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
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
}
