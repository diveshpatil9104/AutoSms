package com.example.autowish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.example.autowish.ui.theme.AutoWishTheme
import com.example.autowish.ui.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoWishTheme {
                // Use navigation to manage screens including HomeScreen and AddBirthdayScreen
                AppNavigation()
            }
        }
    }
}