plugins {
    id("java")
    id("maven-publish")
    id("checkstyle")
}

repositories {
    mavenCentral()

    // The upload destination
    maven { url = uri("https://codeberg.org/api/packages/haruki7049/maven") }
}

dependencies {}

java {
    withJavadocJar()
    withSourcesJar()

    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.release = 17
}

publishing {
    repositories {
        maven {
            name = "Codeberg"
            url = uri("https://codeberg.org/api/packages/haruki7049/maven")

            credentials {
                username = System.getenv("CODEBERG_USERNAME") ?: ""
                password = System.getenv("CODEBERG_TOKEN") ?: ""
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = "dev.haruki7049.buildente"
            artifactId = "buildente-lib"
            version = System.getenv("BUILDENTE_VERSION") ?: "main-local"

            from(components["java"])

            pom {
                url = "https://codeberg.org/haruki7049/buildente"
                scm {
                    url = "https://codeberg.org/haruki7049/buildente"
                    connection = "scm:git:https://codeberg.org/haruki7049/buildente.git"
                    developerConnection = "scm:git:ssh://codeberg.org/haruki7049/buildente.git"
                }
            }
        }
    }
}

checkstyle {
    toolVersion = "12.1.1"
}
