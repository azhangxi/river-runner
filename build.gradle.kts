plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.2.21" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    // Compose Multiplatform for the web build (1.9 adds the Wasm + JS fallback distribution)
    id("org.jetbrains.compose") version "1.9.3" apply false
    id("io.github.takahirom.roborazzi") version "1.43.0" apply false
}
