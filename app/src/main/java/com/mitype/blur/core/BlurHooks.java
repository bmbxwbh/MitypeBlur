package com.mitype.blur.core;

import java.lang.reflect.Method;

/**
 * Hook 策略（基于 smali 交叉验证修正，支持 0.2.346 ~ 0.2.974）：
 * - CAP: 能力总闸/OS版本解除；0.2.974 起方法名双写（cc/ee/qq/dd）
 * - GATE: 启用门禁 —— 旧版 h()；0.2.790+ 为 g()/gg()
 * - H1a: 锁定深/浅色 hook UiStateManager.u()（910=bb.p1 / 974=bb.q1）
 * - H1b: 材质极性在 f/ff、b/bb 入口写 l；l/ll 摘 View 减少闪烁
 * - P1': 材质工厂 d/dd(Z) after 单次套用预设
 * - 0.2.974: R8 将单字母方法扩成双字母，查找走 TargetMap.noArg/oneArg
 */
public final class BlurHooks {

    public static final String TAG = "MitypeBlur";
    public static final String TARGET_PKG = "com.xiaomi.type";

    /** 捕获的缓存材质实例（懒加载各构建一次，之后复用同一对象）。 */
    private static volatile Object materialLight;
    private static volatile Object materialDark;
    /** 最近一次成功挂上的 blur 包装（xe.e/cf.e）；clear 时回填避免闪一下底色。 */
    private static volatile Object lastBlurWrap;
    /** b() 成功挂上时的明暗；k() 同极性时整段跳过，避免 clear+重挂闪一下。 */
    private static volatile Boolean lastAppliedPolarity;
    /** f() 重建窗口内：View.setBackgroundColor 改透明，避免纯色底闪一下。 */
    private static final ThreadLocal<Boolean> sInBlurSetup = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> sSkipReapply = new ThreadLocal<>();
    /** setMiuiPhraseThemeFollowInputMethod 上次已注入的 1=浅 / 2=深；相同则跳过，避免发送时主题闪。 */
    private static volatile Integer sLastImeThemeMode;

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
        installGateBypassHook(cl, tm, logFn, configFn);     // GATE 解除启用门禁
        installUiThemeHook(cl, logFn, configFn);            // H1a 锁定深/浅色 → UiStateManager.u()
        installThemeInvokeSkip(logFn, configFn);            // H1c 主题 injector 未变则跳过（防发送闪）
        installSkipRedundantReapply(cl, logFn, configFn);   // H1d 已挂载时跳过 reapply（防发送闪）
        installStateGateHook(cl, tm, logFn, configFn);      // H1b 材质极性 / 防闪烁
        installMaterialCaptureHook(cl, tm, logFn, configFn); // P1' 材质捕获 + 单次参数写入
        installHapticStyleHook(cl, logFn, configFn);        // H4 触感风格重映射
        installStrokeUniformHook(cl, logFn, configFn);      // DEV 描边着色器细参
        installCandidateSoftenHook(cl, logFn, configFn);   // SOFT 候选词蓝光柔化
    }

    /**
     * 0.2.790+：helper 为 bb.x，启用判定在 g()（读字段 e 的 Boolean Flow），
     * 明暗材质变体在字段 l。旧版（bb.u/t/s）仍是 h() 门禁 + d/e/f/k 状态流。
     */
    private static boolean isModernHelper(Class<?> helperCls) {
        try {
            Method g = TargetMap.noArg(helperCls, "g", "gg");
            java.lang.reflect.Field l = helperCls.getDeclaredField("l");
            return g != null && g.getReturnType() == boolean.class && l.getType() == boolean.class;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * GATE: 解除启用门禁，使材质必然被应用。
     * - 旧版：l()/a() 路径上的 h() 检查（0.2.520+）
     * - 0.2.790+：b()/f()/k() 开头的 g() 检查
     * 缺方法时静默跳过。
     */
    private static void installGateBypassHook(final ClassLoader cl, final TargetMap tm,
                                              final LogFn logFn, final ConfigFn configFn) {
        try {
            Class<?> cls = Class.forName(tm.helperClass, false, cl);
            // 0.2.790+：h() 读字段 f，T(h,g,i) 把 h()=true 当强制深色主题 → 白字。
            // 旧版 h() 才是应用门禁；modern 只 hook g()。
            if (!isModernHelper(cls)) {
                Method hGate = TargetMap.noArg(cls, "h", "hh");
                if (hGate != null) {
                    final Method hg = hGate;
                    HookInstaller.hookAfter(hg, new HookInstaller.Interceptor() {
                        @Override
                        public void intercept(HookInstaller.MethodCall call) {
                            if (configFn.get().enable) {
                                call.setResult(Boolean.TRUE);
                            }
                        }
                    });
                    logFn.invoke("GATE h()-bypass installed (" + tm.helperClass + ".h)", null);
                }
            } else {
                logFn.invoke("GATE h() skipped on modern helper (theme force-dark flag)", null);
            }
        } catch (Throwable t) {
            logFn.invoke("GATE h()-bypass skipped (not present in this version)", t);
        }
        try {
            Class<?> cls = Class.forName(tm.helperClass, false, cl);
            Method gGate = TargetMap.noArg(cls, "g", "gg");
            if (gGate != null && gGate.getReturnType() == boolean.class) {
                final Method gg = gGate;
                HookInstaller.hookAfter(gg, new HookInstaller.Interceptor() {
                    @Override
                    public void intercept(HookInstaller.MethodCall call) {
                        if (configFn.get().enable) {
                            call.setResult(Boolean.TRUE);
                        }
                    }
                });
                logFn.invoke("GATE g()-bypass installed (" + tm.helperClass + "." + gg.getName() + ")", null);
            }
        } catch (Throwable t) {
            logFn.invoke("GATE g()-bypass skipped (not present in this version)", t);
        }
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

    // H1a: 0.2.790+ 文字/UI 主题源 —— UiStateManager.u()
    // helper.k 由 IMS 从 u() 拷入；文字色、按键底色都跟 u()。
    // 跟随系统也强制跟系统夜间模式，避免输入法皮肤偏好把文字带成白色。
    private static void installUiThemeHook(ClassLoader cl, LogFn logFn, ConfigFn configFn) {
        try {
            // 0.2.910=bb.p1；0.2.974=bb.q1；方法 u（974 仍单字母）
            Class<?> ui = null;
            for (String n : new String[]{"bb.q1", "bb.p1"}) {
                try {
                    ui = Class.forName(n, false, cl);
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (ui == null) {
                logFn.invoke("H1a UiStateManager class not found", null);
                return;
            }
            Method u = TargetMap.noArg(ui, "u", "uu");
            if (u == null) {
                logFn.invoke("H1a u() not found on " + ui.getName(), null);
                return;
            }
            final Method uu = u;
            HookInstaller.hookAfter(uu, new HookInstaller.Interceptor() {
                @Override
                public void intercept(HookInstaller.MethodCall call) {
                    Config cfg = configFn.get();
                    if (!cfg.enable) return;
                    call.setResult(resolveWantDark(call.getThisObject(), cfg));
                }
            });
            logFn.invoke("H1a UiStateManager.u() <- policy/system-night (" + ui.getName() + ")", null);
        } catch (Throwable t) {
            logFn.invoke("H1a u() hook skipped (not present?)", t);
        }
    }

    /**
     * H1c: 每次发送都会 reapplyHyperMaterialState → T() →
     * InputMethodServiceInjector.setMiuiPhraseThemeFollowInputMethod(1|2)。
     * 主题未变时仍会重设，框架可能闪一下。相同 mode 直接 skip 原 invoke。
     */
    private static void installThemeInvokeSkip(LogFn logFn, ConfigFn configFn) {
        try {
            Method invoke = Method.class.getMethod("invoke",
                    Object.class, Object[].class);
            HookInstaller.hookBefore(invoke, new HookInstaller.Interceptor() {
                @Override
                public void intercept(HookInstaller.MethodCall call) {
                    if (!configFn.get().enable) return;
                    Object host = call.getThisObject();
                    if (!(host instanceof Method)) return;
                    Method m = (Method) host;
                    if (!"setMiuiPhraseThemeFollowInputMethod".equals(m.getName())) return;
                    Object raw = call.getArg(1);
                    if (!(raw instanceof Object[])) return;
                    Object[] args = (Object[]) raw;
                    if (args.length < 1 || !(args[0] instanceof Integer)) return;
                    Integer mode = (Integer) args[0];
                    Integer last = sLastImeThemeMode;
                    if (mode.equals(last)) {
                        call.setResult(null);
                        call.skip();
                        return;
                    }
                    sLastImeThemeMode = mode;
                }
            });
            logFn.invoke("H1c theme-invoke skip when mode unchanged", null);
        } catch (Throwable t) {
            logFn.invoke("H1c theme-invoke skip failed", t);
        }
    }

    /**
     * H1d: 点发送会 reapplyHyperMaterialState → o/f/a/T 整段重做。
     * 模糊 View 已挂在 parent 上时整段 skip，杜绝无意义的 clear/主题/布局闪。
     */
    private static void installSkipRedundantReapply(ClassLoader cl, LogFn logFn, ConfigFn configFn) {
        try {
            Class<?> ims = Class.forName("com.hyperos.inputmethod.MiInputMethodService", false, cl);
            for (Method m : ims.getDeclaredMethods()) {
                String n = m.getName();
                boolean isReapply = "reapplyHyperMaterialState".equals(n)
                        || n.contains("applyHyperMaterialRunnable");
                if (!isReapply || m.getParameterCount() != 1) continue;
                final Method rm = m;
                HookInstaller.hookBefore(rm, new HookInstaller.Interceptor() {
                    @Override
                    public void intercept(HookInstaller.MethodCall call) {
                        if (!configFn.get().enable) return;
                        if (isBlurAttached(call.getThisObject())) {
                            call.setResult(null);
                            call.skip();
                        }
                    }
                });
            }
            logFn.invoke("H1d skip reapply when blur already attached", null);
        } catch (Throwable t) {
            logFn.invoke("H1d skip-reapply failed", t);
        }
    }

    private static boolean isBlurAttached(Object ims) {
        Object helper = ReflectUtil.getObjectField(ims, "hyperMaterialHelper");
        if (helper == null) return false;
        Object view = ReflectUtil.getObjectField(helper, "i");
        if (view == null) return false;
        try {
            if (view.getClass().getMethod("getParent").invoke(view) == null) return false;
            int w = (Integer) view.getClass().getMethod("getWidth").invoke(view);
            int h = (Integer) view.getClass().getMethod("getHeight").invoke(view);
            // 0 尺寸不算「已挂好」，仍允许 reapply 走完整高度落地
            return w > 0 && h > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * HyperChanger 同款：k()/j() 会按云端白名单决定是否保留材质 View。
     * 把当前包强制写入 versions=2 与 dark/light 集合，避免发送/切编辑器时 View 被删再挂（闪）。
     * 兼容 bb.u/t/x/b0 各构建字段漂移（910: t/u/v/w → 974: u/v/w/x）。
     */
    @SuppressWarnings("unchecked")
    private static void forcePackageWhitelist(Object helper) {
        try {
            String pkg = null;
            for (String n : new String[]{"t", "u", "v"}) {
                Object o = ReflectUtil.getObjectField(helper, n);
                if (o instanceof String && !((String) o).isEmpty()) {
                    pkg = (String) o;
                    break;
                }
            }
            if (pkg == null) return;

            // packageMaterialVersions: 第一个 Map 字段
            for (String n : new String[]{"u", "v", "w"}) {
                Object m = ReflectUtil.getObjectField(helper, n);
                if (m instanceof java.util.Map) {
                    java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) m;
                    Object cur = map.get(pkg);
                    if (!(cur instanceof Integer) || ((Integer) cur) > 2) {
                        java.util.Map<Object, Object> copy = new java.util.LinkedHashMap<>(map);
                        copy.put(pkg, 2);
                        ReflectUtil.setObjectField(helper, n, copy);
                    }
                    break;
                }
            }

            // 两个 Set：dark / light。已知对 (v,w) 或 (w,x)
            String setA = null, setB = null;
            for (String n : new String[]{"v", "w", "x", "y"}) {
                if (ReflectUtil.getObjectField(helper, n) instanceof java.util.Set) {
                    if (setA == null) setA = n;
                    else if (setB == null) {
                        setB = n;
                        break;
                    }
                }
            }
            if (setA == null || setB == null) return;
            String darkSet = setA; // v→dark(910) / w→dark(974 当 A=w,B=x)
            String lightSet = setB;
            // 974 第一个 Set 是 w（dark）、第二个 x（light）——与 setA/setB 顺序一致

            boolean dark = ReflectUtil.getBooleanField(helper, "l", false);
            addToSetField(helper, dark ? darkSet : lightSet, pkg, true);
            addToSetField(helper, dark ? lightSet : darkSet, pkg, false);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void addToSetField(Object helper, String name, String pkg, boolean add) {
        Object set = ReflectUtil.getObjectField(helper, name);
        if (!(set instanceof java.util.Set)) return;
        java.util.Set<Object> copy = new java.util.LinkedHashSet<>((java.util.Set<Object>) set);
        if (add) {
            copy.add(pkg);
        } else {
            copy.remove(pkg);
        }
        ReflectUtil.setObjectField(helper, name, copy);
    }

    // H1b: 材质极性 + 防闪烁
    // f() 建 View 时按 l 设背景色，b() 才挂材质 —— 入口与 resolveWantDark 对齐。
    // l() 清材质造成闪烁：enable 时临时摘掉 i 空转。
    private static final ThreadLocal<Object> sLatchedBlurView = new ThreadLocal<>();

    private static void installStateGateHook(final ClassLoader cl, final TargetMap tm,
                                             final LogFn logFn, final ConfigFn configFn) {
        try {
            Class<?> cls = Class.forName(tm.helperClass, false, cl);
            final boolean modern = isModernHelper(cls);
            if (modern) {
                HookInstaller.Interceptor alignPolarity = new HookInstaller.Interceptor() {
                    @Override
                    public void intercept(HookInstaller.MethodCall call) {
                        Object thiz = call.getThisObject();
                        if (thiz == null || !configFn.get().enable) return;
                        ReflectUtil.setBooleanField(thiz, "l",
                                resolveWantDark(thiz, configFn.get()));
                    }
                };
                Method entry = TargetMap.anyArgs(cls, new Class<?>[]{
                        boolean.class, android.widget.FrameLayout.class, int.class},
                        "f", "ff");
                if (entry != null) {
                    final Method fe = entry;
                    HookInstaller.hookBefore(fe, new HookInstaller.Interceptor() {
                        @Override
                        public void intercept(HookInstaller.MethodCall call) {
                            sInBlurSetup.set(Boolean.TRUE);
                            Object thiz = call.getThisObject();
                            if (thiz != null && configFn.get().enable) {
                                ReflectUtil.setBooleanField(thiz, "l",
                                        resolveWantDark(thiz, configFn.get()));
                            }
                        }
                    });
                    HookInstaller.hookAfter(fe, new HookInstaller.Interceptor() {
                        @Override
                        public void intercept(HookInstaller.MethodCall call) {
                            sInBlurSetup.remove();
                        }
                    });
                }
                Method apply = TargetMap.oneArg(cls, android.view.View.class, "b", "bb");
                if (apply != null) {
                    final Method ap = apply;
                    HookInstaller.hookBefore(ap, alignPolarity);
                    HookInstaller.hookAfter(ap, new HookInstaller.Interceptor() {
                        @Override
                        public void intercept(HookInstaller.MethodCall call) {
                            Object thiz = call.getThisObject();
                            if (thiz != null) {
                                lastAppliedPolarity = ReflectUtil.getBooleanField(thiz, "l", false);
                            }
                        }
                    });
                }
                // k()/kk() 同极性重挂跳过 + 白名单
                Method reapply = TargetMap.noArg(cls, "k", "kk");
                if (reapply != null) {
                    final Method kr = reapply;
                    HookInstaller.hookBefore(kr, new HookInstaller.Interceptor() {
                        @Override
                        public void intercept(HookInstaller.MethodCall call) {
                            Object thiz = call.getThisObject();
                            if (thiz == null || !configFn.get().enable) return;
                            forcePackageWhitelist(thiz);
                            boolean l = ReflectUtil.getBooleanField(thiz, "l", false);
                            Object view = ReflectUtil.getObjectField(thiz, "i");
                            if (view != null && lastAppliedPolarity != null
                                    && lastAppliedPolarity == l) {
                                ReflectUtil.setBooleanField(thiz, "o", true);
                                sSkipReapply.set(Boolean.TRUE);
                            }
                        }
                    });
                    HookInstaller.hookAfter(kr, new HookInstaller.Interceptor() {
                        @Override
                        public void intercept(HookInstaller.MethodCall call) {
                            Object thiz = call.getThisObject();
                            if (Boolean.TRUE.equals(sSkipReapply.get()) && thiz != null) {
                                ReflectUtil.setBooleanField(thiz, "o", false);
                            }
                            sSkipReapply.remove();
                        }
                    });
                }
                Method recompute = TargetMap.noArg(cls, "j", "jj");
                if (recompute != null) {
                    final Method jr = recompute;
                    HookInstaller.hookBefore(jr, new HookInstaller.Interceptor() {
                        @Override
                        public void intercept(HookInstaller.MethodCall call) {
                            Object thiz = call.getThisObject();
                            if (thiz == null || !configFn.get().enable) return;
                            forcePackageWhitelist(thiz);
                        }
                    });
                }
                Method cleanup = TargetMap.noArg(cls, "l", "ll");
                if (cleanup != null) {
                    final Method lc = cleanup;
                    HookInstaller.hookBefore(lc, new HookInstaller.Interceptor() {
                        @Override
                        public void intercept(HookInstaller.MethodCall call) {
                            Object thiz = call.getThisObject();
                            if (thiz == null || !configFn.get().enable) return;
                            Object view = ReflectUtil.getObjectField(thiz, "i");
                            sLatchedBlurView.set(view);
                            ReflectUtil.setObjectField(thiz, "i", null);
                        }
                    });
                    HookInstaller.hookAfter(lc, new HookInstaller.Interceptor() {
                        @Override
                        public void intercept(HookInstaller.MethodCall call) {
                            Object thiz = call.getThisObject();
                            Object view = sLatchedBlurView.get();
                            sLatchedBlurView.remove();
                            if (thiz != null && view != null) {
                                ReflectUtil.setObjectField(thiz, "i", view);
                            }
                        }
                    });
                }
                // f() 窗口内新 View：0 尺寸时 INVISIBLE（HyperChanger），避免空窗露底/压暗
                try {
                    Method setBg = android.view.View.class.getDeclaredMethod(
                            "setBackgroundColor", int.class);
                    HookInstaller.hookBefore(setBg, new HookInstaller.Interceptor() {
                        @Override
                        public void intercept(HookInstaller.MethodCall call) {
                            if (!configFn.get().enable) return;
                            if (!Boolean.TRUE.equals(sInBlurSetup.get())) return;
                            Object view = call.getThisObject();
                            // 尚未 layout：先藏起来，等有尺寸再显示
                            try {
                                Integer w = (Integer) view.getClass()
                                        .getMethod("getWidth").invoke(view);
                                Integer h = (Integer) view.getClass()
                                        .getMethod("getHeight").invoke(view);
                                if (w != null && h != null && (w <= 0 || h <= 0)) {
                                    view.getClass().getMethod("setVisibility", int.class)
                                            .invoke(view, Integer.valueOf(4)); // INVISIBLE
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                    logFn.invoke("ANTI-FLASH hide zero-size blur view (HyperChanger)", null);
                } catch (Throwable t) {
                    logFn.invoke("ANTI-FLASH bg hook failed", t);
                }
                logFn.invoke("H1b modern polarity+flash (" + tm.helperClass + ")", null);
                return;
            }
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
        installAntiFlashHooks(cl, tm, logFn, configFn);
    }

    /**
     * 发送/刷新时 k()/l() 会先 clear 材质再重挂，中间一帧只剩底色 → 看起来像压暗。
     * enable 时把 clear(null) 换成再挂一次上次成功的包装；关闭模块时仍允许真清空。
     */
    private static void installAntiFlashHooks(ClassLoader cl, TargetMap tm,
                                              LogFn logFn, ConfigFn configFn) {
        try {
            Class<?> capCls = Class.forName(tm.capabilityClass, false, cl);
            String pkg = tm.blurApiClass.substring(0, tm.blurApiClass.lastIndexOf('.'));
            Class<?> matCls = Class.forName(pkg + ".e", false, cl);
            Method apply = TargetMap.oneArg(capCls, android.view.View.class, "a", "aa");
            if (apply == null) {
                throw new NoSuchMethodException("xe.b.a/aa");
            }
            final Method applyM = apply;
            HookInstaller.hookBefore(applyM, new HookInstaller.Interceptor() {
                @Override
                public void intercept(HookInstaller.MethodCall call) {
                    if (!configFn.get().enable) return;
                    Object mat = call.getArg(1);
                    if (mat != null) {
                        lastBlurWrap = mat;
                        return;
                    }
                    Object sub = lastBlurWrap;
                    if (sub != null) {
                        call.setArg(1, sub);
                    }
                }
            });
            logFn.invoke("ANTI-FLASH material-clear substitute installed (" + applyM.getName() + ")", null);
        } catch (Throwable t) {
            logFn.invoke("ANTI-FLASH material-clear skip failed", t);
        }
        try {
            Class<?> apiCls = Class.forName(tm.blurApiClass, false, cl);
            Method pass = TargetMap.anyArgs(apiCls, new Class<?>[]{
                    android.view.View.class, boolean.class}, "A", "AA");
            if (pass != null) {
                final Method pm = pass;
                HookInstaller.hookBefore(pm, new HookInstaller.Interceptor() {
                    @Override
                    public void intercept(HookInstaller.MethodCall call) {
                        if (!configFn.get().enable) return;
                        if (Boolean.FALSE.equals(call.getArg(1))) {
                            call.setArg(1, Boolean.TRUE);
                        }
                    }
                });
                logFn.invoke("ANTI-FLASH pass-window disable blocked (" + pm.getName() + ")", null);
            }
        } catch (Throwable t) {
            logFn.invoke("ANTI-FLASH pass-window skip failed", t);
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
