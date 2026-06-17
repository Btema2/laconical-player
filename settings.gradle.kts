pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
buildscript {
    dependencies {
        constraints {
            // Force patched versions of vulnerable transitive deps pulled in by build plugins.
            add("classpath", "io.netty:netty-codec:4.2.15.Final")
            add("classpath", "io.netty:netty-codec-http:4.2.15.Final")
            add("classpath", "io.netty:netty-codec-http2:4.2.15.Final")
            add("classpath", "io.netty:netty-common:4.2.15.Final")
            add("classpath", "io.netty:netty-handler:4.2.15.Final")
            add("classpath", "io.netty:netty-handler-proxy:4.2.15.Final")
            add("classpath", "org.bouncycastle:bcprov-jdk18on:1.84")
            add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.84")
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6")
            add("classpath", "org.apache.commons:commons-lang3:3.18.0")
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Laconical Player"
include(":app", ":core:model", ":core:data", ":core:media", ":core:designsystem")