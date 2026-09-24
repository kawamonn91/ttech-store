package com.ttech.pdftoolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.ttech.common.theme.TtechTheme
import com.ttech.pdftoolkit.ui.PdfToolkitScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContent {
            TtechTheme(seed = Color(0xFF0D9488)) {
                PdfToolkitScreen()
            }
        }
    }
}
