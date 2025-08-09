package com.turbocrackers.bettervillagespawnpoint;

import com.turbocrackers.bettervillagespawnpoint.util.BlockDebugger;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;

@Mod(Constants.MOD_ID)
public class BetterVillageSpawnPointForge
{
    public BetterVillageSpawnPointForge()
    {
        // Init our common class
        CommonClass.init();

        // Load config
        CommonClass.m_Config = new ForgeConfig();
        ModLoadingContext context = ModLoadingContext.get();
        ((ForgeConfig)CommonClass.m_Config).RegisterConfig(context);

        // Start listening for Forge events.
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event)
    {
        event.getServer().execute(() -> CommonClass.m_VillageLocator.FindVillageAndSpawn(event.getServer()));
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event)
    {
        if (BlockDebugger.DEBUG_ENABLED)
        {
            event.getDispatcher().register(
                    Commands.literal("showBlockDebug")
                            .requires(source -> source.hasPermission(2))
                            .executes(context ->
                                      {
                                          ServerLevel level = context.getSource().getLevel();
                                          CommonClass.m_BlockDebugger.ToggleBlockFailureDebug(level, true);
                                          return 1;
                                      }));

            event.getDispatcher().register(
                    Commands.literal("hideBlockDebug")
                            .requires(source -> source.hasPermission(2))
                            .executes(context ->
                                      {
                                          ServerLevel level = context.getSource().getLevel();
                                          CommonClass.m_BlockDebugger.ToggleBlockFailureDebug(level, false);
                                          return 1;
                                      }));

            event.getDispatcher().register(
                    Commands.literal("spawn")
                            .requires(source -> source.hasPermission(2))
                            .executes(context ->
                                      {
                                          net.minecraft.server.level.ServerLevel overworld = context.getSource().getServer().overworld();
                                          var data = overworld.getLevelData();
                                          BlockPos shared_spawn_pos = new BlockPos( data.getXSpawn(), data.getYSpawn(), data.getZSpawn() );
                                          Objects.requireNonNull(context.getSource().getPlayerOrException()).teleportTo(shared_spawn_pos.getX() + 0.5, shared_spawn_pos.getY() + 0.1, shared_spawn_pos.getZ() + 0.5);
                                          return 1;
                                      }));
        }
    }
}