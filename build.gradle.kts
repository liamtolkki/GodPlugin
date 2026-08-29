import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.liamtolkkinen"
version = providers.gradleProperty("releaseVersion")
    .orElse("0.7.0-SNAPSHOT")
    .get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val godApiVersion = "0.1.0"
val godApiJar = layout.buildDirectory.file("dependencies/godapi-$godApiVersion.jar")

val downloadGodApi by tasks.registering {
    group = "build setup"
    description = "Downloads the pinned GodApi GitHub Release JAR."
    outputs.file(godApiJar)

    doLast {
        val outputPath = godApiJar.get().asFile.toPath()
        Files.createDirectories(outputPath.parent)

        val temporaryPath = outputPath.resolveSibling("${outputPath.fileName}.download")
        val releaseUrl = URI.create(
            "https://github.com/liamtolkki/GodApi/releases/download/" +
                "v$godApiVersion/godapi-$godApiVersion.jar"
        )

        try {
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
            val request = HttpRequest.newBuilder(releaseUrl)
                .header("User-Agent", "GodPlugin-Gradle-Build")
                .GET()
                .build()
            val response = client.send(
                request,
                HttpResponse.BodyHandlers.ofFile(temporaryPath)
            )

            if (response.statusCode() !in 200..299) {
                throw GradleException(
                    "Failed to download GodApi $godApiVersion from " +
                        "$releaseUrl: HTTP ${response.statusCode()}"
                )
            }

            Files.move(
                temporaryPath,
                outputPath,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            Files.deleteIfExists(temporaryPath)
        }
    }
}

dependencies {
    implementation(files(godApiJar))
    implementation("com.google.code.gson:gson:2.13.2")

    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    testImplementation("io.papermc.paper:paper-api:26.1.2.build.+")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.0")
    testImplementation("org.mockito:mockito-core:5.20.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(downloadGodApi)
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("god")
    archiveClassifier.set("plain")
}

tasks.named<ShadowJar>("shadowJar") {
    dependsOn(downloadGodApi)
    archiveBaseName.set("god")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.wrapper {
    gradleVersion = "9.7.1"
    distributionType = Wrapper.DistributionType.BIN
}
