import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.jksify) apply false
}

allprojects {
//    tasks.withType<KotlinCompile>().configureEach {
//        compilerOptions {
//            freeCompilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
//        }
//    }
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs =
            options.compilerArgs + listOf("-Xlint:deprecation", "-Xlint:unchecked")
    }
}
