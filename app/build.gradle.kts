import com.android.build.api.artifact.SingleArtifact
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val emitloomVersion = providers.gradleProperty("emitloomVersionName").getOrElse("1.0.0")
val emitloomVersionCode = providers.gradleProperty("emitloomVersionCode").getOrElse("1").toInt()
val demoControllerEnabled = providers.gradleProperty("emitloomDemo").getOrElse("false") == "true"
val licenseAssets = layout.buildDirectory.dir("generated/emitloomLicenseAssets")
val packageLicense = tasks.register<Copy>("packageProjectLicense") {
    from(rootProject.file("LICENSE"))
    into(licenseAssets.map { it.dir("licenses") })
    rename { "AGPL-3.0.txt" }
}
val localSigning = Properties().apply {
    rootProject.file("signing.properties").takeIf(File::isFile)?.inputStream()?.use(::load)
}
fun signingValue(name: String): String? = providers.environmentVariable(name).orNull
    ?: localSigning.getProperty(name)

android {
    namespace = "com.emitloom.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emitloom.app"
        minSdk = 26
        targetSdk = 35
        versionCode = emitloomVersionCode
        versionName = emitloomVersion
        buildConfigField("boolean", "DEMO_CONTROLLER", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(signingValue("EMITLOOM_KEYSTORE") ?: ".emitloom-signing/release.p12")
            storePassword = signingValue("EMITLOOM_STORE_PASSWORD")
            keyAlias = signingValue("EMITLOOM_KEY_ALIAS")
            keyPassword = signingValue("EMITLOOM_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            if (demoControllerEnabled) {
                applicationIdSuffix = ".demo"
                resValue("string", "app_name", "EmitLoom Demo")
                buildConfigField("boolean", "DEMO_CONTROLLER", "true")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    sourceSets.getByName("main").assets.srcDir(licenseAssets)
}

tasks.named("preBuild") { dependsOn(packageLicense) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

val validateEmitLoomSigning = tasks.register("validateEmitLoomSigning") {
    doLast {
        val required = listOf("EMITLOOM_STORE_PASSWORD", "EMITLOOM_KEY_ALIAS", "EMITLOOM_KEY_PASSWORD")
        check(required.all { !signingValue(it).isNullOrBlank() }) {
            "Configure EmitLoom release signing in signing.properties or with the EMITLOOM_* environment variables."
        }
    }
}

tasks.matching { it.name == "packageRelease" || it.name == "validateSigningRelease" }.configureEach {
    dependsOn(validateEmitLoomSigning)
}

androidComponents.onVariants { variant ->
    val apkKind = when (variant.buildType) {
        "release" -> "release"
        "debug" -> if (demoControllerEnabled) "demo" else return@onVariants
        else -> return@onVariants
    }
    val apkDirectory = variant.artifacts.get(SingleArtifact.APK)
    val loader = variant.artifacts.getBuiltArtifactsLoader()
    val assembleTask = "assemble${variant.name.replaceFirstChar(Char::uppercaseChar)}"
    tasks.matching { it.name == assembleTask }.configureEach {
        doLast {
            val apk = checkNotNull(loader.load(apkDirectory.get())?.elements?.singleOrNull()) {
                "Expected exactly one $apkKind APK."
            }
            check(apk.versionName == emitloomVersion && apk.versionCode == emitloomVersionCode) {
                "Unexpected $apkKind APK version."
            }
            val exportDir = rootProject.file("apk-export").toPath()
            Files.createDirectories(exportDir)
            val destination = exportDir.resolve("emitloom-v$emitloomVersion-$apkKind.apk")
            val staging = Files.createTempFile(exportDir, ".emitloom-$apkKind-", ".apk")
            try {
                Files.copy(File(apk.outputFile).toPath(), staging, StandardCopyOption.REPLACE_EXISTING)
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(staging)
            }
            logger.lifecycle("EmitLoom $apkKind exported to $destination")
        }
    }
}


