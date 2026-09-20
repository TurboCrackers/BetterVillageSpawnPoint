package com.turbocrackers.bettervillagespawnpoint;

import com.turbocrackers.bettervillagespawnpoint.util.BlockDebugger;
import net.minecraft.command.Commands;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
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
    public void onServerStarted(FMLServerStartedEvent event)
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
                                          ServerWorld level = context.getSource().getLevel();
                                          CommonClass.m_BlockDebugger.ToggleBlockFailureDebug(level, true);
                                          return 1;
                                      }));

            event.getDispatcher().register(
                    Commands.literal("hideBlockDebug")
                            .requires(source -> source.hasPermission(2))
                            .executes(context ->
                                      {
                                          ServerWorld level = context.getSource().getLevel();
                                          CommonClass.m_BlockDebugger.ToggleBlockFailureDebug(level, false);
                                          return 1;
                                      }));

            event.getDispatcher().register(
                    Commands.literal("spawn")
                            .requires(source -> source.hasPermission(2))
                            .executes(context ->
                                      {
                                          ServerWorld overworld = context.getSource().getServer().overworld();
                                          BlockPos shared_spawn_pos = overworld.getSharedSpawnPos();
                                          Objects.requireNonNull(context.getSource().getPlayerOrException()).teleportTo(shared_spawn_pos.getX() + 0.5, shared_spawn_pos.getY() + 0.1, shared_spawn_pos.getZ() + 0.5);
                                          return 1;
                                      }));
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        CommonClass.SendErrorMessageIfNeeded( (ServerPlayerEntity)event.getPlayer() );
    }
}