package com.example.salarytick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.salarytick.ui.home.SalaryTickApp
import com.example.salarytick.ui.theme.SalaryTickTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SalaryTickTheme {
                SalaryTickApp()
            }
        }
    }
}
