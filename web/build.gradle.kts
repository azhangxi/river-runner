plugins {
    kotlin("multiplatform") version "2.1.21"
}

repositories {
    mavenCentral()
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "river-runner.js"
            }
        }
        binaries.executable()
    }
    sourceSets.jsMain {
        kotlin.srcDirs("src/main/kotlin", "../engine/src/main/kotlin", "../shared/src/main/kotlin")
        dependencies { implementation("org.jetbrains.kotlin:kotlin-dom-api-compat:2.1.21") }
        resources.srcDir("src/main/resources")
    }
}
