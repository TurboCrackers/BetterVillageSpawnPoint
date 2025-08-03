package com.turbocrackers.bettervillagespawnpoint;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.nio.file.Path;
import java.util.List;

public class FabricConfig extends CommonConfig {

    private static final String CONFIG_FILE = "config/BetterVillageSpawnPoint-common.toml";

    private List<? extends String> structureIds;
    private boolean useMinecraftVillageFallback;
    private int searchRadius;

    public void registerConfig() {
        Path configPath = Path.of(CONFIG_FILE);

        // Load or create config file with comments enabled
        CommentedFileConfig config = CommentedFileConfig.builder(configPath)
                .autosave()
                .preserveInsertionOrder()
                .build();
        config.load();

        // Ensure keys exist with defaults and comments
        if (!config.contains("villageTags")) {
            config.set("villageTags", List.of("#minecraft:village"));
            config.setComment("villageTags",
                              "List of village IDs and/or tags to search for as spawn targets.\n" +
                              "Examples: #minecraft:village or minecraft:village_plains.\n" +
                              "This is the same input used for the /locate structure command.");
        }
        structureIds = config.get("villageTags");

        if (!config.contains("useMinecraftVillagesAsFallback")) {
            config.set("useMinecraftVillagesAsFallback", true);
            config.setComment("useMinecraftVillagesAsFallback",
                              "If true, vanilla Minecraft villages will be used as a fallback when no custom village tags match.");
        }
        useMinecraftVillageFallback = config.get("useMinecraftVillagesAsFallback");

        if (!config.contains("villageSearchRadius")) {
            config.set("villageSearchRadius", 2000);
            config.setComment("villageSearchRadius",
                              "The distance (in blocks) to search from (0, 0, 0) for a village spawn point.");
        }
        searchRadius = config.get("villageSearchRadius");

        config.save();
    }

    @Override
    public int GetSearchRadius() {
        return searchRadius;
    }

    @Override
    public Boolean UseVanillaFallback() {
        return useMinecraftVillageFallback;
    }

    @Override
    public List<? extends String> GetStructureList() {
        return structureIds;
    }
}
