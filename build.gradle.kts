plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("maven-publish")
}

group = "igs-landstuhl"

version = "v1.0.1-PATCH-2"

application {
    mainClass.set("de.igslandstuhl.database.Application")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("commons-codec:commons-codec:1.19.0")
    implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20260101.1")
    implementation("org.jline:jline:3.30.6") // for better console input handling
    implementation("org.yaml:snakeyaml:2.2") // plugin imports

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4") // using JUnit 5 (latest)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("test.environment", "true")
}
tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}
tasks.shadowJar {
    archiveBaseName.set("student-database")
    archiveClassifier.set("fat")
    archiveVersion.set(project.version.toString())    // omit version in filename if you want
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // or another version you prefer
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = "igs-landstuhl"
            artifactId = "student-database"
            version = project.version.toString()
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Learn-Monitor/student-database/")

            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}