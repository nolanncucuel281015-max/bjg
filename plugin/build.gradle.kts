plugins {
	java
	id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "com.diamondaltar"
version = "1.0.0"

repositories {
	mavenCentral()
	maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
	paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}

// Les capacités et leurs effets sont partagés avec le mod Fabric (même code, mappings Mojang).
val syncSharedSources by tasks.registering(Sync::class) {
	from("../src/main/java") {
		include(
			"com/diamondaltar/ability/*Effect.java",
			"com/diamondaltar/ability/Cosmic.java",
			"com/diamondaltar/ability/FX.java",
			"com/diamondaltar/ability/Glow.java",
			"com/diamondaltar/ability/Abilities.java",
			"com/diamondaltar/ability/ArmorPassives.java",
			"com/diamondaltar/effect/TimedEffect.java",
			"com/diamondaltar/effect/EffectManager.java",
		)
	}
	into(layout.buildDirectory.dir("shared-src"))
}

sourceSets.main {
	java.srcDir(syncSharedSources)
}

tasks.compileJava {
	options.release.set(21)
	options.encoding = "UTF-8"
}

tasks.processResources {
	val v = project.version.toString()
	inputs.property("version", v)
	filesMatching("plugin.yml") {
		expand("version" to v)
	}
	// Traductions (noms des capacités) lues par le plugin.
	from("../src/main/resources/assets/diamondaltar/lang") {
		include("fr_fr.json")
		into("lang")
	}
}

// Pack de ressources : textures, modèles, armure, traductions + police du flash d'écran.
val resourcePack by tasks.registering(Zip::class) {
	archiveFileName.set("DiamondAltar-ResourcePack.zip")
	destinationDirectory.set(layout.buildDirectory.dir("libs"))
	from("../src/main/resources/assets") {
		into("assets")
		exclude("diamondaltar/particles/**", "diamondaltar/textures/particle/**", "diamondaltar/blockstates/**", "diamondaltar/icon.png")
	}
	from("resourcepack")
	from("../src/main/resources/assets/diamondaltar/icon.png") {
		rename { "pack.png" }
	}
}

tasks.build {
	dependsOn(resourcePack)
}
