package com.cctomato.tomatofile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cctomato.tomatofile.ui.AppNav
import com.cctomato.tomatofile.ui.theme.TomatoTheme
import com.cctomato.tomatofile.ui.tv.ProvideTvMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TomatoTheme {
                ProvideTvMode {
                    AppNav()
                }
            }
        }
    }
}