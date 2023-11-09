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
        val commonMain by getting {
            dependencies {
                implementation(compose.ui)
                implementation(compose.material3)
                implementation(compose.foundation)
                implementation(compose.runtime)
                implementation(libs.io.eqoty.secretk.client)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.io.github.luca992.getenv)
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
    val properties = Properties()
    val localPropertiesFile = project.rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        properties.load(localPropertiesFile.reader())
    }
    properties["MNEMONIC"]?.let {
        envMap.put("MNEMONIC", it)
    }
    return envMap
}

tasks.withType<JavaExec> {
    environment = createEnvVariables(environment)
}