plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jline:jline:3.30.0")
    implementation("org.yaml:snakeyaml:2.4")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.release = 21
    options.compilerArgs.add("-Xlint:all")
}

application {
    mainClass = "javaprac.Main"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.test {
    useJUnitPlatform()
    // each challenge check compiles and forks a JVM, so run test classes in parallel forks
    maxParallelForks = 2
}
