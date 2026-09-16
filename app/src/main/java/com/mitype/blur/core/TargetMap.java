package com.mitype.blur.core;

import java.util.HashMap;
import java.util.Map;

/**
 * 目标版本 → 混淆名映射表（基于 0.2.346 与 0.2.169 双版本逆向交叉验证）。
 * 两版为同一管线的不同 R8 构建，仅 3 处名字不同，其余字段/方法完全同名。
 */
public final class TargetMap {

    public final String helperClass;      // 毛玻璃总控（bb.s / gb.r / bb.t）
    public final String blurApiClass;     // HyperOS blur API 封装（xe.h / cf.i）
    public final String liveRadiusMethod; // 实时半径方法名（q / s；null = R1 禁用）
    public final String capabilityClass;  // 能力校验类（xe.b / cf.b）：clinit 读 blur 相关系统属性
    public final String capabilityGateMethod; // 能力总闸方法名：无参静态 boolean，全部模糊API指令0统一检查点
    public final String osVersionMethod;  // OS版本方法名：无参静态 int（ro.mi.os.version.code）
    public final String materialFactoryMethod; // 材质工厂方法名：d(Z)/e(Z)，参数 boolean(isDark)

    public TargetMap(String helperClass, String blurApiClass, String liveRadiusMethod,
                     String capabilityClass, String capabilityGateMethod, String osVersionMethod,
                     String materialFactoryMethod) {
        this.helperClass = helperClass;
        this.blurApiClass = blurApiClass;
        this.liveRadiusMethod = liveRadiusMethod;
        this.capabilityClass = capabilityClass;
        this.capabilityGateMethod = capabilityGateMethod;
        this.osVersionMethod = osVersionMethod;
        this.materialFactoryMethod = materialFactoryMethod;
    }

    /** 最新已验证规则（0.2.910.ba19145a）：未知版本默认走这套。 */
    public static final TargetMap LATEST =
            new TargetMap("bb.b0", "xe.h", "q", "xe.b", "c", "e", "d");

    private static final Map<Long, TargetMap> MAP = new HashMap<>();

    static {
        MAP.put(20346L, new TargetMap("bb.s", "xe.h", "q", "xe.b", "c", "e", "d")); // 0.2.346.fcd599f0
        MAP.put(20169L, new TargetMap("gb.r", "cf.i", "s", "cf.b", "c", "f", "d")); // 0.2.169.d9397d3b (MiType)
        MAP.put(20520L, new TargetMap("bb.t", "xe.h", "q", "xe.b", "c", "e", "e")); // 0.2.520.3c8e7df7
        MAP.put(20596L, new TargetMap("bb.u", "xe.h", "q", "xe.b", "c", "e", "e")); // 0.2.596.319bcc61
        MAP.put(20599L, new TargetMap("bb.u", "xe.h", "q", "xe.b", "c", "e", "e")); // 0.2.599.905736fd
        MAP.put(20790L, new TargetMap("bb.x", "xe.h", "q", "xe.b", "c", "e", "d")); // 0.2.790.6ca2b9d4
        MAP.put(20910L, LATEST); // 0.2.910.ba19145a：helper 改名 bb.b0，与 790 同构
    }

    /** versionCode 是否在表内精确收录。 */
    public static boolean isKnown(long versionCode) {
        return MAP.containsKey(versionCode);
    }

    /**
     * 按目标 versionCode 精确匹配；未知版本返回 {@link #LATEST}，不再因版本号未收录而失败。
     * 可能与当前混淆结构不完全一致，调用方可再走 {@link #detectByShape} 校验/择优。
     */
    public static TargetMap get(long versionCode) {
        TargetMap m = MAP.get(versionCode);
        return m != null ? m : LATEST;
    }

    /** 形状校验：确认 helper 类具备本模块依赖的全部成员。 */
    public static boolean shapeOk(Class<?> c) {
        try {
            c.getDeclaredMethod("l");
            for (String f : new String[]{"d", "e", "f", "k"}) {
                c.getDeclaredField(f);
            }
            // 材质工厂方法可能叫 d 或 e；返回类型名形如 zg.e / xxx.e
            boolean hasFactory = false;
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0] == boolean.class
                        && !m.getReturnType().equals(void.class)
                        && !m.getReturnType().equals(boolean.class)
                        && m.getReturnType().getName().endsWith(".e")
                        && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    hasFactory = true;
                    break;
                }
            }
            return hasFactory;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 形状校验：确认能力类具备无参静态 boolean 总闸（已知版本同名 "c"）。 */
    public static boolean capShapeOk(Class<?> c) {
        try {
            java.lang.reflect.Method m = c.getDeclaredMethod("c");
            return m.getReturnType() == boolean.class
                    && java.lang.reflect.Modifier.isStatic(m.getModifiers());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 自动探测材质工厂方法名：无参不行，需单参 boolean 且返回含 ".e;" 的类型。 */
    public static String detectMaterialFactoryMethod(Class<?> helperCls) {
        for (java.lang.reflect.Method m : helperCls.getDeclaredMethods()) {
            if (m.getParameterTypes().length == 1
                    && m.getParameterTypes()[0] == boolean.class
                    && !m.getReturnType().equals(void.class)
                    && !m.getReturnType().equals(boolean.class)
                    && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                return m.getName();
            }
        }
        return "d"; // 兜底默认
    }

    /** 未知版本形状探测：优先匹配已收录候选，失败时由调用方回退 {@link #LATEST}。 */
    public static TargetMap detectByShape(ClassLoader cl) {
        TargetMap[] candidates = {
                LATEST, // bb.b0 @ 0.2.910
                new TargetMap("bb.x", "xe.h", "q", "xe.b", "c", "e", "d"), // 0.2.790
                new TargetMap("bb.u", "xe.h", "q", "xe.b", "c", "e", "e"),
                new TargetMap("bb.s", "xe.h", "q", "xe.b", "c", "e", "d"),
                new TargetMap("bb.t", "xe.h", "q", "xe.b", "c", "e", "e"),
                new TargetMap("gb.r", "cf.i", "s", "cf.b", "c", "f", "d"),
        };
        for (TargetMap m : candidates) {
            try {
                Class<?> c = Class.forName(m.helperClass, false, cl);
                if (shapeOk(c)) return m;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
