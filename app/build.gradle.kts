import com.google.gson.stream.JsonWriter
import java.io.FileWriter
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import javax.inject.Inject

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.serialization)
    alias(libs.plugins.easylauncher)
    alias(libs.plugins.compose.compiler)
}

// The browser is an independent npm project. Only its distribution becomes Java
// resources; source files and node_modules never enter the APK.
val webDirectory = rootProject.layout.projectDirectory.dir("web")
val webDist = webDirectory.dir("dist")
val npmExecutable = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"
val installWebDependencies = tasks.register<Exec>("installWebDependencies") {
    group = "build"
    description = "Install the browser project's locked npm dependencies."
    workingDir(webDirectory)
    commandLine(npmExecutable, "ci", "--include=dev", "--no-audit", "--no-fund")
    inputs.files(webDirectory.file("package.json"), webDirectory.file("package-lock.json"),
        webDirectory.file(".npmrc"), webDirectory.file(".nvmrc"))
    inputs.property("nodeVersion", providers.exec { commandLine("node", "--version") }.standardOutput.asText)
    inputs.property("npmVersion", providers.exec { commandLine(npmExecutable, "--version") }.standardOutput.asText)
    outputs.dir(webDirectory.dir("node_modules"))
}
val buildWeb = tasks.register<Exec>("buildWeb") {
    group = "build"
    description = "Bundle the browser UI and npm dependencies."
    dependsOn(installWebDependencies)
    workingDir(webDirectory)
    commandLine(npmExecutable, "run", "build")
    inputs.dir(webDirectory.dir("src"))
    inputs.dir(webDirectory.dir("scripts"))
    inputs.files(webDirectory.file("package.json"), webDirectory.file("package-lock.json"))
    inputs.files(installWebDependencies.map { it.outputs.files })
    outputs.dir(webDist)
}

abstract class SyncWebResources : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val distribution: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun copyDistribution() {
        fileSystemOperations.sync {
            from(distribution)
            into(outputDirectory)
        }
    }
}

androidComponents.onVariants { variant ->
    val webResources = tasks.register<SyncWebResources>("generate${variant.name.replaceFirstChar { it.uppercase() }}WebResources") {
        dependsOn(buildWeb)
        distribution.set(webDist)
    }
    variant.sources.resources?.addGeneratedSourceDirectory(webResources, SyncWebResources::outputDirectory)
}

val signPath: String? = System.getenv("storyteller_f_sign_path")
val signFile = signPath?.let(rootProject::file)
val signAlias: String? = System.getenv("storyteller_f_sign_alias")
val signStorePassword: String? = System.getenv("storyteller_f_sign_store_password")
val signKeyPassword: String? = System.getenv("storyteller_f_sign_key_password")
val javaVersion = JavaVersion.VERSION_21

android {
    namespace = "com.storyteller_f.feiya"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.storyteller_f.feiya"
        minSdk = 33
        targetSdk = 37
        versionCode = 8
        versionName = "1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (signFile != null && signAlias != null && signStorePassword != null && signKeyPassword != null) {
            create("release") {
                keyAlias = signAlias
                keyPassword = signKeyPassword
                storeFile = signFile
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += ("/META-INF/{AL2.0,LGPL2.1}")
            pickFirsts += listOf("META-INF/*", "/META-INF/io.netty.versions.properties")
            // Netty's platform jars bundle identical third-party license files.
            // Keep one copy in the APK rather than excluding the notices.
            pickFirsts += "META-INF/license/**"
            // The same jars also repeat identical GraalVM configuration resources.
            pickFirsts += "META-INF/native-image/io.netty/netty-codec-native-quic/**"
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
    constraints {
        implementation(libs.androidx.concurrent.futures) {
            because("AndroidX Test requires 1.2.0; AGP aligns test dependencies with the app runtime")
        }
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
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

    implementation(libs.accompanist.permissions)

    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.rules)
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
