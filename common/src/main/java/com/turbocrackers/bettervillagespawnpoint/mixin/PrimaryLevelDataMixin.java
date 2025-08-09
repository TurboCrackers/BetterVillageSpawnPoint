package com.turbocrackers.bettervillagespawnpoint.mixin;

import com.turbocrackers.bettervillagespawnpoint.CommonClass;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PrimaryLevelData.class)
public abstract class PrimaryLevelDataMixin
{
    @Shadow public abstract int getXSpawn();
    @Shadow public abstract int getYSpawn();
    @Shadow public abstract int getZSpawn();

    @Shadow private int xSpawn;
    @Shadow private int ySpawn;
    @Shadow private int zSpawn;

    @Inject(method = "getXSpawn", at = @At("RETURN"), cancellable = true)
    private void overrideGetXSpawn(CallbackInfoReturnable<Integer> cir)
    {
        BlockPos current_shared_spawn_pos = new BlockPos( this.xSpawn, this.ySpawn, this.zSpawn );

        // Are we still using the village spawn pos?
        if( current_shared_spawn_pos.equals(CommonClass.m_VillageLocator.GetVillageSpawnPos()) )
        {
            cir.setReturnValue( this.xSpawn );
            cir.cancel();
        }
        // If the level's spawn position is different from this mod's custom position, do the original logic.
    }

    @Inject(method = "getYSpawn", at = @At("RETURN"), cancellable = true)
    private void overrideGetYSpawn(CallbackInfoReturnable<Integer> cir)
    {
        BlockPos current_shared_spawn_pos = new BlockPos( this.xSpawn, this.ySpawn, this.zSpawn );

        // Are we still using the village spawn pos?
        if( current_shared_spawn_pos.equals(CommonClass.m_VillageLocator.GetVillageSpawnPos()) )
        {
            cir.setReturnValue( this.ySpawn );
            cir.cancel();
        }
        // If the level's spawn position is different from this mod's custom position, do the original logic.
    }

    @Inject(method = "getZSpawn", at = @At("RETURN"), cancellable = true)
    private void overrideGetZSpawn(CallbackInfoReturnable<Integer> cir)
    {
        BlockPos current_shared_spawn_pos = new BlockPos( this.xSpawn, this.ySpawn, this.zSpawn );

        // Are we still using the village spawn pos?
        if( current_shared_spawn_pos.equals(CommonClass.m_VillageLocator.GetVillageSpawnPos()) )
        {
            cir.setReturnValue( this.zSpawn );
            cir.cancel();
        }
        // If the level's spawn position is different from this mod's custom position, do the original logic.
    }
}
