package com.mitype.blur.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.mitype.blur.ui.UiMode
import com.mitype.blur.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val uiMode: UiMode,
)
