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
        /*
         * 対象のメソッド、または型定義に @Transactional アノテーションが付加されている場合に
         * 下記の動作をする処理をインターセプトしてください。
         * 1. 該当のメソッド実行前に "Starts transaction." を標準出力に表示する。
         * 2. 該当のメソッド実行が正常終了した場合 "Commit transaction." を標準出力に表示する。
         * 3. 該当のメソッド実行が例外終了した場合 "Rollbak transaction." を標準出力に表示し、例外を上位に throw する。
         * 
         * ※ @Transactional が付加されていない場合は、該当のメソッドの実行結果のみ返してください。
         * ※ @Transactional アノテーションは easyframework.Transactional.java として新規作成してください。
         * ※ ./gradlew clean build run で実行し、curl http://localhost:8080/hello で確認してください。
         */
         return null;
    }
}
