import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	id("net.neoforged.gradle.userdev") version "7.0.163"
	alias(libs.plugins.shadow)
}

// gradle.properties
private val project_name: String by project
private val project_group: String by project
private val mapsync_version: String by project
private val project_description: String by project
private val project_authors: String by project
private val project_copyright: String by project
private val project_home_url: String by project
private val project_source_url: String by project
private val project_issues_url: String by project

version = "${mapsync_version}-${libs.versions.minecraft.get()}"
group = project_group

private val modLocalDep: Configuration by configurations.creating
private val shadowBundle: Configuration by configurations.creating

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
	
	create("server") {
		systemProperty("forge.enabledGameTestNamespaces", project_name.lowercase())
		arguments.add("--nogui")
	}
}

dependencies {
	implementation("net.neoforged:neoforge:${libs.versions.neoforge.get()}")
	
	project(":dep-websockets", configuration = "shadedElements").also {
		implementation(it)
		jarJar(it)
		shadowBundle(it)
	}

	// Map mod integrations (compile only)
	// Using local JARs from h:\git\map-sync\Mods
	compileOnly(files("../Mods/journeymap-neoforge-1.21.1-6.0.0-beta.66.jar"))
	compileOnly(files("../Mods/xaeroworldmap-neoforge-1.21.1-1.40.16.jar"))
	compileOnly(files("../Mods/xaerominimap-neoforge-1.21.1-25.3.13.jar"))
	
	// VoxelMap not available in this version - integration disabled
}

repositories {
	maven(url = "https://maven.neoforged.net/releases") {
		name = "NeoForged"
	}
	maven(url = "https://maven.parchmentmc.org") {
		name = "ParchmentMC"
	}
	maven(url = "https://api.modrinth.com/maven") {
		name = "Modrinth"
		content {
			includeGroup("maven.modrinth")
		}
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
		from(file("../LICENSE")) {
			rename { "LICENSE_${project_name}" }
		}
	}
	processResources {
		val expansions: Map<String, Any> = buildMap expansions@{
			this@expansions["mod_name"] = project_name
			this@expansions["mod_version"] = project.version
			this@expansions["mod_description"] = project_description
			this@expansions["mod_copyright"] = project_copyright
			this@expansions["mod_home_url"] = project_home_url
			this@expansions["mod_source_url"] = project_source_url
			this@expansions["mod_issues_url"] = project_issues_url
			this@expansions["minecraft_version"] = libs.versions.minecraft.get()
			this@expansions["neoforge_version"] = libs.versions.neoforge.get()
			this@expansions["project_authors"] = project_authors
		}
		inputs.properties(expansions)
		filesMatching("META-INF/neoforge.mods.toml") {
			expand(expansions)
		}
		filesMatching("assets/mapsync/lang/en_us.json") {
			expand(expansions)
		}
		filesMatching("mapsync.version.const") {
			expand(expansions)
		}
	}
	named<ShadowJar>("shadowJar") {
		archiveClassifier = "all"
		configurations = listOf(shadowBundle)
	}
	
	register<Sync>("distJar") {
		from(named("jar"))
		into(file("dist/"))
	}
}
