package com.mitype.blur.ui.screen.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import top.yukonga.miuix.kmp.basic.Icon
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
fun SettingsScreen(
    prefs: android.content.SharedPreferences?,
    connected: Boolean,
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

    Scaffold(
        topBar = {
            TopAppBar(title = "小爱输入法毛玻璃调节", scrollBehavior = scrollBehavior)
        },
        popupHost = { },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .overScrollVertical()
                .scrollEndHaptic()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            if (!connected || prefs == null) {
                item {
                    Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        Text("⚠ 未连接 LSPosed 服务：请确认模块已启用后重新打开。", fontSize = 14.sp, color = Color(0xFFE65100), modifier = Modifier.padding(12.dp))
                    }
                }
            }

            item {
                Card(Modifier.padding(top = 8.dp).fillMaxWidth()) {
                    Text("ℹ️ 修改后需重启输入法生效", fontSize = 13.sp, color = Color(0xFF1565C0), modifier = Modifier.padding(12.dp))
                }
            }

            item {
                SmallTitle("毛玻璃效果")
                Card(Modifier.fillMaxWidth()) {
                    SwitchPreference(checked = enable, onCheckedChange = { enable = it; save { e -> e.putBoolean(Config.KEY_ENABLE, enable) } }, title = "启用毛玻璃", summary = "关闭后恢复原生外观", startAction = { Icon(Icons.Rounded.Visibility, contentDescription = null) }, enabled = connected)
                    BasicComponent(title = "模糊程度：${when(blurPreset){Config.BLUR_LIGHT->"轻盈";Config.BLUR_HEAVY->"厚重";else->"柔光"}}", summary = when(blurPreset){Config.BLUR_LIGHT->"混色×0.60 更通透";Config.BLUR_HEAVY->"混色×1.18 更浓郁";else->"混色×1.00 原厂观感"}, startAction = { Icon(Icons.Rounded.Info, contentDescription = null) }, onClick = { blurPreset = (blurPreset+1)%3; save { e -> e.putInt(Config.KEY_BLUR_PRESET, blurPreset) } }, enabled = connected)
                    SwitchPreference(checked = bypassCheck, onCheckedChange = { bypassCheck = it; save { e -> e.putBoolean(Config.KEY_BYPASS_VERSION_CHECK, bypassCheck) } }, title = "解除系统版本校验", summary = "强制启用模糊管线", enabled = connected)
                }
            }

            item {
                SmallTitle("背景模式")
                Card(Modifier.fillMaxWidth()) {
                    BasicComponent(title = "材质明暗：${when(materialPolicy){Config.POLICY_FOLLOW_SYSTEM->"跟随系统";Config.POLICY_FORCE_LIGHT->"锁定浅色";else->"锁定深色"}}", startAction = { Icon(Icons.Rounded.DarkMode, contentDescription = null) }, onClick = { materialPolicy = (materialPolicy+1)%3; save { e -> e.putInt(Config.KEY_MATERIAL_POLICY, materialPolicy) } }, enabled = connected)
                }
            }

            if (devMode && connected && prefs != null) {
                item {
                    SmallTitle("细参调节（覆盖预设）")
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            SliderPreference(value = devColor, onValueChange = { devColor = it; save { e -> e.putFloat(Config.KEY_DEV_COLOR_SCALE, devColor) } }, title = "混色浓度 ×%.2f".format(devColor), valueRange = 0.2f..2.0f)
                            SliderPreference(value = devRadii, onValueChange = { devRadii = it; save { e -> e.putFloat(Config.KEY_DEV_RADII_SCALE, devRadii) } }, title = "霜面扩散 ×%.2f".format(devRadii), valueRange = 0.5f..2.0f)
                            SliderPreference(value = devCorner, onValueChange = { devCorner = it; save { e -> e.putFloat(Config.KEY_DEV_CORNER_DP, devCorner) } }, title = "圆角 %.1fdp".format(devCorner), valueText = if (devCorner < 0) "原版" else "%.1f".format(devCorner), valueRange = -1f..40f, steps = 41)
                            SliderPreference(value = devStroke, onValueChange = { devStroke = it; save { e -> e.putFloat(Config.KEY_DEV_STROKE_DP, devStroke) } }, title = "描边 %.1fdp".format(devStroke), valueText = if (devStroke < 0) "原生" else "%.1f".format(devStroke), valueRange = -1f..8f, steps = 9)
                            SliderPreference(value = devGlow, onValueChange = { devGlow = it; save { e -> e.putFloat(Config.KEY_DEV_GLOW_SCALE, devGlow) } }, title = "光晕 ×%.2f".format(devGlow), valueText = if (devGlow < 0) "原生" else "×%.2f".format(devGlow), valueRange = -1f..3f)
                        }
                    }
                }
            }

            item {
                SmallTitle("触感风格")
                Card(Modifier.fillMaxWidth()) {
                    BasicComponent(title = "按键触感：${when(hapticPreset){0->"原生不干预";1->"机械清脆";2->"轻若羽触";3->"厚重踏实";else->"复古键机"}}", startAction = { Icon(Icons.Rounded.Vibration, contentDescription = null) }, onClick = { hapticPreset = (hapticPreset+1)%5; save { e -> e.putInt(Config.KEY_HAPTIC_PRESET, hapticPreset) } }, enabled = connected)
                }
            }

            item {
                SmallTitle("开发者模式")
                Card(Modifier.fillMaxWidth()) {
                    SwitchPreference(checked = devMode, onCheckedChange = { devMode = it; save { e -> e.putBoolean(Config.KEY_DEV_MODE, devMode) } }, title = "启用开发者模式", summary = "细参滑块覆盖预设值", enabled = connected)
                }
            }

            item {
                Card(Modifier.padding(top = 8.dp, bottom = 24.dp).fillMaxWidth()) {
                    BasicComponent(title = "重置为默认值", summary = "柔光 / 锁深色 / 触感原生", onClick = {
                        save { editor ->
                            editor.putBoolean(Config.KEY_ENABLE, true); editor.putInt(Config.KEY_BLUR_PRESET, 1)
                            editor.putInt(Config.KEY_MATERIAL_POLICY, 2); editor.putInt(Config.KEY_HAPTIC_PRESET, 0)
                            editor.putBoolean(Config.KEY_BYPASS_VERSION_CHECK, true); editor.putBoolean(Config.KEY_DEV_MODE, false)
                            editor.remove("frost_alpha"); editor.remove("blur_radius_dp"); editor.remove("corner_radius_dp")
                            editor.remove(Config.KEY_DEV_COLOR_SCALE); editor.remove(Config.KEY_DEV_RADII_SCALE)
                            editor.remove(Config.KEY_DEV_CORNER_DP); editor.remove(Config.KEY_DEV_STROKE_DP); editor.remove(Config.KEY_DEV_GLOW_SCALE)
                        }
                        enable = true; blurPreset = 1; materialPolicy = 2; hapticPreset = 0
                        bypassCheck = true; devMode = false
                        devColor = baseCfg.effColorScale(); devRadii = baseCfg.effRadiiScale()
                        devCorner = -1f; devStroke = -1f; devGlow = -1f
                    }, enabled = connected)
                }
            }
        }
    }
}
