package com.mitype.blur.hook;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.List;

import com.mitype.blur.core.BlurHooks;
import com.mitype.blur.core.Config;
import com.mitype.blur.core.HookInstaller;
import com.mitype.blur.core.TargetMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * api101 flavor 入口：libxposed api 101.0.1（与 102 源码级兼容）。
 * 配置经 LSPosed Remote Preferences 读取（与 UI 端 XposedServiceHelper 写入同一存储），
 * 跨进程跨 UID 可读，这是设置页调节能生效的传输通道。
 */
public class ModuleMain extends XposedModule {

    private static volatile SharedPreferences sRemotePrefs;

    public ModuleMain() {
        HookInstaller.init(new HookInstaller.Installer() {
            @Override
            public void hookMethod(final Executable method, final boolean before,
                                   final HookInstaller.Interceptor interceptor) {
                hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            MutableCall call = new MutableCall(chain);
                            if (before) {
                                try {
                                    interceptor.intercept(call);
                                } catch (Throwable t) {
                                    log(Log.WARN, BlurHooks.TAG, "interceptor error", t);
                                }
                                return chain.proceed(call.argsArray());
                            } else {
                                call.setResult(chain.proceed());
                                try {
                                    interceptor.intercept(call);
                                } catch (Throwable t) {
                                    log(Log.WARN, BlurHooks.TAG, "interceptor error", t);
                                }
                                return call.getResult();
                            }
                        });
            }
        });
    }

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!BlurHooks.TARGET_PKG.equals(param.getPackageName())) return;
        try {
            // RemotePreferences 内部已带缓存，重复读取开销极低；每次 get 均为最新值
            sRemotePrefs = getRemotePreferences(Config.PREFS_NAME);
            long versionCode = 0;
            try {
                // ApplicationInfo.versionCode 仅 API 33+ 存在；低版本走形状探测兜底
                java.lang.reflect.Field vf = android.content.pm.ApplicationInfo.class.getField("versionCode");
                versionCode = vf.getLong(param.getApplicationInfo());
            } catch (Throwable ignored) {
            }
            TargetMap tm = TargetMap.get(versionCode);
            if (tm == null) {
                log(Log.INFO, BlurHooks.TAG,
                        "unknown IME versionCode=" + versionCode + ", trying shape-detect fallback");
                tm = TargetMap.detectByShape(param.getClassLoader());
            }
            if (tm == null) {
                log(Log.WARN, BlurHooks.TAG, "no compatible target mapping found, abort");
                return;
            }
            final TargetMap map = tm;
            // 启动快照：进程内配置冻结 —— 任何修改需重启输入法进程生效（刻意设计：
            // 避免对出厂材质单例的反复破坏性改写；设置页顶部有醒目提示）。
            final Config bootConfig = Config.load(sRemotePrefs);
            log(Log.INFO, BlurHooks.TAG, "boot config: enable=" + bootConfig.enable
                    + " preset=" + bootConfig.blurPreset
                    + "(alphaScale=" + bootConfig.presetAlphaScale() + ")"
                    + " policy=" + bootConfig.materialPolicy
                    + " haptic=" + bootConfig.hapticPreset
                    + " bypassCheck=" + bootConfig.bypassVersionCheck);
            BlurHooks.installAll(
                    param.getClassLoader(),
                    map,
                    (msg, t) -> {
                        if (t == null) {
                            log(Log.INFO, BlurHooks.TAG, msg);
                        } else {
                            log(Log.WARN, BlurHooks.TAG, msg, t);
                        }
                    },
                    () -> bootConfig
            );
        } catch (Throwable t) {
            log(Log.ERROR, BlurHooks.TAG, "install failed", t);
        }
    }

    /** 把不可变的 Chain 参数包装成可写视图。 */
    private static final class MutableCall implements HookInstaller.MethodCall {
        private final XposedInterface.Chain chain;
        private final List<Object> args;
        private Object result;

        MutableCall(XposedInterface.Chain chain) {
            this.chain = chain;
            this.args = new ArrayList<>(chain.getArgs());
        }

        Object[] argsArray() {
            return args.toArray();
        }

        @Override
        public Object getThisObject() {
            return chain.getThisObject();
        }

        @Override
        public int argCount() {
            return args.size();
        }

        @Override
        public Object getArg(int index) {
            return args.get(index);
        }

        @Override
        public void setArg(int index, Object value) {
            args.set(index, value);
        }

        @Override
        public Object getResult() {
            return result;
        }

        @Override
        public void setResult(Object value) {
            result = value;
        }
    }
}
