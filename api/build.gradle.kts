plugins {
    id("nyaadanbou-conventions.repositories")
    id("cronscheduler-conventions.commons")
    id("org.jetbrains.kotlinx.atomicfu")
    `maven-publish`
}

group = "cc.mewcraft.cronscheduler"
version = "0.0.1-SNAPSHOT"
description = "A Kotlin CRON scheduler based on cron-utils library. Specially made for Linux cron format!"

dependencies {
    api(local.cronutils) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    compileOnly(local.guava)
    testImplementation(local.cronutils)
    testImplementation(local.guava)
}

publishing {
    repositories {
        maven("https://repo.mewcraft.cc/private") {
            credentials {
                username = providers.gradleProperty("nyaadanbou.mavenUsername").orNull
                password = providers.gradleProperty("nyaadanbou.mavenPassword").orNull
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}