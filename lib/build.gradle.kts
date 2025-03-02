import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)
    explicitApi()

    jvm()
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.android.driver)
            implementation(libs.androidx.core)
        }
        commonMain {
            dependencies {
                implementation(compose.foundation)
                implementation(compose.runtime)
                implementation(libs.mmkv)
                implementation(libs.gson)
                implementation(compose.material3)
                implementation(libs.jetbrains.lifecycle.viewmodel)
                api(libs.coil.kt)
                api(libs.coil.kt.compose)
                api(libs.coil.kt.okhttp)
            }
        }

        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
                api(libs.mupdf.fitz)
                implementation(libs.jvm.driver)
            }
        }
    }
}

composeCompiler {
    reportsDestination = project.layout.buildDirectory.dir("compose_compiler")
    metricsDestination = project.layout.buildDirectory.dir("compose_compiler")
}

android {
    namespace = "com.archko.reader.pdf"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.archko.reader.pdf.cache")
        }
    }
}