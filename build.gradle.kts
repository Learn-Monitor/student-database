plugins {
    java
    application
    id("com.gradleup.shadow") version "9.4.3"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "io.github.learn-monitor"

version = "v2.0.0-SNAPSHOT-3"

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

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // built-in plugins
    implementation("io.github.learn-monitor:plugin-loader:v1.0.5")

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

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "student-database", version.toString())

    pom {
        name = "Student database"
        description = "A Java-based application designed to manage and store student information efficiently. It allows admins to perform CRUD (Create, Read, Update, Delete) operations on student records, classes, subjects, and other school-related data, making it a valuable tool for educational institutions. Students can view their progress, and teachers can assign them topics, based on subjects."
        url = "https://github.com/Learn-Monitor/student-database"

        licenses {
            license {
                name = "GNU General Public License v3.0"
                url = "http://www.gnu.org/licenses/gpl-3.0.txt"
            }
        }
        developers {
            developer {
                id = "schlaumeier5"
                name = "Lukas Morgenstern"
                url = "https://github.com/schlaumeier5"
            }
        }
        scm {
            url = "https://github.com/Learn-Monitor/student-database"
            connection = "scm:git:https://github.com/Learn-Monitor/student-database.git"
            developerConnection = "scm:git:ssh://git@github.com/Learn-Monitor/student-database.git"
        }
    }
}
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.withType<Sign>())
}