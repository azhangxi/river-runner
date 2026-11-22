import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Web version of River Runner for iPhone and other browsers. Built twice: Wasm for current
// browsers (Safari 18.2+) and JS for older ones; `composeCompatibilityBrowserDistribution`
// packs both with a small loader that picks the one the browser supports.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    js {
        browser { commonWebpackConfig { outputFileName = "riverRunner.js" } }
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "riverRunner.js" } }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":engine"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "com.riverrunner.web.res"
}
