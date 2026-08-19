package com.myapp.motrava

import androidx.compose.ui.window.ComposeUIViewController
import com.myapp.motrava.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
    }
) { 
    App() 
}