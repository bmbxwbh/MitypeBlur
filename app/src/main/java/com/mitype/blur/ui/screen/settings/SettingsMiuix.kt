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
import androidx.compose.ui.unit.Dp
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

private var sPrefs by mutableStateOf<android.content.SharedPreferences?>(null)
private var sConnected by mutableStateOf(false)

@Composable
fun SettingPagerMiuix(
    bottomInnerPadding: Dp,
    onNavigateBack: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    var enable by remember { mutableStateOf(Config.load(sPrefs).enable) }
    var blurPreset by remember { mutableIntStateOf(Config.load(sPrefs).blurPreset) }
    var materialPolicy by remember { mutableIntStateOf(Config.load(sPrefs).materialPolicy) }
    var hapticPreset by remember { mutableIntStateOf(Config.load(sPrefs).hapticPreset) }
    var bypassCheck by remember { mutableStateOf(Config.load(sPrefs).bypassVersionCheck) }
    var devMode by remember { mutableStateOf(Config.load(sPrefs).devMode) }
    val baseCfg = remember { Config.load(sPrefs) }
    var devColor by remember { mutableFloatStateOf(if (Config.load(sPrefs).devColorScale > 0) Config.load(sPrefs).devColorScale else baseCfg.effColorScale()) }
    var devRadii by remember { mutableFloatStateOf(if (Config.load(sPrefs).devRadiiScale > 0) Config.load(sPrefs).devRadiiScale else baseCfg.effRadiiScale()) }
    var devCorner by remember { mutableFloatStateOf(Config.load(sPrefs).devCornerDp) }
    var devStroke by remember { mutableFloatStateOf(Config.load(sPrefs).devStrokeDp) }
    var devGlow by remember { mutableFloatStateOf(Config.load(sPrefs).devGlowScale) }

    fun save(block: (android.content.SharedPreferences.Editor) -> Unit) {
        sPrefs?.edit()?.apply(block)?.apply()
    }

    Scaffold(
        topBar = { TopAppBar(title = "参数调节", scrollBehavior = scrollBehavior) },
        popupHost = { },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.overScrollVertical().scrollEndHaptic()
                .nestedScroll(scrollBehavior.nestedScrollConnection).padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            if (sConnected && sPrefs != null) {
                item { Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    Text("ℹ️ 修改后需重启输入法生效", fontSize = 13.sp, color = Color(0xFF1565C0), modifier = Modifier.padding(12.dp))
                }}
            }

            item { SmallTitle("毛玻璃效果"); Card(Modifier.fillMaxWidth()) {
                SwitchPreference(checked = enable, onCheckedChange = { enable = it; save { e -> e.putBoolean(Config.KEY_ENABLE, enable) } }, title = "启用毛玻璃", summary = "关闭后恢复原生外观", enabled = true)
                BasicComponent(title = "模糊程度：${when(blurPreset){0->"轻盈";2->"厚重";else->"柔光"}}", summary = when(blurPreset){0->"混色×0.60 更通透";2->"混色×1.18 更浓郁";else->"混色×1.00 原厂观感"}, onClick = { blurPreset = (blurPreset+1)%3; save { e -> e.putInt(Config.KEY_BLUR_PRESET, blurPreset) } })
                SwitchPreference(checked = bypassCheck, onCheckedChange = { bypassCheck = it; save { e -> e.putBoolean(Config.KEY_BYPASS_VERSION_CHECK, bypassCheck) } }, title = "解除系统版本校验", summary = "强制启用模糊管线")
            }}
            item { SmallTitle("背景模式"); Card(Modifier.fillMaxWidth()) {
                BasicComponent(title = "材质明暗：${when(materialPolicy){0->"跟随系统";1->"锁定浅色";else->"锁定深色"}}", onClick = { materialPolicy = (materialPolicy+1)%3; save { e -> e.putInt(Config.KEY_MATERIAL_POLICY, materialPolicy) } })
            }}
            if (devMode) { item { SmallTitle("细参调节（覆盖预设）"); Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(vertical=6.dp)) {
                SliderPreference(value=devColor,onValueChange={devColor=it;save{e->e.putFloat(Config.KEY_DEV_COLOR_SCALE,it)}},title="混色浓度 ×%.2f".format(devColor),valueRange=0.2f..2.0f)
                SliderPreference(value=devRadii,onValueChange={devRadii=it;save{e->e.putFloat(Config.KEY_DEV_RADII_SCALE,it)}},title="霜面扩散 ×%.2f".format(devRadii),valueRange=0.5f..2.0f)
                SliderPreference(value=devCorner,onValueChange={devCorner=it;save{e->e.putFloat(Config.KEY_DEV_CORNER_DP,it)}},title="圆角 %.1fdp".format(devCorner),valueRange=-1f..40f,steps=41)
                SliderPreference(value=devStroke,onValueChange={devStroke=it;save{e->e.putFloat(Config.KEY_DEV_STROKE_DP,it)}},title="描边 %.1fdp".format(devStroke),valueRange=-1f..8f,steps=9)
                SliderPreference(value=devGlow,onValueChange={devGlow=it;save{e->e.putFloat(Config.KEY_DEV_GLOW_SCALE,it)}},title="光晕 ×%.2f".format(devGlow),valueRange=-1f..3f)
            }}}}
            item { SmallTitle("触感风格"); Card(Modifier.fillMaxWidth()) {
                BasicComponent(title="按键触感：${when(hapticPreset){0->"原生";1->"机械清脆";2->"轻若羽触";3->"厚重踏实";else->"复古键机"}}", onClick={hapticPreset=(hapticPreset+1)%5;save{e->e.putInt(Config.KEY_HAPTIC_PRESET,hapticPreset)}})
            }}
            item { SmallTitle("开发者模式"); Card(Modifier.fillMaxWidth()) {
                SwitchPreference(checked=devMode,onCheckedChange={devMode=it;save{e->e.putBoolean(Config.KEY_DEV_MODE,devMode)}},title="启用开发者模式",summary="细参滑块覆盖预设值")
            }}
            item { Card(Modifier.padding(top=8.dp,bottom=24.dp).fillMaxWidth()) {
                BasicComponent(title="重置为默认值",onClick={
                    save{e->e.putBoolean(Config.KEY_ENABLE,true);e.putInt(Config.KEY_BLUR_PRESET,1);e.putInt(Config.KEY_MATERIAL_POLICY,2);e.putInt(Config.KEY_HAPTIC_PRESET,0);e.putBoolean(Config.KEY_BYPASS_VERSION_CHECK,true);e.putBoolean(Config.KEY_DEV_MODE,false)}
                    enable=true;blurPreset=1;materialPolicy=2;hapticPreset=0;bypassCheck=true;devMode=false;devColor=1.0f;devRadii=1.0f;devCorner=-1f;devStroke=-1f;devGlow=-1f
                }, enabled=true)
            }}
            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}
