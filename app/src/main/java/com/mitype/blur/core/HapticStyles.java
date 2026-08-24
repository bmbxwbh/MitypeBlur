package com.mitype.blur.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 触感风格预设：把 t9.b.a(枚举) 返回的 miuix 触感常量按"事件→风格"重映射。
 * 常量为 miuix/view/h 的语义马达波形值（sys.haptic.version>=1.0 合法区间）。
 */
public final class HapticStyles {

    /** miuix/view/h 波形常量。 */
    public static final int VIRTUAL_RELEASE = 0x10000000;
    public static final int TAP_NORMAL = 0x10000001;
    public static final int TAP_LIGHT = 0x10000002;
    public static final int FLICK = 0x10000003;
    public static final int SWITCH = 0x10000004;
    public static final int MESH_HEAVY = 0x10000005;
    public static final int MESH_NORMAL = 0x10000006;
    public static final int MESH_LIGHT = 0x10000007;
    public static final int LONG_PRESS = 0x10000008;
    public static final int HOLD = 0x1000000f;
    public static final int BUTTON_LARGE = 0x10000012;
    public static final int BUTTON_MIDDLE = 0x10000013;
    public static final int BUTTON_SMALL = 0x10000014;
    public static final int GEAR_LIGHT = 0x10000015;
    public static final int GEAR_HEAVY = 0x10000016;
    public static final int KEYBOARD = 0x10000017;
    public static final int ALERT = 0x10000018;

    /** 预设 id：与 Config.hapticPreset 对应（1~4）。 */
    public static final int PRESET_CRISP = 1;      // 机械清脆
    public static final int PRESET_FEATHER = 2;    // 轻若羽触
    public static final int PRESET_SOLID = 3;      // 厚重踏实
    public static final int PRESET_RETRO = 4;      // 复古键机

    private static final Map<Integer, Map<String, Integer>> PRESETS = new HashMap<>();

    static {
        // 机械清脆
        Map<String, Integer> crisp = new HashMap<>();
        crisp.put("KEY_PRESS", TAP_NORMAL);
        crisp.put("LONG_PRESS", LONG_PRESS);
        crisp.put("ERROR", TAP_LIGHT);
        crisp.put("SUCCESS", TAP_LIGHT);
        crisp.put("BUTTON_LARGE", BUTTON_MIDDLE);
        PRESETS.put(PRESET_CRISP, crisp);

        // 轻若羽触
        Map<String, Integer> feather = new HashMap<>();
        feather.put("KEY_PRESS", TAP_LIGHT);
        feather.put("LONG_PRESS", FLICK);
        feather.put("ERROR", TAP_LIGHT);
        feather.put("SUCCESS", TAP_LIGHT);
        feather.put("BUTTON_LARGE", BUTTON_SMALL);
        PRESETS.put(PRESET_FEATHER, feather);

        // 厚重踏实
        Map<String, Integer> solid = new HashMap<>();
        solid.put("KEY_PRESS", MESH_HEAVY);
        solid.put("LONG_PRESS", HOLD);
        solid.put("ERROR", ALERT);
        solid.put("SUCCESS", MESH_NORMAL);
        solid.put("BUTTON_LARGE", BUTTON_LARGE);
        PRESETS.put(PRESET_SOLID, solid);

        // 复古键机
        Map<String, Integer> retro = new HashMap<>();
        retro.put("KEY_PRESS", SWITCH);
        retro.put("LONG_PRESS", MESH_HEAVY);
        retro.put("ERROR", GEAR_HEAVY);
        retro.put("SUCCESS", SWITCH);
        retro.put("BUTTON_LARGE", GEAR_HEAVY);
        PRESETS.put(PRESET_RETRO, retro);
    }

    /** 查询某预设下事件应替换的风格；无映射返回 null（保留原生）。 */
    public static Integer styleFor(int preset, String eventName) {
        if (preset <= HapticStylesHolder.NATIVE) return null;
        Map<String, Integer> m = PRESETS.get(preset);
        if (m == null) return null;
        return m.get(eventName);
    }

    public static Map<Integer, Map<String, Integer>> all() {
        return Collections.unmodifiableMap(PRESETS);
    }

    private static final class HapticStylesHolder {
        static final int NATIVE = Config.HAPTIC_NATIVE;
    }

    private HapticStyles() {
    }
}
