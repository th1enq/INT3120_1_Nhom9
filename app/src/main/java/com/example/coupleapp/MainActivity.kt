package com.example.coupleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.example.coupleapp.navigation.NavGraph
import com.example.coupleapp.ui.theme.CoupleAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Make status bar transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Get navigation target from intent (for widget clicks)
        val navigateTo = intent.getStringExtra("navigate_to")
        
        setContent {
            CoupleAppTheme {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    startDestination = if (navigateTo == "sleep_tracker") "sleep_tracker" else null
                )
            }
        }
    }
}
