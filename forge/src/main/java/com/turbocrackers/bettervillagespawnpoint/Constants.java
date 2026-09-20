package com.turbocrackers.bettervillagespawnpoint;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Constants {

	public static final String MOD_ID = "bettervillagespawnpoint";
	public static final String MOD_NAME = "BetterVillageSpawnPoint";
	// 1.12.2's @Mod takes the version as a compile-time constant. Keep this equal to `version` in
	// gradle.properties; forge:checkModVersionConstant fails the build if they drift apart.
	public static final String MOD_VERSION = "3.0";
	// 1.12.2 ships log4j only; slf4j is not on the game's classpath.
	public static final Logger LOG = LogManager.getLogger(MOD_NAME);
}
