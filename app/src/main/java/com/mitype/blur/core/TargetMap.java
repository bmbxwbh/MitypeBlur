package com.mitype.blur.core;

import java.util.HashMap;
import java.util.Map;

/**
 * 目标版本 → 混淆名映射表（基于 0.2.346 与 0.2.169 双版本逆向交叉验证）。
 * 两版为同一管线的不同 R8 构建，仅 3 处名字不同，其余字段/方法完全同名。
 */
public final class TargetMap {

    public final String helperClass;      // 毛玻璃总控（bb.s / gb.r）
    public final String blurApiClass;     // HyperOS blur API 封装（xe.h / cf.i）
    public final String liveRadiusMethod; // 实时半径方法名（q / s；null = R1 禁用）
    public final String capabilityClass;  // 能力校验类（xe.b / cf.b）：clinit 读 blur 相关系统属性
    public final String capabilityGateMethod; // 能力总闸方法名：无参静态 boolean，全部模糊API指令0统一检查点
    public final String osVersionMethod;  // OS版本方法名：无参静态 int（ro.mi.os.version.code）

    public TargetMap(String helperClass, String blurApiClass, String liveRadiusMethod,
                     String capabilityClass, String capabilityGateMethod, String osVersionMethod) {
        this.helperClass = helperClass;
        this.blurApiClass = blurApiClass;
        this.liveRadiusMethod = liveRadiusMethod;
        this.capabilityClass = capabilityClass;
        this.capabilityGateMethod = capabilityGateMethod;
        this.osVersionMethod = osVersionMethod;
    }

    private static final Map<Long, TargetMap> MAP = new HashMap<>();

    static {
        MAP.put(20346L, new TargetMap("bb.s", "xe.h", "q", "xe.b", "c", "e")); // 0.2.346.fcd599f0
        MAP.put(20169L, new TargetMap("gb.r", "cf.i", "s", "cf.b", "c", "f")); // 0.2.169.d9397d3b (MiType)
    }

    /** 按目标 versionCode 精确匹配。 */
    public static TargetMap get(long versionCode) {
        return MAP.get(versionCode);
    }

    /** 形状校验：确认 helper 类具备本模块依赖的全部成员。 */
    public static boolean shapeOk(Class<?> c) {
        try {
            c.getDeclaredMethod("l");
            c.getDeclaredMethod("d", boolean.class);
            for (String f : new String[]{"d", "e", "f", "k", "x", "y"}) {
                c.getDeclaredField(f);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 形状校验：确认能力类具备无参静态 boolean 总闸（两已知版本同名 "c"）。 */
    public static boolean capShapeOk(Class<?> c) {
        try {
            java.lang.reflect.Method m = c.getDeclaredMethod("c");
            return m.getReturnType() == boolean.class
                    && java.lang.reflect.Modifier.isStatic(m.getModifiers());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 未知版本兜底：逐个候选映射做形状探测，两套都失败返回 null。 */
    public static TargetMap detectByShape(ClassLoader cl) {
        TargetMap[] candidates = {
                new TargetMap("bb.s", "xe.h", "q", "xe.b", "c", "e"),
                new TargetMap("gb.r", "cf.i", "s", "cf.b", "c", "f"),
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
