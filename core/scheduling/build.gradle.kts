import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin, no Android: the scheduling engine must be testable on the JVM alone (docs/spec/02-diseno.md §3).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// The engine decides whether an alarm rings: line coverage below 90 % fails `check` (docs/spec/03-ci-release.md).
kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}

tasks.named("check") {
    dependsOn("koverVerify")
}

dependencies {
    testImplementation(libs.junit)
}
