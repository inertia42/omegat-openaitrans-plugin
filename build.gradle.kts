import org.gradle.crypto.checksum.Checksum
import java.io.FileInputStream
import java.util.*

plugins {
    java
    signing
    distribution
    id("org.gradle.crypto.checksum") version "1.4.0"
    id("com.diffplug.spotless") version "6.12.0"
    id("org.omegat.gradle") version "2.0.0-rc2"
    id("com.palantir.git-version") version "3.0.0" apply false
}

val dotgit = project.file(".git")
if (dotgit.exists()) {
    apply(plugin = "com.palantir.git-version")
    val versionDetails: groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails> by extra
    val details = versionDetails()
    val baseVersion = details.lastTag.substring(1)
    version = when {
        details.isCleanTag -> baseVersion
        else -> baseVersion
    }
} else {
    val gitArchival = project.file(".git-archival.properties")
    val props = Properties()
    props.load(FileInputStream(gitArchival))
    val versionDescribe = props.getProperty("describe")
    val regex = "^v\\d+\\.\\d+\\.\\d+$".toRegex()
    version = when {
        regex.matches(versionDescribe) -> versionDescribe.substring(1)
        else -> versionDescribe.substring(1) + "-SNAPSHOT"
    }
}

omegat {
    version = "6.0.0"
    pluginClass = "xyz.inertia.machinetranslators.openaitrans.OpenaiTranslate"
    packIntoJarFileFilter = {it.exclude("META-INF/**/*", "module-info.class")}
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    // OmegaT
    compileOnly("org.omegat:omegat:${omegat.version.get()}")

    // Uber JAR
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    packIntoJar("net.bytebuddy:byte-buddy:1.14.17")

    // Annotations
    compileOnly("org.jetbrains:annotations:24.1.0")
    packIntoJar("org.slf4j:slf4j-api:2.0.7")
    implementation("org.netbeans.api:org-openide-awt:RELEASE130") 
    implementation("commons-io:commons-io:2.7")
    implementation("commons-lang:commons-lang:2.6")
    packIntoJar("com.fasterxml.jackson.core:jackson-core:2.15.2")
    packIntoJar("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    testImplementation("junit:junit:4.13")
}

distributions {
    main {
        contents {
            from(tasks["jar"], "README.md", "COPYING", "CHANGELOG.md")
        }
    }
}

val signKey = listOf("signingKey", "signing.keyId", "signing.gnupg.keyName").find {project.hasProperty(it)}
tasks.withType<Sign> {
    onlyIf { signKey != null }
}

signing {
    when (signKey) {
        "signingKey" -> {
            val signingKey: String? by project
            val signingPassword: String? by project
            useInMemoryPgpKeys(signingKey, signingPassword)
        }

        "signing.keyId" -> {/* do nothing */
        }

        "signing.gnupg.keyName" -> {
            useGpgCmd()
        }
    }
    sign(tasks.distZip.get())
    sign(tasks.jar.get())
}

val jar by tasks.getting(Jar::class) {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

spotless {
    java {
        target(listOf("src/*/java/**/*.java"))
        // removeUnusedImports()
        palantirJavaFormat()
        importOrder("org.omegat", "java", "javax", "", "\\#")
    }
}


tasks.register<Checksum>("createChecksums") {
    dependsOn(tasks.distZip)
    inputFiles.setFrom(listOf(tasks.jar.get(), tasks.distZip.get()))
    outputDirectory.set(layout.buildDirectory.dir("distributions"))
    checksumAlgorithm.set(Checksum.Algorithm.SHA512)
    appendFileNameToChecksum.set(true)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    systemProperty("file.encoding", "UTF-8")
}

tasks.withType<ProcessResources> {
    filteringCharset = "UTF-8"
}