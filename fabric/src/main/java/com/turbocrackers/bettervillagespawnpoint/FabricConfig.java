package com.turbocrackers.bettervillagespawnpoint;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Config(name = "bettervillagespawnpoint")
public class FabricConfig extends CommonConfig implements ConfigData
{
    @ConfigEntry.Gui.Tooltip
    public List<String> villageTags = new ArrayList<>(Arrays.asList("minecraft:village"));
    // Tooltip: List of structure IDs to search for as spawn targets.
    // Examples: minecraft:village or repurposed_structures:village_badlands.
    // Same input used for the /locate command. 1.16.5 has no structure tags, and the per-biome
    // village variants (minecraft:village_plains ...) cannot be searched for on this version.

    @ConfigEntry.Gui.Tooltip
    public List<String> exclusions = new ArrayList<>();
    // Tooltip: Blacklist. Structures that must never be used as the spawn point, even if
    // villageTags (or the vanilla fallback) would otherwise pick them. Uses the same format
    // as villageTags. Each entry can be an exact ID (minecraft:village) or a wildcard pattern
    // (repurposed_structures:*, *:village_*).
    // Example: villageTags = [ "minecraft:village", "repurposed_structures:village_badlands" ]
    //          exclusions  = [ "repurposed_structures:village_*" ]

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

    public List<String> GetExclusionsList() {
        return exclusions;
    }
}
