plugins {
    id("java")
    id("maven-publish")
    id("checkstyle")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

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

tasks.named<Test>("test") {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/haruki7049/buildente")

            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
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
                url = "https://github.com/haruki7049/buildente"
                scm {
                    url = "https://github.com/haruki7049/buildente"
                    connection = "scm:git:https://github.com/haruki7049/buildente.git"
                    developerConnection = "scm:git:ssh://github.com/haruki7049/buildente.git"
                }

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/license/mit"
                    }
                }

                developers {
                    developer {
                        id = "haruki7049"
                        email = "tontonkirikiri@gmail.com"
                    }
                }
            }
        }
    }
}

checkstyle {
    toolVersion = "12.1.1"
}
