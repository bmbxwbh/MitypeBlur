package com.mitype.blur.ui.screen.params

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitype.blur.core.Config
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun BlurParamsPagerMiuix(
    prefs: android.content.SharedPreferences?,
    connected: Boolean,
    bottomInnerPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val dark = isSystemInDarkTheme()
    val scrollBehavior = MiuixScrollBehavior()

    var enable by remember(prefs) { mutableStateOf(prefs?.getBoolean(Config.KEY_ENABLE, Config.DEFAULT_ENABLE) ?: Config.DEFAULT_ENABLE) }
    var blurPreset by remember(prefs) { mutableIntStateOf(prefs?.getInt(Config.KEY_BLUR_PRESET, Config.DEFAULT_BLUR_PRESET) ?: Config.DEFAULT_BLUR_PRESET) }
    var materialPolicy by remember(prefs) { mutableIntStateOf(prefs?.getInt(Config.KEY_MATERIAL_POLICY, Config.DEFAULT_MATERIAL_POLICY) ?: Config.DEFAULT_MATERIAL_POLICY) }
    var hapticPreset by remember(prefs) { mutableIntStateOf(prefs?.getInt(Config.KEY_HAPTIC_PRESET, Config.DEFAULT_HAPTIC_PRESET) ?: Config.DEFAULT_HAPTIC_PRESET) }
    var bypassCheck by remember(prefs) { mutableStateOf(prefs?.getBoolean(Config.KEY_BYPASS_VERSION_CHECK, Config.DEFAULT_BYPASS_VERSION_CHECK) ?: Config.DEFAULT_BYPASS_VERSION_CHECK) }
    var devMode by remember(prefs) { mutableStateOf(prefs?.getBoolean(Config.KEY_DEV_MODE, false) ?: false) }
    val baseCfg = remember(prefs) { Config.load(prefs) }
    var devColor by remember(prefs) { mutableFloatStateOf(prefs?.getFloat(Config.KEY_DEV_COLOR_SCALE, -1f)?.takeIf { it > 0 } ?: baseCfg.effColorScale()) }
    var devRadii by remember(prefs) { mutableFloatStateOf(prefs?.getFloat(Config.KEY_DEV_RADII_SCALE, -1f)?.takeIf { it > 0 } ?: baseCfg.effRadiiScale()) }
    var devCorner by remember(prefs) { mutableFloatStateOf(prefs?.getFloat(Config.KEY_DEV_CORNER_DP, -1f) ?: -1f) }
    var devStroke by remember(prefs) { mutableFloatStateOf(prefs?.getFloat(Config.KEY_DEV_STROKE_DP, -1f) ?: -1f) }
    var devGlow by remember(prefs) { mutableFloatStateOf(prefs?.getFloat(Config.KEY_DEV_GLOW_SCALE, -1f) ?: -1f) }

    fun save(block: (android.content.SharedPreferences.Editor) -> Unit) {
        prefs?.edit()?.apply(block)?.apply()
    }

    fun presetText(v: Int) = when (v) {
        Config.BLUR_LIGHT -> "轻盈"
        Config.BLUR_HEAVY -> "厚重"
        else -> "柔光"
    }
    fun presetDesc(v: Int) = when (v) {
        Config.BLUR_LIGHT -> "混色×0.60 · 更通透"
        Config.BLUR_HEAVY -> "混色×1.18 · 更浓郁"
        else -> "混色×1.00 · 原厂观感"
    }
    fun policyText(v: Int) = when (v) {
        Config.POLICY_FOLLOW_SYSTEM -> "跟随系统"
        Config.POLICY_FORCE_LIGHT -> "锁定浅色"
        else -> "锁定深色"
    }
    fun hapticText(v: Int) = when (v) {
        0 -> "原生"; 1 -> "机械清脆"; 2 -> "轻若羽触"; 3 -> "厚重踏实"; else -> "复古键机"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "参数调节",
                scrollBehavior = scrollBehavior
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars
                .add(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .overScrollVertical()
                .scrollEndHaptic()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            // ── 重启提示 ──
            if (connected && prefs != null) {
                item {
                    Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        Text(
                            text = "ℹ️ 修改参数后需重启输入法：设置→应用管理→小爱输入法→强制停止",
                            fontSize = 13.sp,
                            color = Color(0xFF1565C0),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                item {
                    Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        Text(
                            text = "⚠ 未连接 LSPosed 服务",
                            fontSize = 14.sp,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // ── 毛玻璃效果 ──
            item {
                SmallTitle(text = "毛玻璃效果")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        checked = enable,
                        onCheckedChange = { enable = it; save { s -> s.putBoolean(Config.KEY_ENABLE, it) } },
                        title = "启用毛玻璃",
                        summary = "关闭后恢复输入法原生外观",
                        startAction = { Icon(Icons.Rounded.Visibility, contentDescription = null) },
                        enabled = connected
                    )
                    BasicComponent(
                        title = "模糊程度：${presetText(blurPreset)}",
                        summary = presetDesc(blurPreset),
                        startAction = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            val next = (blurPreset + 1) % Config.BLUR_PRESET_COUNT
                            blurPreset = next; save { s -> s.putInt(Config.KEY_BLUR_PRESET, next) }
                        },
                        enabled = connected
                    )
                    SwitchPreference(
                        checked = bypassCheck,
                        onCheckedChange = { bypassCheck = it; save { s -> s.putBoolean(Config.KEY_BYPASS_VERSION_CHECK, it) } },
                        title = "解除系统版本校验",
                        summary = "在旗标关闭但框架支持的设备上强制启用模糊管线",
                        enabled = connected
                    )
                }
            }

            // ── 背景模式 ──
            item {
                SmallTitle(text = "背景模式")
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "材质明暗：${policyText(materialPolicy)}",
                        summary = when (materialPolicy) {
                            Config.POLICY_FOLLOW_SYSTEM -> "跟随系统昼夜自动反转"
                            Config.POLICY_FORCE_LIGHT -> "浅霜底 + 黑字"
                            else -> "深霜底 + 白字"
                        },
                        startAction = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
                        onClick = {
                            val next = (materialPolicy + 1) % 3
                            materialPolicy = next; save { s -> s.putInt(Config.KEY_MATERIAL_POLICY, next) }
                        },
                        enabled = connected
                    )
                }
            }

            // ── 触感风格 ──
            item {
                SmallTitle(text = "触感风格")
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "按键触感：${hapticText(hapticPreset)}",
                        summary = when (hapticPreset) {
                            0 -> "点击切换预设；原生为不干预"
                            1 -> "TAP_NORMAL / 长按渐强 / 轻提示"
                            2 -> "全轻触波形，细腻安静"
                            3 -> "网格重 + 持握长按，厚实"
                            else -> "开关拨动 + 齿轮档位，复古"
                        },
                        startAction = { Icon(Icons.Rounded.Vibration, contentDescription = null) },
                        onClick = {
                            val next = (hapticPreset + 1) % 5
                            hapticPreset = next; save { s -> s.putInt(Config.KEY_HAPTIC_PRESET, next) }
                        },
                        enabled = connected
                    )
                }
            }

            // ── 开发者模式 ──
            item {
                SmallTitle(text = "开发者模式")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        checked = devMode,
                        onCheckedChange = { devMode = it; save { s -> s.putBoolean(Config.KEY_DEV_MODE, it) } },
                        title = "启用开发者模式",
                        summary = "细参滑块覆盖预设值；改动需重启输入法生效",
                        startAction = { Icon(Icons.Rounded.Build, contentDescription = null) },
                        enabled = connected
                    )
                }
            }
            if (devMode && connected && prefs != null) {
                item {
                    SmallTitle(text = "细参调节（覆盖预设）")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            SliderPreference(
                                value = devColor,
                                onValueChange = { devColor = it; save { s -> s.putFloat(Config.KEY_DEV_COLOR_SCALE, it) } },
                                title = "混色浓度 ×%.2f".format(devColor),
                                summary = "霜白三层 alpha 等比缩放",
                                valueRange = 0.2f..2.0f,
                            )
                            SliderPreference(
                                value = devRadii,
                                onValueChange = { devRadii = it; save { s -> s.putFloat(Config.KEY_DEV_RADII_SCALE, it) } },
                                title = "霜面扩散 ×%.2f".format(devRadii),
                                summary = "混色层衰减半径等比缩放",
                                valueRange = 0.5f..2.0f,
                            )
                            SliderPreference(
                                value = devCorner,
                                onValueChange = { devCorner = it; save { s -> s.putFloat(Config.KEY_DEV_CORNER_DP, it) } },
                                title = "圆角半径 %.1fdp".format(devCorner),
                                summary = "-1 跟随原版形状",
                                valueText = if (devCorner < 0) "原版" else "%.1f".format(devCorner),
                                valueRange = -1f..40f,
                                steps = 41,
                            )
                            SliderPreference(
                                value = devStroke,
                                onValueChange = { devStroke = it; save { s -> s.putFloat(Config.KEY_DEV_STROKE_DP, it) } },
                                title = "描边宽度 %.1fdp".format(devStroke),
                                summary = "-1 原生 1.5dp",
                                valueText = if (devStroke < 0) "原生" else "%.1f".format(devStroke),
                                valueRange = -1f..8f,
                                steps = 9,
                            )
                            SliderPreference(
                                value = devGlow,
                                onValueChange = { devGlow = it; save { s -> s.putFloat(Config.KEY_DEV_GLOW_SCALE, it) } },
                                title = "光晕强度 ×%.2f".format(devGlow),
                                summary = "-1 原生；作用于描边 alpha 与光晕宽度",
                                valueRange = -1f..3f,
                            )
                        }
                    }
                }
            }

            // ── 重置 ──
            item {
                Card(modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).fillMaxWidth()) {
                    BasicComponent(
                        title = "重置为默认值",
                        summary = "柔光磨砂 / 材质锁深色 / 触感原生",
                        onClick = {
                            save { editor ->
                                editor.putBoolean(Config.KEY_ENABLE, Config.DEFAULT_ENABLE)
                                editor.putInt(Config.KEY_BLUR_PRESET, Config.DEFAULT_BLUR_PRESET)
                                editor.putInt(Config.KEY_MATERIAL_POLICY, Config.DEFAULT_MATERIAL_POLICY)
                                editor.putInt(Config.KEY_HAPTIC_PRESET, Config.DEFAULT_HAPTIC_PRESET)
                                editor.putBoolean(Config.KEY_BYPASS_VERSION_CHECK, Config.DEFAULT_BYPASS_VERSION_CHECK)
                                editor.putBoolean(Config.KEY_DEV_MODE, false)
                                editor.remove("frost_alpha"); editor.remove("blur_radius_dp")
                                editor.remove("corner_radius_dp")
                                editor.remove(Config.KEY_DEV_COLOR_SCALE)
                                editor.remove(Config.KEY_DEV_RADII_SCALE)
                                editor.remove(Config.KEY_DEV_CORNER_DP)
                                editor.remove(Config.KEY_DEV_STROKE_DP)
                                editor.remove(Config.KEY_DEV_GLOW_SCALE)
                            }
                            enable = Config.DEFAULT_ENABLE
                            blurPreset = Config.DEFAULT_BLUR_PRESET
                            materialPolicy = Config.DEFAULT_MATERIAL_POLICY
                            hapticPreset = Config.DEFAULT_HAPTIC_PRESET
                            bypassCheck = Config.DEFAULT_BYPASS_VERSION_CHECK
                            devMode = false
                            devColor = baseCfg.effColorScale()
                            devRadii = baseCfg.effRadiiScale()
                            devCorner = -1f; devStroke = -1f; devGlow = -1f
                        },
                        enabled = connected
                    )
                }
                Spacer(Modifier.height(bottomInnerPadding))
            }
        }
    }
}
