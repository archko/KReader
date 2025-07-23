import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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
            implementation(libs.androidx.core)
            api(libs.mupdf.fitz.aar)
        }
        commonMain {
            dependencies {
                implementation(compose.foundation)
                implementation(compose.runtime)
                implementation(libs.mmkv)
                implementation(compose.material3)
                implementation(libs.jetbrains.lifecycle.viewmodel)
                api(libs.coil.kt)
                api(libs.coil.kt.compose)
                api(libs.coil.kt.okhttp)
                implementation(libs.androidx.room.runtime)
                implementation(libs.sqlite.bundled)
            }
        }

        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
                api(libs.mupdf.fitz)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    //add("kspIosArm64", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
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