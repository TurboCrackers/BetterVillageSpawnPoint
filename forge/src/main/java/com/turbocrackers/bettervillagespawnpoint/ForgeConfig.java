package com.turbocrackers.bettervillagespawnpoint;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import java.util.List;

public class ForgeConfig extends CommonConfig
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_IDS;
    private static final ForgeConfigSpec.ConfigValue<Boolean> USE_MINECRAFT_VILLAGE_FALLBACK;
    private static final ForgeConfigSpec.ConfigValue<Integer> SEARCH_RADIUS;

    static
    {
        STRUCTURE_IDS = BUILDER
                .comment("List of village IDs and/or tags to search for as spawn targets.\nExamples: #minecraft:village or minecraft:village_plains.\nThis is the same input that you would use for the /locate structure command\nTo find all of the village structure IDs/tags, open the game and type '/locate structure' and then scroll through the options to find structure IDs that you want. Use '/locate structure #' to find village tags.")
                .defineList(
                        "villageTags",
                        List.of(
                                "#minecraft:village"
                               ),
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
}
