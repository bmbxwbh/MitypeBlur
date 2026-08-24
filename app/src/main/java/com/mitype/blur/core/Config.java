package com.mitype.blur.core;

import android.content.SharedPreferences;

/**
 * 配置契约（UI 端经 XposedServiceHelper 写入 Remote Preferences，key 一一对应）：
 * enable:boolean(true) / blur_preset:int(0=轻盈,1=柔光,2=厚重)
 * material_policy:int(2=锁深色) / haptic_preset:int(0=原生) / bypass_version_check:boolean(true)
 * 开发者模式（覆盖预设的细参，-1/关闭=跟随预设）：
 * dev_mode:boolean(false) / dev_color_scale:f(-1) / dev_radii_scale:f(-1)
 * dev_corner_dp:f(-1) / dev_stroke_dp:f(-1) / dev_glow_scale:f(-1)
 */
public final class Config {

    public static final String PREFS_NAME = "xiaoai_blur_prefs";

    public static final String KEY_ENABLE = "enable";
    public static final String KEY_BLUR_PRESET = "blur_preset";
    /** 遗留键：旧版霜白滑杆，重置时必须显式移除。 */
    public static final String LEGACY_KEY_FROST_ALPHA = "frost_alpha";
    public static final String KEY_MATERIAL_POLICY = "material_policy";
    public static final String KEY_HAPTIC_PRESET = "haptic_preset";
    public static final String KEY_BYPASS_VERSION_CHECK = "bypass_version_check";

    /** 开发者模式：总开关 + 细参覆盖（值 <0/=false 表示跟随预设或不干预）。 */
    public static final String KEY_DEV_MODE = "dev_mode";
    public static final String KEY_DEV_COLOR_SCALE = "dev_color_scale";
    public static final String KEY_DEV_RADII_SCALE = "dev_radii_scale";
    public static final String KEY_DEV_CORNER_DP = "dev_corner_dp";
    public static final String KEY_DEV_STROKE_DP = "dev_stroke_dp";
    public static final String KEY_DEV_GLOW_SCALE = "dev_glow_scale";

    /** 遗留键：v1.6 及之前版本的滑杆自由值，升级/重置时必须显式移除，否则钩子侧读到脏配置。 */
    public static final String LEGACY_KEY_BLUR_RADIUS_DP = "blur_radius_dp";
    public static final String LEGACY_KEY_CORNER_RADIUS_DP = "corner_radius_dp";

    /** 材质明暗策略值。 */
    public static final int POLICY_FOLLOW_SYSTEM = 0;
    public static final int POLICY_FORCE_LIGHT = 1;
    public static final int POLICY_FORCE_DARK = 2;

    /** 触感风格预设：0=原生不干预，1~4 见 HapticStyles。 */
    public static final int HAPTIC_NATIVE = 0;

    /** 磨砂质感预设：轻盈=更透，柔光=原厂观感（默认，钩子侧零写入），厚重=更浓郁。
     *  实现为对出厂混色三层 alpha 的等比缩放；几何参数（J/f[]）永不改动，
     *  从结构上杜绝「调预设变实心/变近黑」的问题。 */
    public static final int BLUR_LIGHT = 0;
    public static final int BLUR_SOFT = 1;
    public static final int BLUR_HEAVY = 2;
    public static final int BLUR_PRESET_COUNT = 3;
    public static final float[] PRESET_ALPHA_SCALE = {0.60f, 1.00f, 1.18f};
    /** 霜面扩散系数预设（对出厂 f[] 三层衰减半径等比缩放；柔光=1.00 出厂零改动）。 */
    public static final float[] PRESET_RADII_SCALE = {0.90f, 1.00f, 1.10f};

    public static final boolean DEFAULT_DEV_MODE = false;
    public static final float DEFAULT_DEV_OVERRIDE = -1f;

    public static final boolean DEFAULT_ENABLE = true;
    public static final int DEFAULT_BLUR_PRESET = BLUR_SOFT;
    public static final int DEFAULT_MATERIAL_POLICY = POLICY_FORCE_DARK;
    public static final int DEFAULT_HAPTIC_PRESET = HAPTIC_NATIVE;
    public static final boolean DEFAULT_BYPASS_VERSION_CHECK = true;

    public boolean enable = DEFAULT_ENABLE;
    public int blurPreset = DEFAULT_BLUR_PRESET;
    public int materialPolicy = DEFAULT_MATERIAL_POLICY;
    public int hapticPreset = DEFAULT_HAPTIC_PRESET;
    public boolean bypassVersionCheck = DEFAULT_BYPASS_VERSION_CHECK;

    public boolean devMode = DEFAULT_DEV_MODE;
    public float devColorScale = DEFAULT_DEV_OVERRIDE;
    public float devRadiiScale = DEFAULT_DEV_OVERRIDE;
    public float devCornerDp = DEFAULT_DEV_OVERRIDE;
    public float devStrokeDp = DEFAULT_DEV_OVERRIDE;
    public float devGlowScale = DEFAULT_DEV_OVERRIDE;

    public Config() {}

    public static Config load(SharedPreferences sp) {
        Config c = new Config();
        if (sp == null) return c;
        try {
            c.enable = sp.getBoolean(KEY_ENABLE, DEFAULT_ENABLE);
            c.blurPreset = clampPreset(sp.getInt(KEY_BLUR_PRESET, DEFAULT_BLUR_PRESET));
            c.materialPolicy = sp.getInt(KEY_MATERIAL_POLICY, DEFAULT_MATERIAL_POLICY);
            c.hapticPreset = sp.getInt(KEY_HAPTIC_PRESET, DEFAULT_HAPTIC_PRESET);
            c.bypassVersionCheck = sp.getBoolean(KEY_BYPASS_VERSION_CHECK, DEFAULT_BYPASS_VERSION_CHECK);
            c.devMode = sp.getBoolean(KEY_DEV_MODE, DEFAULT_DEV_MODE);
            c.devColorScale = sp.getFloat(KEY_DEV_COLOR_SCALE, DEFAULT_DEV_OVERRIDE);
            c.devRadiiScale = sp.getFloat(KEY_DEV_RADII_SCALE, DEFAULT_DEV_OVERRIDE);
            c.devCornerDp = sp.getFloat(KEY_DEV_CORNER_DP, DEFAULT_DEV_OVERRIDE);
            c.devStrokeDp = sp.getFloat(KEY_DEV_STROKE_DP, DEFAULT_DEV_OVERRIDE);
            c.devGlowScale = sp.getFloat(KEY_DEV_GLOW_SCALE, DEFAULT_DEV_OVERRIDE);
        } catch (Throwable ignored) {
        }
        return c;
    }

    private static int clampPreset(int v) {
        return (v < BLUR_LIGHT || v > BLUR_HEAVY) ? DEFAULT_BLUR_PRESET : v;
    }

    /** 当前预设的混色 alpha 缩放系数；柔光档=1.0（钩子侧零写入）。 */
    public float presetAlphaScale() {
        return PRESET_ALPHA_SCALE[clampPreset(blurPreset)];
    }

    /** 当前预设的霜面扩散系数（f[] 等比）；柔光档=1.0。 */
    public float presetRadiiScale() {
        return PRESET_RADII_SCALE[clampPreset(blurPreset)];
    }

    /** 开发者覆盖优先，否则回落预设。≤0 视为未设置。 */
    public float effColorScale() {
        return devColorScale > 0f ? devColorScale : presetAlphaScale();
    }

    public float effRadiiScale() {
        return devRadiiScale > 0f ? devRadiiScale : presetRadiiScale();
    }

    public static Config defaults() {
        return new Config();
    }
}
