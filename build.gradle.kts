import com.vanniktech.maven.publish.SonatypeHost

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "com.enconvert"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

// Publishes to the Sonatype Central Portal with GPG signing. Credentials + key
// come from ~/.gradle/gradle.properties. Artifact id is "enconvert-kotlin" so it
// does NOT collide with the Java SDK's "com.enconvert:enconvert-sdk". The plugin
// builds the sources + javadoc jars automatically.
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("com.enconvert", "enconvert-kotlin", "0.0.1")

    pom {
        name.set("Enconvert Kotlin SDK")
        description.set(
            "Enconvert Kotlin SDK — read any page or file into agent-ready Markdown/JSON/screenshots, " +
                "every read scored. V2 perception + file conversion.",
        )
        inceptionYear.set("2026")
        url.set("https://github.com/conversionapi/kotlin-sdk")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("enconvert")
                name.set("Enconvert")
                email.set("support@enconvert.com")
                url.set("https://enconvert.com")
            }
        }
        scm {
            url.set("https://github.com/conversionapi/kotlin-sdk")
            connection.set("scm:git:git://github.com/conversionapi/kotlin-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/conversionapi/kotlin-sdk.git")
        }
    }
}
