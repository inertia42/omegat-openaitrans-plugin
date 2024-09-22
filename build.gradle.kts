


repositories {
    maven {
        url=uri("https://www.jitpack.io")
    }
    mavenCentral()
}



plugins {
//    id("io.beekeeper.gradle.plugin") version "0.15.0"
//    id("com.github.spotbugs") version "6.0.0-beta.3"
    java
//    checkstyle
    kotlin("jvm") version "1.9.10"

}
version = "1.2.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}


dependencies {
    //no compile,because slf4j-api is included in OmegaT ifself
    compileOnly("org.slf4j:slf4j-api:1.7.25")

    compileOnly("org.omegat:omegat:6.0.0")
    compileOnly("commons-io:commons-io:2.7")
    compileOnly("commons-lang:commons-lang:2.6")
    // no need.We can use slf4j-jdk14 which is included by "org.omegat:omegat:4.3.0"
//    provided 'org.slf4j:slf4j-nop:1.7.21'
    testImplementation("junit:junit:4.13.1")

    //testCompile 'xmlunit:xmlunit:1.6'
    //testCompile 'org.madlonkay.supertmxmerge:supertmxmerge:2.0.1'

    //testCompile 'org.apache.logging.log4j:log4j-api:2.13.3'
    //testCompile 'org.apache.logging.log4j:log4j-core:2.13.3'
//    testCompile 'org.apache.logging.log4j:log4j-slf4j-impl:2.13.3'

    // https://mvnrepository.com/artifact/cn.hutool/hutool-json
    implementation("cn.hutool:hutool-json:5.8.22")
    // https://mvnrepository.com/artifact/cn.hutool/hutool-http
    implementation("cn.hutool:hutool-http:5.8.22")
    // https://mvnrepository.com/artifact/cn.hutool/hutool-crypto
    //compile group: 'cn.hutool', name: 'hutool-crypto', version: '5.4.0'
//    implementation(kotlin("stdlib-jdk11"))

}


//test.useTestNG()

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs = listOf("-Xlint:deprecation","-Xlint:unchecked")
    options.encoding = "UTF-8"
    options.release = 11
}

////checkstyle
//tasks.withType<Checkstyle>().configureEach{
//        reports{
//            xml.required.set(false)
//            html.required.set(true)
//            html.stylesheet = resources.text.fromFile("${rootProject.projectDir}/config/checkstyle/google_checks.xml")
//
//        }
//
//}



// Build FatJar
//
// It is easy to install a 3rd-party OmegaT plugin which is
// a single jar file, because all user should do is just to put the jar
// file into plugins directory.

//tasks.jar
//task.withType<jar>().configureEach{
//
//    // make gradle5 compatible
////    from files(sourceSets.main.output.classesDir)
////    from sourceSets.main.output.classesDirs
//    from sourceSets.main.output
//            dependsOn configurations.runtimeClasspath
//            from { configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it)  } }
////            {
////        exclude 'META-INF/MANIFEST.MF'
////    }
//    manifest {
//        // plugin's main class name is defined in gradle.properties file.
//        attributes("OmegaT-Plugins": pluginMainClass)
//    }
//    duplicatesStrategy "exclude"
//}

tasks.register<Jar>("uberJar") {
//    archiveClassifier.set("uber")
    println(sourceSets.main.get().output)
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    manifest {
        // plugin's main class name is defined in gradle.properties file.
        attributes("OmegaT-Plugins" to providers.gradleProperty("pluginMainClass"))
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}



// Hack for IntelliJ IDEA
//idea {
//    module {
//        testSourceDirs += file('src/integration-test/java')
//    }
//}

//javadoc {
//    classpath += configurations.provided
//    options{
//        encoding 'UTF-8'
////        charSet 'UTF-8'
//    }
////    options.locale = 'en_US'
////    options.encoding = 'UTF-8'
////    options.charSet = 'UTF-8'
////    options.links 'http://docs.oracle.com/javase/17/docs/api'
//}

//tasks.register('myJavadocs', Javadoc) {
//    source = sourceSets.main.allJava
//    classpath += configurations.provided
//    options.locale = 'en_US'
//    author true
//    options.addStringOption('encoding','UTF-8')
//    options.docletpath = configurations.jaxDoclet.files.asType(List)
//    options.encoding = 'UTF-8'
//    options.charSet = 'UTF-8'
//    options.links 'http://docs.oracle.com/javase/17/docs/api'
//}
/*
groovydoc {
    classpath += configurations.provided
}
*/

//tasks.register('javadocJar', Jar) {
//    dependsOn javadoc
//            archiveClassifier = 'javadoc'
//    from javadoc.destinationDir
//}


//artifacts {
//    archives jar
//            archives sourceJar
//            archives javadocJar
//}



