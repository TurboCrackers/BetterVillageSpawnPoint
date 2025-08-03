package com.turbocrackers.bettervillagespawnpoint.mixin;

import com.turbocrackers.bettervillagespawnpoint.CommonClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin
{
   @Inject(method = "adjustSpawnLocation", at = @At("HEAD"), cancellable = true)
   private void overrideAdjustSpawnLocation(ServerLevel level, BlockPos pos, CallbackInfoReturnable<BlockPos> cir)
   {
       ServerPlayer player = (ServerPlayer)(Object)this;

       // Use our village spawn position if that's still what we're using for server spawn.
       if( level.getSharedSpawnPos().equals(CommonClass.m_VillageLocator.GetVillageSpawnPos()) )
       {
           CommonClass.m_VillageLocator.RefreshSpawnPos(level);
           BlockPos shared_spawn_pos = level.getSharedSpawnPos();

           //player.setPos( shared_spawn_pos.getX() + 0.5, shared_spawn_pos.getY() + 0.1, shared_spawn_pos.getZ() + 0.5 );
           cir.setReturnValue( shared_spawn_pos );
           cir.cancel();
       }
       // Otherwise, let the original logic happen (it does voodoo to find a safe spot).
   }
}
