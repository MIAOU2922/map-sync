plugins {
	id("net.neoforged.gradle.userdev") version "7.0.163"
}

// gradle.properties
private val project_name: String by project
private val project_group: String by project
private val distantsync_version: String by project
private val project_description: String by project
private val project_authors: String by project
private val project_copyright: String by project
private val project_home_url: String by project
private val project_source_url: String by project
private val project_issues_url: String by project

version = "${distantsync_version}-${libs.versions.minecraft.get()}"
group = project_group

base {
	archivesName = project_name
}

runs {
	configureEach {
		systemProperty("forge.logging.markers", "REGISTRIES")
		systemProperty("forge.logging.console.level", "debug")
	}
	
	create("client") {
		systemProperty("forge.enabledGameTestNamespaces", project_name.lowercase())
	}
}

dependencies {
	implementation("net.neoforged:neoforge:${libs.versions.neoforge.get()}")
	
	// TODO: Add Distant Horizons and map mod integrations when needed
}

repositories {
	maven(url = "https://maven.neoforged.net/releases") {
		name = "NeoForged"
	}
	maven(url = "https://maven.parchmentmc.org") {
		name = "ParchmentMC"
	}
	mavenCentral()
}

java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

tasks {
	compileJava {
		options.encoding = "UTF-8"
		options.release = 21
	}
	jar {
		from("LICENSE") {
			rename { "${it}_${project_name}" }
		}
		manifest {
			attributes(mapOf(
				"Specification-Title" to project_name,
				"Specification-Version" to version,
				"Implementation-Title" to project_name,
				"Implementation-Version" to version
			))
		}
	}
}
