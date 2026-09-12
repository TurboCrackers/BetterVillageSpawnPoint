package com.turbocrackers.bettervillagespawnpoint;

import com.turbocrackers.bettervillagespawnpoint.util.BlockDebugger;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Objects;

@Mod(Constants.MOD_ID)
public class BetterVillageSpawnPointNeoForge
{

    public BetterVillageSpawnPointNeoForge(ModContainer container)
    {
        // Init our common class
        CommonClass.init();

        // Load config
        CommonClass.m_Config = new NeoForgeConfig();
        ((NeoForgeConfig)CommonClass.m_Config).RegisterConfig(container);

        // Start listening for NeoForge events.
        NeoForge.EVENT_BUS.register(this);
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
                                          ServerLevel level = context.getSource().getLevel();
                                          BlockPos shared_spawn_pos = level.getRespawnData().pos();
                                          Objects.requireNonNull(context.getSource().getPlayer()).teleportTo(shared_spawn_pos.getX() + 0.5, shared_spawn_pos.getY() + 0.1, shared_spawn_pos.getZ() + 0.5);
                                          return 1;
                                      }));
        }
    }
}