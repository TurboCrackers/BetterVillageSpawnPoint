package com.turbocrackers.bettervillagespawnpoint.mixin;

import com.turbocrackers.bettervillagespawnpoint.CommonClass;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin
{
    // Pre-1.21.9 this hooked Level.getSharedSpawnPos, whose only extra behaviour over the raw
    // level data was clamping the spawn to the world border. That clamp is now its own method,
    // so hook that: when the spawn being adjusted is the village spawn we chose, hand it back
    // untouched instead of letting vanilla move it to the border centre.
    @Inject(method = "getWorldBorderAdjustedRespawnData", at = @At("HEAD"), cancellable = true)
    public void overrideGetWorldBorderAdjustedRespawnData(LevelData.RespawnData respawn_data, CallbackInfoReturnable<LevelData.RespawnData> cir)
    {
        // Are we still using the village spawn pos?
        if( respawn_data.pos().equals(CommonClass.m_VillageLocator.GetVillageSpawnPos()) )
        {
            cir.setReturnValue( respawn_data );
            cir.cancel();
        }
        // If the level's spawn position is different from this mod's custom position, do the original logic.
    }
}
