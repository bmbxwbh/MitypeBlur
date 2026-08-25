package com.mitype.blur.core;

import java.lang.reflect.Method;

/**
 * Hook 策略（基于 smali 交叉验证修正，支持 0.2.346 / 0.2.169 双版本）：
 * - CAP: 能力总闸/OS版本解除（详见 installCapabilityBypass），需在其余钩子前安装
 * - H1: helper.l() after —— d=TRUE 接通毛玻璃；e/f 为互斥明暗极性流，按 material_policy
 *   二选一写入；k 材质变体与极性一致。状态必须每次事件重写（原版 l() 会回写覆盖）
 * - P1': helper.d(Z) after —— 材质懒加载全进程仅构建一次：捕获实例并【一次性】套用预设
 *   （= 出厂混色三层 alpha 等比缩放；J/f[] 几何零改动）。配置采用启动快照（ModuleMain 只在
 *   包就绪时读取一次），任何修改需重启输入法进程生效 —— 刻意设计：单次写入杜绝对出厂
 *   单例的累积性破坏（此前热重放缩放 f[] 曾导致深色套近黑、浅色套发白且无法还原）
 * - R1 已删除：setMiBackgroundBlurRadius 不在键盘模糊管线中，属死钩子
 */
public final class BlurHooks {

    public static final String TAG = "MitypeBlur";
    public static final String TARGET_PKG = "com.xiaomi.type";

    /** 捕获的缓存材质实例（懒加载各构建一次，之后复用同一对象）。 */
    private static volatile Object materialLight;
    private static volatile Object materialDark;

    public interface LogFn {
        void invoke(String msg, Throwable t);
    }

    public interface ConfigFn {
        /** 实现内必须保证每次调用都读到最新配置（等价于 reload 语义），这是「能调节」的生命线。 */
        Config get();
    }

    private BlurHooks() {
    }

    public static void installAll(ClassLoader cl, TargetMap tm, LogFn logFn, ConfigFn configFn) {
        installCapabilityBypass(cl, tm, logFn, configFn);   // CAP 解除系统能力校验
        installStateGateHook(cl, tm, logFn, configFn);      // H1 (+P3/圆角) + 明暗策略
        installMaterialCaptureHook(cl, tm, logFn, configFn); // P1' 材质捕获 + 单次参数写入
        installHapticStyleHook(cl, logFn, configFn);        // H4 触感风格重映射
        installStrokeUniformHook(cl, logFn, configFn);      // DEV 描边着色器细参
        installCandidateSoftenHook(cl, logFn, configFn);   // SOFT 候选词蓝光柔化
    }

    /**
     * CAP: 系统能力校验解除。
     * 背景：澎湃OS模糊 API 封装类的每个方法都在指令 0 检查能力总闸
     * （0.2.346=xe.b.c / 0.2.169=cf.b.c，clinit 读 persist.sys.background_blur_supported 等属性），
     * 旗标关闭的设备上所有调用被静默丢弃——这是「模块已装但看不到毛玻璃」的隐形原因之一。
     *
     * P-总闸: 强制能力方法恒返 TRUE，一点解开全部模糊 API 门禁；
     *         IMS.onCreate/onConfigurationChanged 派生的 helper.s 标志也随之自愈。
     * P-版本: blurApiClass 的无参静态 int 方法返回澎湃OS大版本，<1 时混色接口与材质
     *   Parcel 序列化走退化分支；兜底抬到 3 启用完整高级视觉路径。
     *
     * 边界：只能解除「软件旗标」门禁；纯 AOSP ROM 上 ViewExtension 方法物理缺失，
     * 反射会失败并走既有降级路径，此类设备需标准 FLAG_BLUR_BEHIND 替代方案。
     */
    private static void installCapabilityBypass(final ClassLoader cl, final TargetMap tm,
                                                final LogFn logFn, final ConfigFn configFn) {
        // P-总闸
        try {
            Class<?> capCls = Class.forName(tm.capabilityClass, false, cl);
            Method gate = capCls.getDeclaredMethod(tm.capabilityGateMethod);
            HookInstaller.hookAfter(gate, new HookInstaller.Interceptor() {
                @Override
                public void intercept(HookInstaller.MethodCall call) {
                    if (configFn.get().bypassVersionCheck) {
                        call.setResult(Boolean.TRUE);
                    }
                }
            });
            logFn.invoke("CAP capability gate bypassed ("
                    + tm.capabilityClass + "." + tm.capabilityGateMethod + ")", null);
        } catch (Throwable t) {
            logFn.invoke("CAP gate install failed", t);
        }
        // P-版本下限
        try {
            Class<?> apiCls = Class.forName(tm.blurApiClass, false, cl);
            Method osVer = apiCls.getDeclaredMethod(tm.osVersionMethod);
            HookInstaller.hookAfter(osVer, new HookInstaller.Interceptor() {
                @Override
                public void intercept(HookInstaller.MethodCall call) {
                    Config c = configFn.get();
                    if (!c.bypassVersionCheck) return;
                    int v = (Integer) call.getResult();
                    if (v < 1) call.setResult(3);
                }
            });
            logFn.invoke("CAP os-version floor installed ("
                    + tm.blurApiClass + "." + tm.osVersionMethod + ")", null);
        } catch (Throwable t) {
            logFn.invoke("CAP os-version floor failed (optional)", t);
        }
    }

    // H4: t9.b.a(事件枚举) after —— 按预设替换触感波形常量（唯一风格出口）
    private static void installHapticStyleHook(final ClassLoader cl, final LogFn logFn,
                                               final ConfigFn configFn) {
        try {
            Class<?> cls = Class.forName("t9.b", false, cl);
            Class<?> eventCls = Class.forName("t9.a", false, cl);
            Method target = cls.getDeclaredMethod("a", eventCls);
            HookInstaller.hookAfter(target, new HookInstaller.Interceptor() {
                @Override
                public void intercept(HookInstaller.MethodCall call) {
                    Config cfg = configFn.get();
                    if (!cfg.enable || cfg.hapticPreset == Config.HAPTIC_NATIVE) return;
                    Object ev = call.getArg(0);
                    if (ev == null) return;
                    Integer override = HapticStyles.styleFor(cfg.hapticPreset, ev.toString());
                    if (override != null && !override.equals(call.getResult())) {
                        call.setResult(override);
                    }
                }
            });
            logFn.invoke("H4 haptic-style hook installed", null);
        } catch (Throwable t) {
            logFn.invoke("H4 install failed (haptic mapper missing?)", t);
        }
    }

    // H1: 状态闸门 + 材质明暗策略 + 参数重放
    private static void installStateGateHook(final ClassLoader cl, final TargetMap tm,
                                             final LogFn logFn, final ConfigFn configFn) {
        try {
            Class<?> cls = Class.forName(tm.helperClass, false, cl);
            Method target = cls.getDeclaredMethod("l");
            HookInstaller.hookAfter(target, new HookInstaller.Interceptor() {
                @Override
                public void intercept(HookInstaller.MethodCall call) {
                    Config cfg = configFn.get();
                    Object thiz = call.getThisObject();
                    if (thiz == null) return;

                    if (cfg.enable) {
                        boolean wantDark = resolveWantDark(thiz, cfg);
                        // d=毛玻璃总开关；e/f 为互斥的明暗极性流。
                        // 实测校准（v1.5.0 极性相反）：e=TRUE → 深色观感（白字），f=TRUE → 浅色观感（黑字）
                        ReflectUtil.setFlowValue(thiz, "d", Boolean.TRUE);
                        ReflectUtil.setFlowValue(thiz, "e", wantDark);
                        ReflectUtil.setFlowValue(thiz, "f", !wantDark);
                        // 深浅材质变体选择字段与极性保持一致
                        if (ReflectUtil.getBooleanField(thiz, "k", !wantDark) != wantDark) {
                            ReflectUtil.setBooleanField(thiz, "k", wantDark);
                        }
                        // DEV: 圆角覆盖（<0 跟随原版）
                        if (cfg.devCornerDp >= 0) {
                            float px = cfg.devCornerDp * android.content.res.Resources
                                    .getSystem().getDisplayMetrics().density;
                            ReflectUtil.setFloatField(thiz, "x", px);
                            ReflectUtil.setFloatField(thiz, "y", px);
                        }
                    }
                }
            });
            logFn.invoke("H1 state-gate + param-replay installed (" + tm.helperClass + ")", null);
        } catch (Throwable t) {
            logFn.invoke("H1 install failed (target version changed?)", t);
        }
    }

    /**
     * 解析明暗极性目标：0=跟随系统夜间模式 / 1=锁定浅色 / 2=锁定深色。
     * 该值同时决定材质变体（k）与文字/系统栏图标极性（e/f 互斥流），全局自洽。
     */
    private static boolean resolveWantDark(Object thiz, Config cfg) {
        switch (cfg.materialPolicy) {
            case Config.POLICY_FORCE_LIGHT:
                return false;
            case Config.POLICY_FOLLOW_SYSTEM:
                Object svc = ReflectUtil.getObjectField(thiz, "a");
                int ui = 0;
                if (svc instanceof android.content.Context) {
                    ui = ((android.content.Context) svc)
                            .getResources().getConfiguration().uiMode;
                }
                return (ui & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            case Config.POLICY_FORCE_DARK:
            default:
                return true;
        }
    }

    // P1': 材质实例捕获（懒加载首次构建时各一次）
    private static void installMaterialCaptureHook(final ClassLoader cl, final TargetMap tm,
                                                   final LogFn logFn, final ConfigFn configFn) {
        try {
            Class<?> cls = Class.forName(tm.helperClass, false, cl);
            Method target = cls.getDeclaredMethod(tm.materialFactoryMethod, boolean.class);
            HookInstaller.hookAfter(target, new HookInstaller.Interceptor() {
                @Override
                public void intercept(HookInstaller.MethodCall call) {
                    Object material = call.getResult();
                    if (material == null) return;
                    if (Boolean.TRUE.equals(call.getArg(0))) {
                        materialDark = material;
                    } else {
                        materialLight = material;
                    }
                    applyToMaterial(material, configFn.get()); // 构建时一次性写入预设
                }
            });
            logFn.invoke("P1' material-capture installed", null);
        } catch (Throwable t) {
            logFn.invoke("P1' install failed", t);
        }
    }

    /**
     * 构建时一次性参数写入（每个材质实例仅执行一次，进程内不复读配置）：
     * - 预设 = 出厂混色三层 alpha 等比缩放（轻盈×0.60 / 柔光×1.00 零写入 / 厚重×1.18，上限0xFA）。
     * - 几何参数 J 与 f[] 全程零改动。f[] 是与 J 无关的出厂调优常量
     *   （light={121,3,3} / dark={15,3,3}），此前按比例缩放 f[] 的做法破坏了出厂调优，
     *   导致霜面衰减半径爆表——深色套近黑、浅色套发白——该逻辑已彻底移除。
     * - 默认档（柔光 ×1.00）零写入 ⇒ 「直接启用模块不改任何配置」与原生观感逐字节一致
     *   （高可用基线，勿动）。
     */
    private static void applyToMaterial(Object m, Config c) {
        if (!c.enable || m == null) return;
        // 生效值 = 开发者覆盖(>0) 否则预设档；柔光+无覆盖 ⇒ 双零写入（原生基线）。
        // 构建期单次写入：此刻 f[] 仍为出厂值，等比缩放安全且确定；
        // 进程生命周期内不再二次触碰 —— 杜绝旧热重放模式的累积破坏。
        float cs = c.effColorScale();
        if (Math.abs(cs - 1f) > 0.01f) {
            ReflectUtil.scaleAlphas(m, "e", cs);
        }
        float rs = c.effRadiiScale();
        if (Math.abs(rs - 1f) > 0.01f) {
            ReflectUtil.scaleAlphas(m, "f", rs);
        }
    }

    /**
     * DEV: 描边着色器细参 —— 拦截 RuntimeShader.setFloatUniform，
     * 按 uniform 名替换/缩放入参。仅当开发者显式设置（≥0/>0）时介入。
     */
    private static void installStrokeUniformHook(final ClassLoader cl, final LogFn logFn,
                                                 final ConfigFn configFn) {
        try {
            Class<?> shaderCls = Class.forName("android.graphics.RuntimeShader", false, cl);
            Class<?>[] sigs = {
                    String.class, float.class,
                    String.class, float.class, float.class,
                    String.class, float.class, float.class, float.class,
                    String.class, float.class, float.class, float.class, float.class,
            };
            for (int n = 0; n < 4; n++) {
                final int floatCount = n + 1;
                Class<?>[] params = new Class<?>[floatCount + 1];
                params[0] = String.class;
                for (int i = 0; i < floatCount; i++) params[i + 1] = float.class;
                Method target = shaderCls.getMethod("setFloatUniform", params);
                HookInstaller.hookBefore(target, new HookInstaller.Interceptor() {
                    @Override
                    public void intercept(HookInstaller.MethodCall call) {
                        Config c = configFn.get();
                        if (!c.devMode) return;
                        String name = (String) call.getArg(0);
                        if ("uStrokeWidth".equals(name) && c.devStrokeDp >= 0) {
                            call.setArg(1, c.devStrokeDp * density());
                        } else if ("uGlowWidth".equals(name) && c.devGlowScale > 0) {
                            // 光晕宽度随强度滑杆联动：基准 3dp × 强度
                            call.setArg(1, 3f * c.devGlowScale
                                    * android.content.res.Resources.getSystem()
                                    .getDisplayMetrics().density);
                        } else if (("uAlpha1".equals(name) || "uAlpha2".equals(name))
                                && c.devGlowScale > 0) {
                            Object v = call.getArg(1);
                            if (v instanceof Float) {
                                float a = Math.min(1f, (Float) v * c.devGlowScale);
                                call.setArg(1, a);
                            }
                        }
                    }
                });
            }
            logFn.invoke("DEV stroke-uniform hooks installed (4 signatures)", null);
        } catch (Throwable t) {
            logFn.invoke("DEV stroke-uniform install failed (optional)", t);
        }
    }

    private static float density() {
        return android.content.res.Resources.getSystem().getDisplayMetrics().density;
    }

    /**
     * SOFT: 候选词蓝光柔化 —— 拦截 Paint.setColor，
     * 当检测到高饱和蓝色（候选词高亮色）时，向柔和灰蓝色偏移。
     * 仅在 devMode 或 candidate_soften>0 时生效。
     */
    private static void installCandidateSoftenHook(final ClassLoader cl, final LogFn logFn,
                                                   final ConfigFn configFn) {
        try {
            Class<?> paintCls = Class.forName("android.graphics.Paint", false, cl);
            Method target = paintCls.getDeclaredMethod("setColor", int.class);
            HookInstaller.hookBefore(target, new HookInstaller.Interceptor() {
                @Override
                public void intercept(HookInstaller.MethodCall call) {
                    Config c = configFn.get();
                    if (!c.enable || c.candidateSoften <= 0) return;
                    Object v = call.getArg(0);
                    if (!(v instanceof Integer)) return;
                    int color = (Integer) v;
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;
                    // 检测亮蓝色：蓝通道显著高于红通道，且整体亮度较高
                    if (b > r + 40 && b > 140 && g > r) {
                        float t = Math.min(c.candidateSoften / 100.0f, 1.0f) * 0.65f;
                        // 向柔和灰蓝 #96AAC8 混合
                        int nr = Math.round(r + (0x96 - r) * t);
                        int ng = Math.round(g + (0xAA - g) * t);
                        int nb = Math.round(b + (0xC8 - b) * t);
                        call.setArg(0, (0xFF << 24) | (nr << 16) | (ng << 8) | nb);
                    }
                }
            });
            logFn.invoke("SOFT candidate-soften hook installed", null);
        } catch (Throwable t) {
            logFn.invoke("SOFT candidate-soften install failed", t);
        }
    }

}
