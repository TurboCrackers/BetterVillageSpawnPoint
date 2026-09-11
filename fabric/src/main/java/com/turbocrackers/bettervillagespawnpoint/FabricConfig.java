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
    // Tooltip: List of village tags or IDs to search for as spawn targets.
    // Examples: #minecraft:village or minecraft:village_plains.
    // The leading '#' on a tag is optional: an entry that isn't a structure ID is tried as a tag.
    // Same input used for the /locate structure command.

    @ConfigEntry.Gui.Tooltip
    public List<String> exclusions = List.of();
    // Tooltip: Blacklist. Structures that must never be used as the spawn point, even if
    // villageTags (or the vanilla fallback) would otherwise pick them. Uses the same format
    // as villageTags. Each entry can be a tag (#minecraft:village, which excludes every
    // structure in that tag), an exact ID (minecraft:village_snowy), or a wildcard pattern
    // (idas:*, *:village_snowy*). The leading '#' on a tag is optional: an entry that isn't
    // a structure ID is tried as a tag.
    // Example: villageTags = [ "#minecraft:village", "#idas:village" ]
    //          exclusions  = [ "#idas:desert_village", "minecraft:village_snowy", "ctov:*" ]

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
