// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        constraints {
            // AGP 9.2.0 pulls vulnerable jdom2 2.0.6 (XXE, GHSA / Dependabot #16).
            add("classpath", "org.jdom:jdom2:2.0.6.1")
            // Netty vulnerabilities (Dependabot #10–#15, #17, #19–#20, #24–#29, #31–#39).
            // All Netty modules must be pinned to the same version.
            add("classpath", "io.netty:netty-codec:4.2.15.Final")
            add("classpath", "io.netty:netty-codec-http:4.2.15.Final")
            add("classpath", "io.netty:netty-codec-http2:4.2.15.Final")
            add("classpath", "io.netty:netty-common:4.2.15.Final")
            add("classpath", "io.netty:netty-handler:4.2.15.Final")
            add("classpath", "io.netty:netty-handler-proxy:4.2.15.Final")
            // Bouncy Castle vulnerabilities (Dependabot #21–#23).
            add("classpath", "org.bouncycastle:bcprov-jdk18on:1.84")
            add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.84")
            // jose4j vulnerability (Dependabot #18).
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6")
            // commons-lang3 vulnerability (Dependabot #12).
            add("classpath", "org.apache.commons:commons-lang3:3.18.0")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
