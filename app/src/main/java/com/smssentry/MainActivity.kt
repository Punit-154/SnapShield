package com.smssentry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.smssentry.ui.navigation.SMSSentryNavGraph
import com.smssentry.ui.theme.SMSSentryTheme
import com.smssentry.ui.theme.ThemeMode
import com.smssentry.ui.theme.ThemePreferenceRepository
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeRepository = remember { ThemePreferenceRepository(applicationContext) }
            val themeMode by themeRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            SMSSentryTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    SMSSentryNavGraph(
                        navController = navController,
                        themeRepository = themeRepository
                    )
                }
            }
        }
    }
}
