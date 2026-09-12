package com.turbocrackers.bettervillagespawnpoint.mixin;

import com.turbocrackers.bettervillagespawnpoint.CommonClass;
import com.turbocrackers.bettervillagespawnpoint.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Since 1.21.9 a joining player's spawn is no longer chosen through ServerPlayer.adjustSpawnLocation.
 * It is chosen during the connection's configuration phase (PrepareSpawnTask), which calls this
 * static finder with the world spawn and lets it wander the spawn radius looking for a "safe"
 * spot on the heightmap -- which is exactly how players end up in a tree next to the village.
 * When the suggested spawn is the village spawn this mod picked, hand back that block untouched.
 */
@Mixin(PlayerSpawnFinder.class)
public abstract class PlayerSpawnFinderMixin
{
    @Inject(method = "findSpawn", at = @At("HEAD"), cancellable = true)
    private static void overrideFindSpawn(ServerLevel level, BlockPos spawn_suggestion, CallbackInfoReturnable<CompletableFuture<Vec3>> cir)
    {
        // Use our village spawn position if that's still what we're using for server spawn.
        if( spawn_suggestion.equals(CommonClass.m_VillageLocator.GetVillageSpawnPos()) )
        {
            CommonClass.m_VillageLocator.RefreshSpawnPos(level);
            BlockPos spawn_pos = level.getRespawnData().pos();
            Constants.LOG.info("[Better Village Spawn Point] Placing new player at the village spawn {} instead of letting vanilla search around it.", spawn_pos.toShortString());

            cir.setReturnValue( CompletableFuture.completedFuture( Vec3.atBottomCenterOf(spawn_pos) ) );
            cir.cancel();
        }
        // Otherwise, let the original logic happen (it does voodoo to find a safe spot).
    }
}
