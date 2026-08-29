package com.mitype.blur.core;

/**
 * Hook 安装桥：由各 flavor 的入口类实现（api101/api102 的 Hooker 是不同的接口类型，
 * 无法在共享代码里直接引用），这里只提供「方法 + 拦截器」的抽象。
 *
 * 适配器约定：
 * - before 语义：先把原入参拷入 MethodCall，拦截器可 setArg；返回后适配器以（可能修改过的）参数
 *   调用 chain.proceed(args)。
 * - after 语义：适配器先 chain.proceed()，把返回值暴露给拦截器；拦截器可 setResult 覆盖。
 */
public final class HookInstaller {

    /** 拦截器抽象：before/after 共用。 */
    public interface Interceptor {
        void intercept(MethodCall call) throws Throwable;
    }

    /** 对 libxposed Chain 的最小抽象。 */
    public interface MethodCall {
        Object getThisObject();

        int argCount();

        Object getArg(int index);

        void setArg(int index, Object value);

        Object getResult();

        void setResult(Object result);
    }

    public interface Installer {
        void hookMethod(java.lang.reflect.Executable method, boolean before, Interceptor interceptor);
    }

    private static volatile Installer sInstaller;

    public static void init(Installer installer) {
        sInstaller = installer;
    }

    public static void hookAfter(java.lang.reflect.Executable method, Interceptor interceptor) {
        Installer i = sInstaller;
        if (i == null) throw new IllegalStateException("HookInstaller not initialized");
        i.hookMethod(method, false, interceptor);
    }

    public static void hookBefore(java.lang.reflect.Executable method, Interceptor interceptor) {
        Installer i = sInstaller;
        if (i == null) throw new IllegalStateException("HookInstaller not initialized");
        i.hookMethod(method, true, interceptor);
    }

    private HookInstaller() {
    }
}
