// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        constraints {
            // AGP 9.2.0 pulls vulnerable jdom2 2.0.6 (XXE, GHSA / Dependabot #16).
            add("classpath", "org.jdom:jdom2:2.0.6.1")
            // Netty vulnerabilities (Dependabot #10–#15, #17, #19–#20, #24–#29, #31–#39).
            // All Netty modules must be pinned to the same version.
            add("classpath", "io.netty:netty-codec:4.2.16.Final")
            add("classpath", "io.netty:netty-codec-http:4.2.16.Final")
            add("classpath", "io.netty:netty-codec-http2:4.2.16.Final")
            add("classpath", "io.netty:netty-common:4.2.16.Final")
            add("classpath", "io.netty:netty-handler:4.2.16.Final")
            add("classpath", "io.netty:netty-handler-proxy:4.2.16.Final")
            // Bouncy Castle vulnerabilities (Dependabot #21–#23).
            add("classpath", "org.bouncycastle:bcprov-jdk18on:1.84")
            add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.84")
            // jose4j vulnerability (Dependabot #18).
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6")
            // commons-lang3 vulnerability (Dependabot #12).
            add("classpath", "org.apache.commons:commons-lang3:3.20.0")
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

// Force netty/bcprov/commons-lang3/httpclient versions across every project configuration in
// every module, including AGP's internal `unified-test-platform-*` tooling configs
// (Dependabot #3,9,13,19,20,28,29,31-34,37) and Robolectric's testImplementation pull of bcprov
// 1.79/1.81 (Dependabot #40,#41). The buildscript{} constraints block above only reaches the root
// buildscript classpath and does NOT reach these — confirmed via GitHub SBOM + local
// `:app:dependencies`/`:core:media:dependencies`, both still showing old versions on these edges
// despite that pin. commons-lang3/httpclient (Dependabot #12, #1) come from the same UTP edge:
// com.android.tools:sdk-common -> commons-compress:1.27.1 -> commons-lang3:3.16.0, and
// -> httpmime:4.5.6 -> httpclient:4.5.6.
allprojects {
    configurations.all {
        resolutionStrategy {
            force(
                "io.netty:netty-common:4.2.16.Final",
                "io.netty:netty-buffer:4.2.16.Final",
                "io.netty:netty-transport:4.2.16.Final",
                "io.netty:netty-resolver:4.2.16.Final",
                "io.netty:netty-codec:4.2.16.Final",
                "io.netty:netty-codec-http:4.2.16.Final",
                "io.netty:netty-codec-http2:4.2.16.Final",
                "io.netty:netty-codec-socks:4.2.16.Final",
                "io.netty:netty-handler:4.2.16.Final",
                "io.netty:netty-handler-proxy:4.2.16.Final",
                "io.netty:netty-transport-native-unix-common:4.2.16.Final",
                "org.bouncycastle:bcprov-jdk18on:1.85",
                "org.bouncycastle:bcpkix-jdk18on:1.85",
                "org.apache.commons:commons-lang3:3.20.0",
                "org.apache.httpcomponents:httpclient:4.5.14",
            )
        }
    }
}
