package com.example.viko

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.viko.navigation.AppNavigation
import com.example.viko.ui.theme.VikoTheme
import com.example.viko.ui.theme.VBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VikoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VBackground
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
