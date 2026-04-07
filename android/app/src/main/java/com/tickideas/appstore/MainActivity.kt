package com.tickideas.appstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tickideas.appstore.ui.AppNavigation
import com.tickideas.appstore.ui.theme.TickAppStoreTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TickAppStoreTheme {
                AppNavigation()
            }
        }
    }
}
