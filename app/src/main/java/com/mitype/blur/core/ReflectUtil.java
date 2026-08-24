package com.mitype.blur.core;

import java.lang.reflect.Field;

/** 全部防御式反射：目标混淆名漂移时静默降级，绝不抛异常。 */
public final class ReflectUtil {

    private ReflectUtil() {
    }

    public static void setFloatField(Object obj, String name, float v) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setFloat(obj, v);
        } catch (Throwable ignored) {
        }
    }

    /** 读对象引用字段，失败返回 null。 */
    public static Object getObjectField(Object obj, String name) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 读 boolean 字段，失败返回 def。 */
    public static boolean getBooleanField(Object obj, String name, boolean def) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.getBoolean(obj);
        } catch (Throwable ignored) {
            return def;
        }
    }

    /** 写 boolean 字段。 */
    public static void setBooleanField(Object obj, String name, boolean v) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(obj, v);
        } catch (Throwable ignored) {
        }
    }

    public static void setFlowValue(Object host, String fieldName, Object value) {
        try {
            Field f = host.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object flow = f.get(host);
            if (flow == null) return;
            flow.getClass().getMethod("setValue", Object.class).invoke(flow, value);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 等比缩放 int[] 颜色数组的 alpha 字节（RGB 保持原样），单字节上限 0xFA；
     * scale≈1 时零写入。深浅两套配色的层次关系与色相在缩放下完整保留。
     */
    public static void scaleAlphas(Object cfg, String fieldName, float scale) {
        try {
            Field f = cfg.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            int[] arr = (int[]) f.get(cfg);
            if (arr == null || arr.length == 0) return;
            boolean changed = false;
            for (int i = 0; i < arr.length; i++) {
                int cur = (arr[i] >>> 24) & 0xFF;
                int nxt = Math.min(0xFA, Math.round(cur * scale));
                if (nxt == cur) continue;
                arr[i] = (arr[i] & 0x00FFFFFF) | (nxt << 24);
                changed = true;
            }
            if (changed) f.set(cfg, arr);
        } catch (Throwable ignored) {
        }
    }
}
