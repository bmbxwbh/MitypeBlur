package com.mitype.blur.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitype.blur.ui.util.LatestVersionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            checkUpdateEnabled = false,
            latestVersionInfo = LatestVersionInfo(),
            currentAppVersionCode = 0,
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState
}
