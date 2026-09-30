package com.unfaircake.cutter

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.unfaircake.cutter.ui.CakeApp
import com.unfaircake.cutter.ui.theme.UnfairCakeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The camera preview (or the pink permission screen) sits under the status bar, so its
        // icons are always light. The navigation bar sits under the cream panel: dark icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            UnfairCakeTheme {
                CakeApp()
            }
        }
    }
}
