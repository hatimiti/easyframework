package easyframework;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

class Interceptor implements InvocationHandler {

    private final Object target;

    public static Object createProxiedTarget(Object target) {
        return Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            target.getClass().getInterfaces(),
            new Interceptor(target));
    }
    
    private Interceptor(Object obj) {
        this.target = obj;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(target, args);
        }

        return invoke(method, args); 
    }

    private Object invoke(Method method, Object[] args) throws Throwable {
        if (!method.isAnnotationPresent(Transactional.class)
                && !method.getDeclaringClass().isAnnotationPresent(Transactional.class)) {
            return method.invoke(target, args);
        }
        try {
            System.out.println("Starts transaction.");
            return method.invoke(target, args);
        } catch (Throwable t) {
            System.out.println("Rollback transaction.");
            throw t;
        } finally {
            System.out.println("Commit transaction.");
        }
    }
}
