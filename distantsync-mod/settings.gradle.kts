// Update Gradle Wrapper using: ./gradlew wrapper --distribution-type bin --gradle-version <version>
// See Gradle's releases here: https://gradle.org/releases/

pluginManagement {
	repositories {
		maven(url = "https://maven.neoforged.net/releases")
		mavenCentral()
		gradlePluginPortal()
	}
}

rootProject.name = "DistantSync"
