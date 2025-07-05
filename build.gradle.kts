plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
    id("maven-publish")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
    id("xyz.jpenilla.run-paper") version "2.3.1" // Adds runServer and runMojangMappedServer tasks for testing
}

group = "net.gensokyoreimagined"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    paperweight.paperDevBundle("1.21.7-R0.1-SNAPSHOT")
    implementation("org.spongepowered:configurate-hocon:4.1.2")
    implementation("org.spongepowered:configurate-yaml:4.1.2")
}


tasks {
    compileJava {
        options.release = 21
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "private"
            artifact(tasks.shadowJar) {
                artifactId = rootProject.name
            }
        }
    }

    repositories {
        maven {
            name = "gensokyoReimagined"
            url = uri("https://repo.gensokyoreimagined.net")
            credentials {
                username = project.findProperty("gensokyoUser")?.toString() ?: System.getenv("GENSOKYOUSER")
                password = project.findProperty("gensokyoToken")?.toString() ?: System.getenv("GENSOKYOTOKEN")
            }
        }
    }
}
