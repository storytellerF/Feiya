import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.FileWriter
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.starter.easylauncher") version "6.4.1"
    alias(libs.plugins.compose.compiler)
}

val signPath: String? = System.getenv("storyteller_f_sign_path")
val signAlias: String? = System.getenv("storyteller_f_sign_alias")
val signStorePassword: String? = System.getenv("storyteller_f_sign_store_password")
val signKeyPassword: String? = System.getenv("storyteller_f_sign_key_password")
val javaVersion = JavaVersion.VERSION_21

android {
    namespace = "com.storyteller_f.feiya"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.storyteller_f.feiya"
        minSdk = 21
        targetSdk = 35
        versionCode = 8
        versionName = "1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (signPath != null && signAlias != null && signStorePassword != null && signKeyPassword != null) {
            create("release") {
                keyAlias = signAlias
                keyPassword = signKeyPassword
                storeFile = File(signPath)
                storePassword = signStorePassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue(
                "string",
                "leak_canary_display_activity_label",
                "Feiya"
            )
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSignConfig = signingConfigs.findByName("release")
            if (releaseSignConfig != null)
                signingConfig = releaseSignConfig
        }

        create("alpha") {
            applicationIdSuffix = ".alpha"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSignConfig = signingConfigs.findByName("release")
            if (releaseSignConfig != null)
                signingConfig = releaseSignConfig
        }
    }
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    buildFeatures {
        compose = true
        resValues = true
    }

    packaging {
        resources {
            excludes += ("/META-INF/{AL2.0,LGPL2.1}")
            pickFirsts += listOf("META-INF/*", "/META-INF/io.netty.versions.properties")
        }
        jniLibs {
            pickFirsts += "META-INF/*"
        }
    }
    splits {

        // Configures multiple APKs based on ABI.
        abi {
            isEnable = true
            reset()
            include("x86", "x86_64", "arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion.toString())
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    testImplementation(libs.androidx.rules)
    implementation(libs.androidx.adaptive.android)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.customview)
    debugImplementation(libs.androidx.customview.poolingcontainer)

    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.permissions)

    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //ktor
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)

    implementation(libs.bundles.coruntines)


    implementation(libs.logback.android)
    implementation(libs.zxing.core)

    implementation(libs.compose.prefs3)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.webkit)
    implementation(libs.androidx.browser)

    implementation(libs.androidx.core.splashscreen)
    debugImplementation(libs.leakcanary.android)

    val baoFolder = project.findProperty("baoFolder")
    val baoModule = findProject(":bao:startup")
    if (baoFolder == "local" && baoModule != null)
        implementation(baoModule)
    else
        implementation("com.github.storytellerF.Bao:startup:e978cf52f2")

    val yongFolder = project.findProperty("yongFolder")
    val yongModule = findProject(":yong:library")
    if (yongFolder == "local" && yongModule != null) {
        testImplementation(yongModule)
    }
    implementation(libs.compose.markdown)
    implementation(libs.coil.compose)
}

data class DependencyInfo(val group: String, val name: String, val version: String, val children: MutableList<DependencyInfo> = mutableListOf())

if (project.findProperty("report_deps") == "true") {
    project.afterEvaluate {
        val root = layout.buildDirectory.dir("reports/deps").get()
        val outputFile = layout.buildDirectory.file("reports/deps/dependencies.json").get().asFile
        root.asFile.let {
            if (!(it.exists())) it.mkdir()
        }
        FileWriter(outputFile).use { writer ->
            JsonWriter(writer).use { jsonWriter ->
                jsonWriter.beginObject()
                configurations.filter {
                    it.isCanBeResolved
                }.forEach { configuration ->
                    val firstLevelModuleDependencies =
                        configuration.resolvedConfiguration.firstLevelModuleDependencies
                    if (firstLevelModuleDependencies.isNotEmpty() && !configuration.name.startsWith("_internal")) {
                        val newDir = root.dir(configuration.name)
                        newDir.asFile.let {
                            if (!(it.exists())) it.mkdir()
                        }
                        jsonWriter.name(configuration.name)
                        jsonWriter.beginArray()
                        firstLevelModuleDependencies.forEach { module ->
                            buildDependencyTree(module, jsonWriter, newDir)
                        }
                        jsonWriter.endArray()
                    }
                }
                jsonWriter.endObject()
            }

        }
        println("Dependencies exported to ${outputFile.absolutePath}")
    }
}

private fun buildDependencyTree(
    module: ResolvedDependency,
    jsonWriter: JsonWriter,
    newDir: Directory
) {
    val childDir = newDir.dir("${module.moduleGroup}(${module.moduleName})[${module.moduleVersion}]")
    childDir.asFile.let {
        if (!it.exists()) it.mkdir()
    }
    jsonWriter.beginObject()
    jsonWriter.name("group").value(module.moduleGroup)
    jsonWriter.name("name").value(module.moduleName)
    jsonWriter.name("version").value(module.moduleVersion)
    jsonWriter.name("children")
    jsonWriter.beginArray()
    // 遍历间接依赖
    module.children.forEach { childModule ->
        buildDependencyTree(childModule, jsonWriter, childDir)
    }
    jsonWriter.endArray()
    jsonWriter.endObject()
}
