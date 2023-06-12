pluginManagement {
	repositories {
		gradlePluginPortal()
		maven("https://repo.papermc.io/repository/maven-public/")
	}
}

rootProject.name = "TabList"

include(
		"global",
		"api",
		"v1_20_R1",
		"v1_19_R3",
		"v1_19_R2",
		"v1_19_R1",
		"v1_18_R2",
		"v1_17_R1",
		"v1_8_R3",
		"bukkit",
		"bungee")
