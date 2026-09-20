package com.turbocrackers.bettervillagespawnpoint;

import com.turbocrackers.bettervillagespawnpoint.command.DebugCommands;
import com.turbocrackers.bettervillagespawnpoint.util.BlockDebugger;
import com.turbocrackers.bettervillagespawnpoint.util.SpawnPlacement;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;

@Mod(modid = Constants.MOD_ID,
     name = Constants.MOD_NAME,
     version = Constants.MOD_VERSION,
     acceptedMinecraftVersions = "[1.12.2]",
     // The mod only acts on the server; clients without it can still join.
     acceptableRemoteVersions = "*")
public class BetterVillageSpawnPointForge
{
    public BetterVillageSpawnPointForge()
    {
        // Init our common class
        CommonClass.init();

        // Load config (Forge's annotation config is populated by FML; this just exposes it)
        CommonClass.m_Config = new ForgeConfig();

        // Start listening for Forge events.
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event)
    {
        if (BlockDebugger.DEBUG_ENABLED)
        {
            DebugCommands.Register(event);
        }
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event)
    {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        server.addScheduledTask(() -> CommonClass.m_VillageLocator.FindVillageAndSpawn(server));
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerLoggedInEvent event)
    {
        if (event.player instanceof EntityPlayerMP)
        {
            CommonClass.SendErrorMessageIfNeeded( (EntityPlayerMP)event.player );
        }
    }

    /**
     * 1.12.2 has no Mixin on Forge, so the two places the newer branches hook through
     * ServerPlayer.fudgeSpawnLocation are handled as events instead. This one is the new-player
     * case: it fires from PlayerList.readPlayerDataFromFile, after the EntityPlayerMP constructor
     * has already scattered the player around the world spawn and before anything is sent to the
     * client, so moving the player here is invisible.
     */
    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event)
    {
        if (event.getEntityPlayer() instanceof EntityPlayerMP)
        {
            SpawnPlacement.OnPlayerLoaded( (EntityPlayerMP)event.getEntityPlayer(), event.getPlayerDirectory(), event.getPlayerUUID() );
        }
    }

    /**
     * The respawn case (death, or leaving the End) with no usable bed. Vanilla has already put the
     * new player entity at a random spot around the world spawn and sent the respawn packet, so
     * the player is teleported onto the village spawn.
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerRespawnEvent event)
    {
        if (event.player instanceof EntityPlayerMP)
        {
            SpawnPlacement.OnPlayerRespawned( (EntityPlayerMP)event.player );
        }
    }
}
