package com.riverrunner.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // the page shows "加载中" until the app is ready
    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) { App(platform = "Wasm") }
}
