package com.turbocrackers.bettervillagespawnpoint;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ForgeConfig extends CommonConfig
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_IDS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUSION_IDS;
    private static final ForgeConfigSpec.ConfigValue<Boolean> USE_MINECRAFT_VILLAGE_FALLBACK;
    private static final ForgeConfigSpec.ConfigValue<Integer> SEARCH_RADIUS;

    static
    {
        STRUCTURE_IDS = BUILDER
                .comment("List of structure IDs to search for as spawn targets.\nExamples: minecraft:village or repurposed_structures:village_badlands.\nThis is the same input that you would use for the /locate command: open the game and type '/locate ' and scroll through the options to find the structure IDs you want.\n1.16.5 has no structure tags, and the per-biome village variants (minecraft:village_plains ...) cannot be searched for on this version; minecraft:village covers all of them.")
                .defineList(
                        "villageTags",
                        Arrays.asList(
                                "minecraft:village"
                               ),
                        o -> o instanceof String
                           );

        EXCLUSION_IDS = BUILDER
                .comment("\nBlacklist: structures that must never be used as the spawn point, even if villageTags (or the vanilla fallback) would otherwise pick them.\n" +
                         "Uses the same format as villageTags. Each entry can be an exact ID (minecraft:village) or a wildcard pattern (repurposed_structures:*, *:village_*).\n" +
                         "Example: villageTags = [ \"minecraft:village\", \"repurposed_structures:village_badlands\" ]\n" +
                         "         exclusions  = [ \"repurposed_structures:village_*\" ]\n" +
                         "This also applies to the vanilla fallback if you are using it." )
                .defineList(
                        "exclusions",
                        Collections.<String>emptyList(),
                        o -> o instanceof String
                           );

        USE_MINECRAFT_VILLAGE_FALLBACK = BUILDER
                .comment("If you customized the villageTags list and no spawn point could be found, minecraft's vanilla villages will be used as a fallback.")
                .define("useMinecraftVillagesAsFallback", true );

        SEARCH_RADIUS = BUILDER
                .comment("The distance we should search from (0, 0, 0) for a village.")
                .define("villageSearchRadius", 2000 );

        CONFIG = BUILDER.build();
    }

    public void RegisterConfig(ModLoadingContext context)
    {
        context.registerConfig(ModConfig.Type.COMMON, CONFIG, "BetterVillageSpawnPoint-common.toml");
    }

    @Override
    public int GetSearchRadius() { return SEARCH_RADIUS.get(); }

    @Override
    public Boolean UseVanillaFallback() { return USE_MINECRAFT_VILLAGE_FALLBACK.get(); }

    @Override
    public List<? extends String> GetStructureList() { return STRUCTURE_IDS.get(); }

    @Override
    public List<? extends String> GetExclusionsList() { return EXCLUSION_IDS.get(); }
}
