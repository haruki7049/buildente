plugins {
    id("java")
    id("maven-publish")
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
            version = "0.0.0"

            from(components["java"])
        }
    }
}
