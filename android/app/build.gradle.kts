import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lumvane.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lumvane.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 10001
        versionName = "1.0.1"
    }

    androidResources {
        localeFilters += listOf("en", "ru")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.isFile) f.inputStream().use { load(it) }
    }
    val signingProps = Properties().apply {
        val path = (findProperty("lumvaneSigning") as String?) ?: localProps.getProperty("lumvane.signing")
        val f = path?.let { file(it) }
        if (f != null && f.isFile) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (signingProps.isNotEmpty()) create("release") {
            storeFile = file(signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs.useLegacyPackaging = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.inputs.dir(rootProject.file("../testdata")).withPropertyName("sharedTestData")
                if (project.hasProperty("screenshots")) {
                    test.filter.includeTestsMatching("*ScreenshotTest")
                    test.systemProperty("roborazzi.test.record", "true")
                    test.systemProperty("lumvane.screenshots.dir", rootProject.file("../docs/screenshots").absolutePath)
                    test.outputs.upToDateWhen { false }
                } else {
                    test.filter.excludeTestsMatching("*ScreenshotTest")
                }
            }
        }
    }
}

val licenseAssetsRoot = layout.buildDirectory.dir("generated/licenseAssets")
val licenseAssets = tasks.register<Sync>("licenseAssets") {
    val repo = rootProject.file("..")
    from(repo.resolve("LICENSE")) { rename { "LICENSE.txt" } }
    from(repo.resolve("THIRD_PARTY_NOTICES.md"))
    from(repo.resolve("licenses"))
    from(file("libs/lumcore-licenses.txt")) { rename { "Go-modules.txt" } }
    into(licenseAssetsRoot.map { it.dir("licenses") })
}
android.sourceSets["main"].assets.srcDir(licenseAssetsRoot)
tasks.named("preBuild") { dependsOn(licenseAssets) }

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.zxing.embedded)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.zxing.core)
    implementation("org.yaml:snakeyaml:${libs.versions.snakeyaml.get()}:android")

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric) { exclude(group = "org.yaml") }
    testImplementation(libs.roborazzi) { exclude(group = "org.yaml") }
    testImplementation(libs.roborazzi.compose) { exclude(group = "org.yaml") }
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
