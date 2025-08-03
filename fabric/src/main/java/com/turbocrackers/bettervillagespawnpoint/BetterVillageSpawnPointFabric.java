package com.turbocrackers.bettervillagespawnpoint;

import com.mojang.brigadier.CommandDispatcher;
import com.turbocrackers.bettervillagespawnpoint.util.BlockDebugger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

public class BetterVillageSpawnPointFabric implements ModInitializer
{
    @Override
    public void onInitialize()
    {
        // Init our common class
        CommonClass.init();

        // Load config (Fabric version should be handled via a Fabric config library or your own loader)
        CommonClass.m_Config = new FabricConfig();
        ((FabricConfig)CommonClass.m_Config).registerConfig();

        // Server start event
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);

        // Register commands
        registerCommands();
    }

    private void onServerStarted(MinecraftServer server) {
        server.execute(() -> CommonClass.m_VillageLocator.FindVillageAndSpawn(server));
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (BlockDebugger.DEBUG_ENABLED) {
                registerDebugCommands(dispatcher);
            }
        });
    }

    private void registerDebugCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("showBlockDebug")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            CommonClass.m_BlockDebugger.ToggleBlockFailureDebug(level, true);
                            return 1;
                        })
                           );

        dispatcher.register(
                Commands.literal("hideBlockDebug")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            CommonClass.m_BlockDebugger.ToggleBlockFailureDebug(level, false);
                            return 1;
                        })
                           );

        dispatcher.register(
                Commands.literal("spawn")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            BlockPos sharedSpawnPos = level.getSharedSpawnPos();
                            Objects.requireNonNull(context.getSource().getPlayer())
                                    .teleportTo(sharedSpawnPos.getX() + 0.5, sharedSpawnPos.getY() + 0.1, sharedSpawnPos.getZ() + 0.5);
                            return 1;
                        })
                           );
    }
}
