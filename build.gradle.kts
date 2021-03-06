import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "org.openrndr.template"
version = "0.3.9"
val applicationMainClass = "TemplateProgramKt"

val openrndrUseSnapshot = false
val openrndrVersion = if (openrndrUseSnapshot) "0.4.0-SNAPSHOT" else "0.3.39"

val panelUseSnapshot = false
val panelVersion = if (panelUseSnapshot) "0.4.0-SNAPSHOT" else "0.3.22-rc.1"

val orxUseSnapshot = false
val orxVersion = if (orxUseSnapshot) "0.4.0-SNAPSHOT" else "0.3.49"

// supported features are: orx-camera, orx-compositor,orx-easing, orx-filter-extension,orx-file-watcher, orx-fx
// orx-integral-image, orx-interval-tree, orx-jumpflood, orx-kinect-v1, orx-kdtree, orx-mesh-generators,orx-midi, orx-no-clear,
// orx-noise, orx-obj, orx-olive, orx-osc, orx-palette, orx-runway
val orxFeatures = setOf(
    "orx-noise",
    "orx-fx",
    "orx-palette",
    "orx-olive",
    "orx-compositor",
    "orx-gui",
    "orx-shader-phrases",
    "orx-glslify",
    "orx-temporal-blur",
    "orx-jumpflood",
    "orx-poisson-fill",
    "orx-shade-styles",
    "orx-image-fit",
    "orx-camera",
    "orx-mesh-generators"
)

// supported features are: video, panel
val openrndrFeatures = setOf("video", "panel")

// --------------------------------------------------------------------------------------------------------------------

val openrndrOs = when (OperatingSystem.current()) {
    OperatingSystem.WINDOWS -> "windows"
    OperatingSystem.MAC_OS -> "macos"
    OperatingSystem.LINUX -> "linux-x64"
    else -> throw IllegalArgumentException("os not supported")
}
enum class Logging {
    NONE,
    SIMPLE,
    FULL
}

val applicationLogging = Logging.SIMPLE

val kotlinVersion = "1.3.61"

plugins {
    java
    kotlin("jvm") version("1.3.61")
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://dl.bintray.com/openrndr/openrndr")
    }
    maven {
        url = uri("https://jitpack.io")
    }
    flatDir {
        dirs = setOf(file("external"))
    }
}

fun DependencyHandler.orx(module: String): Any {
    return "org.openrndr.extra:$module:$orxVersion"
}

fun DependencyHandler.openrndr(module: String): Any {
    return "org.openrndr:openrndr-$module:$openrndrVersion"
}

fun DependencyHandler.openrndrNatives(module: String): Any {
    return "org.openrndr:openrndr-$module-natives-$openrndrOs:$openrndrVersion"
}

fun DependencyHandler.orxNatives(module: String): Any {
    return "org.openrndr.extra:$module-natives-$openrndrOs:$orxVersion"
}


dependencies {
    runtimeOnly(openrndr("gl3"))
    runtimeOnly(openrndrNatives("gl3"))
    implementation(openrndr("openal"))
    runtimeOnly(openrndrNatives("openal"))
    implementation(openrndr("core"))
    implementation(openrndr("svg"))
    implementation(openrndr("animatable"))
    implementation(openrndr("extensions"))
    implementation(openrndr("filter"))

    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core","1.3.3")
//    implementation("org.openrndr.plugins","openrndr-plugins","1.0-SNAPSHOT")
    implementation("io.github.microutils", "kotlin-logging","1.7.8")

//    compile(fileTree("dir" to "external/hemesh/library", "include" to "*.jar"))

    when(applicationLogging) {
        Logging.NONE -> {
            runtimeOnly("org.slf4j","slf4j-nop","1.7.29")
        }
        Logging.SIMPLE -> {
            runtimeOnly("org.slf4j","slf4j-simple","1.7.29")
        }
        Logging.FULL -> {
            runtimeOnly("org.apache.logging.log4j", "log4j-slf4j-impl", "2.13.0")
            runtimeOnly("com.fasterxml.jackson.core", "jackson-databind", "2.10.1")
            runtimeOnly("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml", "2.10.1")
        }
    }

    if ("video" in openrndrFeatures) {
        implementation(openrndr("ffmpeg"))
        runtimeOnly(openrndrNatives("ffmpeg"))
    }

    if ("panel" in openrndrFeatures) {
        implementation("org.openrndr.panel:openrndr-panel:$panelVersion")
    }

    for (feature in orxFeatures) {
        implementation(orx(feature))
    }

    if ("orx-kinect-v1" in orxFeatures) {
        runtimeOnly("orx-kinect-v1")
    }

    if ("orx-olive" in orxFeatures) {
        implementation("org.jetbrains.kotlin", "kotlin-scripting-compiler-embeddable")
    }

    implementation(kotlin("stdlib-jdk8"))
    testImplementation("junit", "junit", "4.12")
}

// --------------------------------------------------------------------------------------------------------------------

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = applicationMainClass
    }
    doFirst {
        from(configurations.compileClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }

    exclude(listOf("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA", "**/module-info*"))
    archiveFileName.set("application-$openrndrOs.jar")
}

tasks.create("zipDistribution", Zip::class.java) {
    archiveFileName.set("application-$openrndrOs.zip")
    from("./") {
        include("data/**")
    }
    from("$buildDir/libs/application-$openrndrOs.jar")
}.dependsOn(tasks.jar)

tasks.create("run", JavaExec::class.java) {
    main = applicationMainClass
    classpath = sourceSets.main.get().runtimeClasspath
}.dependsOn(tasks.build)