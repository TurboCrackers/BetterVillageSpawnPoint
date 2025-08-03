package com.turbocrackers.bettervillagespawnpoint.mixin;

import com.turbocrackers.bettervillagespawnpoint.CommonClass;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin
{
    @Inject(method = "getSharedSpawnPos", at = @At("HEAD"), cancellable = true)
    public void overrideGetSharedSpawnPos(CallbackInfoReturnable<BlockPos> cir)
    {
        Level level = (Level)(Object)this;
        LevelData level_data = level.getLevelData();
        BlockPos current_shared_spawn_pos = level_data.getSpawnPos();

        // Are we still using the village spawn pos?
        if( current_shared_spawn_pos.equals(CommonClass.m_VillageLocator.GetVillageSpawnPos()) )
        {
            cir.setReturnValue( current_shared_spawn_pos );
            cir.cancel();
        }
        // If the level's spawn position is different from this mod's custom position, do the original logic.
    }
}
