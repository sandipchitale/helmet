plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "sandipchitale"
version = "1.0.33"

repositories {
    mavenCentral()
}

configurations.all {
    exclude("org.slf4j")
}

dependencies {
    implementation("io.fabric8:kubernetes-client:6.13.3")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2024.2.1")
    type.set("IU") // Target IDE Platform

    plugins.set(listOf("com.intellij.kubernetes:242.21829.44"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    runIde {
        if (project.hasProperty("runIde_ideDir")) {
            ideDir = file("${project.extra["runIde_ideDir"]}")
        }
    }

    patchPluginXml {
        sinceBuild.set("222")
        untilBuild.set("243.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
