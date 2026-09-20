package com.turbocrackers.bettervillagespawnpoint;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;
import java.util.List;

/**
 * 1.12.2 uses Forge's annotation config (config/BetterVillageSpawnPoint-common.cfg). The option
 * names match the newer branches' TOML so a config can be carried across versions by hand.
 */
@Config(modid = Constants.MOD_ID, name = "BetterVillageSpawnPoint-common")
public class ForgeConfig extends CommonConfig
{
    @Config.Name("villageTags")
    @Config.Comment({
            "List of structure names to search for as spawn targets, as the chunk generator knows them.",
            "1.12.2 has no structure registry or tags: vanilla names are Village, Mineshaft, Stronghold, Temple, Monument, Mansion and Fortress,",
            "and mods that add villages use their own names (as accepted by /locate).",
            "A namespaced entry from a newer config (minecraft:village, #minecraft:village) is mapped to the vanilla name."})
    public static String[] villageTags = { "Village" };

    @Config.Name("exclusions")
    @Config.Comment({
            "Blacklist: structures that must never be used as the spawn point, even if villageTags (or the vanilla fallback) would otherwise pick them.",
            "Uses the same format as villageTags. Each entry can be an exact name (Village) or a wildcard pattern (*Village*).",
            "This also applies to the vanilla fallback if you are using it."})
    public static String[] exclusions = {};

    @Config.Name("useMinecraftVillagesAsFallback")
    @Config.Comment("If you customized the villageTags list and no spawn point could be found, minecraft's vanilla villages will be used as a fallback.")
    public static boolean useMinecraftVillagesAsFallback = true;

    @Config.Name("villageSearchRadius")
    @Config.Comment({
            "The distance we should search from (0, 0, 0) for a village.",
            "Vanilla 1.12.2 walks outward one village region (32 chunks) at a time and gives up after 100 regions, so this caps",
            "the search in the same units as the newer branches: villageSearchRadius / 16 regions."})
    public static int villageSearchRadius = 2000;

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID)
    private static class Handler
    {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event)
        {
            if (event.getModID().equals(Constants.MOD_ID))
            {
                ConfigManager.sync(Constants.MOD_ID, Config.Type.INSTANCE);
            }
        }
    }

    @Override
    public int GetSearchRadius() { return villageSearchRadius; }

    @Override
    public Boolean UseVanillaFallback() { return useMinecraftVillagesAsFallback; }

    @Override
    public List<? extends String> GetStructureList() { return Arrays.asList(villageTags); }

    @Override
    public List<? extends String> GetExclusionsList() { return Arrays.asList(exclusions); }
}
