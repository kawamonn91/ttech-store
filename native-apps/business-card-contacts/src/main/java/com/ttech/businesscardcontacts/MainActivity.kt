package com.ttech.businesscardcontacts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import com.ttech.businesscardcontacts.ui.BusinessCardContactsScreen
import com.ttech.common.theme.TtechTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtechTheme(seed = Color(0xFF0EA5E9)) {
                BusinessCardContactsScreen()
            }
        }
    }
}
