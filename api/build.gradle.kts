plugins {
    id("nyaadanbou-conventions.repositories")
    id("cronscheduler-conventions.commons")
    `maven-publish`
}

group = "cc.mewcraft.cron"
version = "0.0.1-SNAPSHOT"
description = "A Kotlin CRON scheduler based on cron-utils library. Specially made for Linux cron format!"

dependencies {
    compileOnly(local.guava)
    compileOnly(local.cronutils)
    testImplementation(local.guava)
    testImplementation(local.cronutils)
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
            artifactId = "cron-scheduler"
            from(components["java"])
        }
    }
}