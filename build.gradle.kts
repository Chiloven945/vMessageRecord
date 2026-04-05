plugins {
    java
    id("com.gradleup.shadow") version "9.4.0"
    id("xyz.jpenilla.run-velocity") version "3.0.2"
}

group = "top.chiloven"
version = "1.1.0-SNAPSHOT"

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("com.github.szymon-off:vMessage:1.11.0")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.21.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2")

    // sqlite-jdbc contains JNI bindings that expect the original org.sqlite package.
    // Shading is fine, but relocating it breaks native loading (NativeDB -> org.sqlite.core.NativeDB).
    implementation("org.xerial:sqlite-jdbc:3.51.3.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.mysql:mysql-connector-j:9.6.0")
    implementation("com.h2database:h2:2.4.240")
    implementation("org.postgresql:postgresql:42.7.10")
}

val javaVersion = 21

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
}

val templateSource = layout.projectDirectory.dir("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")

val generateTemplates by tasks.registering(Copy::class) {
    description = "Expands files under src/main/templates for IDE/build compatibility with the old Gradle build."
    group = "build setup"

    inputs.property("version", project.version.toString())
    from(templateSource)
    into(templateDest)
    expand(mapOf("version" to project.version.toString()))
    includeEmptyDirs = false
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

sourceSets {
    main {
        java.srcDir(templateDest)
    }
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(generateTemplates)
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.processResources {
    dependsOn(generateTemplates)
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()

    relocate("com.fasterxml.jackson", "top.chiloven.vmrecord.libs.jackson")
    relocate("com.zaxxer.hikari", "top.chiloven.vmrecord.libs.hikari")
    relocate("com.mysql", "top.chiloven.vmrecord.libs.mysql")
    relocate("org.h2", "top.chiloven.vmrecord.libs.h2")
    relocate("org.postgresql", "top.chiloven.vmrecord.libs.postgresql")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.withType<Jar>().configureEach {
    dependsOn(generateTemplates)
}

tasks.runVelocity {
    velocityVersion("3.5.0-SNAPSHOT")
}
