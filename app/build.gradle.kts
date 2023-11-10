import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.*

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.org.jetbrains.compose)
}

group = "com.eqoty"
version = "1.0-SNAPSHOT"


kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }
    jvm()
    macosArm64().binaries { executable() }
    macosX64().binaries { executable() }
    applyDefaultHierarchyTemplate()

    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlinx.serialization.ExperimentalSerializationApi")
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(compose.ui)
                implementation(compose.material3)
                implementation(compose.foundation)
                implementation(compose.runtime)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.io.eqoty.secretk.client)
                implementation(libs.io.eqoty.secretk.secret.std.msgs)
                implementation(libs.co.touchlab.kermit)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.io.github.luca992.getenv)
                implementation(libs.bignum)
                implementation(libs.bignum.serialization.kotlinx)
            }
        }

        val jvmMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }

        val jsMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.html.core)
            }
        }

    }
}

compose.desktop {
    application {
        mainClass = "Main_desktopKt"
    }
}

compose.desktop.nativeApplication {
    targets(kotlin.targets.getByName("macosArm64"))
    distributions {
        targetFormats(TargetFormat.Dmg)
        packageName = "Sienna Lend Liquidator"
        packageVersion = "1.0.0"
    }
}

compose.experimental {
    web.application {}
}

fun createEnvVariables(environment: Map<String, Any>): MutableMap<String, Any> {
    val envMap = mutableMapOf<String, Any>()
    envMap.putAll(environment)
    val localPropertiesFile = project.rootProject.file("config.json")
    if (localPropertiesFile.exists()) {
        envMap.put("CONFIG", localPropertiesFile.reader().readText())
    }

    return envMap
}

tasks.withType<JavaExec> {
    environment = createEnvVariables(environment)
}