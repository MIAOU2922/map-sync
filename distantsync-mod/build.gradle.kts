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
	
	// Distant Horizons integration (compile only)
	compileOnly(files("../Mods/DistantHorizons-3.0.3-b-1.21.1-fabric-neoforge.jar"))
	
	// Minimap mods (compile only for integration)
	compileOnly(files("../Mods/journeymap-neoforge-1.21.1-6.0.0-beta.66.jar"))
	compileOnly(files("../Mods/xaeroworldmap-neoforge-1.21.1-1.40.16.jar"))
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
	processResources {
		val replaceProperties = mapOf(
			"mod_version" to distantsync_version,
			"mod_name" to project_name,
			"mod_description" to project_description,
			"mod_copyright" to project_copyright,
			"mod_home_url" to project_home_url,
			"mod_issues_url" to project_issues_url,
			"project_authors" to project_authors,
			"minecraft_version" to libs.versions.minecraft.get(),
			"neoforge_version" to libs.versions.neoforge.get()
		)
		inputs.properties(replaceProperties)
		filesMatching("META-INF/neoforge.mods.toml") {
			expand(replaceProperties)
		}
	}
	
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
