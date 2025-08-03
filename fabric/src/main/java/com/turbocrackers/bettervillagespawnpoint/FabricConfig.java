package com.turbocrackers.bettervillagespawnpoint;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.List;

@Config(name = "bettervillagespawnpoint")
public class FabricConfig extends CommonConfig implements ConfigData
{
    @ConfigEntry.Gui.Tooltip
    public List<String> villageTags = List.of("#minecraft:village");
    // Tooltip: List of village IDs or tags to search for as spawn targets.
    // Examples: #minecraft:village or minecraft:village_plains.
    // Same input used for the /locate structure command.

    @ConfigEntry.Gui.Tooltip
    public boolean useMinecraftVillagesAsFallback = true;
    // Tooltip: If true, vanilla Minecraft villages will be used as fallback
    // when no custom village tags match.

    @ConfigEntry.Gui.Tooltip
    public int villageSearchRadius = 2000;
    // Tooltip: The distance (in blocks) to search from (0, 0, 0) for a village spawn point.

    public int GetSearchRadius() {
        return villageSearchRadius;
    }

    public Boolean UseVanillaFallback() {
        return useMinecraftVillagesAsFallback;
    }

    public List<String> GetStructureList() {
        return villageTags;
    }
}
