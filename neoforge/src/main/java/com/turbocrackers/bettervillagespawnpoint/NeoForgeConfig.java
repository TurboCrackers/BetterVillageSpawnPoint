package com.turbocrackers.bettervillagespawnpoint;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class NeoForgeConfig extends CommonConfig
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec CONFIG;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_IDS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUSION_IDS;
    private static final ModConfigSpec.ConfigValue<Boolean> USE_MINECRAFT_VILLAGE_FALLBACK;
    private static final ModConfigSpec.ConfigValue<Integer> SEARCH_RADIUS;

    static
    {
        STRUCTURE_IDS = BUILDER
                .comment("List of village tags and/or IDs to search for as spawn targets.\nExamples: #minecraft:village or minecraft:village_plains.\nThe leading '#' on a tag is optional: an entry that isn't a structure ID is tried as a tag.\nThis is the same input that you would use for the /locate structure command\nTo find all of the village structure IDs/tags, open the game and type '/locate structure' and then scroll through the options to find structure IDs that you want. Use '/locate structure #' to find village tags.")
                .defineList(
                        "villageTags",
                        List.of(
                                "#minecraft:village"
                               ),
                        o -> o instanceof String
                           );

        EXCLUSION_IDS = BUILDER
                .comment("\nBlacklist: structures that must never be used as the spawn point, even if villageTags (or the vanilla fallback) would otherwise pick them.\n" +
                         "Uses the same format as villageTags. Each entry can be a tag (#minecraft:village, which excludes every structure in that tag),\n" +
                         "an exact ID (minecraft:village_snowy), or a wildcard pattern (idas:*, *:village_snowy*).\n" +
                         "The leading '#' on a tag is optional: an entry that isn't a structure ID is tried as a tag.\n" +
                         "Example: villageTags = [ \"#minecraft:village\", \"#idas:village\" ]\n" +
                         "         exclusions  = [ \"#idas:desert_village\", \"minecraft:village_snowy\", \"ctov:*\" ]\n" +
                         "This also applies to the vanilla fallback if you are using it." )
                .defineList(
                        "exclusions",
                        List.<String>of(),
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

    public void RegisterConfig(ModContainer container) { container.registerConfig(ModConfig.Type.COMMON, CONFIG, "BetterVillageSpawnPoint-common.toml"); }

    @Override
    public int GetSearchRadius() { return SEARCH_RADIUS.get(); }

    @Override
    public Boolean UseVanillaFallback() { return USE_MINECRAFT_VILLAGE_FALLBACK.get(); }

    @Override
    public List<? extends String> GetStructureList() { return STRUCTURE_IDS.get(); }

    @Override
    public List<? extends String> GetExclusionsList() { return EXCLUSION_IDS.get(); }
}
