package com.mitype.blur.ui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mitype.blur.core.Config
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class MainActivity : ComponentActivity() {

    private var remotePrefs by mutableStateOf<SharedPreferences?>(null)
    private var connected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                runOnUiThread {
                    remotePrefs = service.getRemotePreferences(Config.PREFS_NAME)
                    connected = true
                }
            }

            override fun onServiceDied(service: XposedService) {
                runOnUiThread { connected = false }
            }
        })

        setContent {
            SettingsScreen(prefs = remotePrefs, connected = connected)
        }
    }
}
