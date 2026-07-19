plugins {
    java
}

group = "org.lwjgl"

val runtimeConfig by configurations.creating {
    isCanBeResolved = true
    extendsFrom(configurations.implementation.get())
}

tasks.register("buildLwjgl") {
    dependsOn("jar")
}

tasks.jar {
    archiveBaseName.set("lwjgl")
    destinationDirectory.set(file("${rootDir}/FCL/src/main/assets/app_runtime/lwjgl"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // LWJGL 3.4.1 modules each ship a META-INF/versions/11/module-info.class. The core
    // lwjgl.jar carries the canonical module descriptor, so it must be processed FIRST:
    // with DuplicatesStrategy.EXCLUDE the first entry wins, which keeps the correct
    // module-info.class and avoids InvalidModuleDescriptorException at runtime.
    from(runtimeConfig.elements.map { files ->
        val sorted = files.map { it.asFile }.sortedByDescending { it.name == "lwjgl.jar" }
        sorted.map {
            println(it.name)
            if (it.isDirectory) it else zipTree(it)
        }
    })
    exclude("net/java/openjdk/cacio/ctc/**")
    // Reproducible output so the SHA/version file only changes when the contents change.
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest {
        attributes("Manifest-Version" to "3.4.1")
        attributes("Automatic-Module-Name" to "org.lwjgl")
    }
    doLast {
        val versionFile = file("../FCL/src/main/assets/app_runtime/lwjgl/version")
        versionFile.writeText(System.currentTimeMillis().toString())
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

dependencies {
    implementation(fileTree("dir" to "libs", "include" to listOf("*.jar")))
    compileOnly(fileTree("dir" to "compileOnly", "include" to listOf("*.jar")))
}