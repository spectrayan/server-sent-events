plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
    `maven-publish`
}

group = "com.spectrayan.sse"
version = "2.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // HTTP engine (battle-tested across Android & JVM)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Spectrayan SSE Kotlin Client")
                description.set("Idiomatic, lightweight Kotlin Coroutines SSE client for Android and JVM applications")
                url.set("https://github.com/spectrayan/server-sent-events")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("spectrayan")
                        name.set("Spectrayan AI Engineering Team")
                        email.set("support@spectrayan.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/spectrayan/server-sent-events.git")
                    developerConnection.set("scm:git:ssh://github.com:spectrayan/server-sent-events.git")
                    url.set("https://github.com/spectrayan/server-sent-events")
                }
            }
        }
    }
}
