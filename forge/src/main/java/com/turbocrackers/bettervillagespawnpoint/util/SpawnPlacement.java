package com.turbocrackers.bettervillagespawnpoint.util;

import com.turbocrackers.bettervillagespawnpoint.CommonClass;
import com.turbocrackers.bettervillagespawnpoint.Constants;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.io.File;

/**
 * 1.12.2 replacement for the ServerPlayerMixin the newer branches use. There the mixin sits on
 * ServerPlayer.fudgeSpawnLocation, which vanilla calls for a brand-new player and for a respawn
 * with no usable bed, and it puts the player exactly on the village spawn instead of letting
 * vanilla wander the spawn radius. Forge 1.12.2 has no Mixin, so the same two moments are caught
 * with Forge events and the same placement applied.
 */
public final class SpawnPlacement
{
    private SpawnPlacement() {}

    /** Same test as the mixins: only act while the world spawn is still the one this mod chose. */
    public static boolean IsWorldSpawnOurs(WorldServer overworld)
    {
        return overworld.getSpawnPoint().equals(CommonClass.m_VillageLocator.GetVillageSpawnPos());
    }

    /**
     * PlayerEvent.LoadFromFile fires for every login, new player or not. A new player is one with
     * no playerdata file; the singleplayer host is additionally stored in level.dat, so a host
     * with a saved position there is not new either.
     */
    public static void OnPlayerLoaded(EntityPlayerMP player, File player_directory, String player_uuid)
    {
        MinecraftServer server = player.getServer();
        if (server == null)
            return;

        if (new File(player_directory, player_uuid + ".dat").exists())
            return;

        WorldServer overworld = server.getWorld(0);
        if (player.getName().equals(server.getServerOwner()) && overworld.getWorldInfo().getPlayerNBTTagCompound() != null)
            return;

        PlaceAtVillageSpawn(player, false);
    }

    /**
     * PlayerRespawnEvent fires after vanilla has built the new player entity. Vanilla only records
     * a bed on the new entity when it actually respawned the player there (an obstructed or missing
     * bed clears it and sends the "home bed was missing or obstructed" message), so a bed on the
     * new entity means "leave the player alone" and no bed means the world-spawn path ran.
     */
    public static void OnPlayerRespawned(EntityPlayerMP player)
    {
        BlockPos bed = player.getBedLocation(player.dimension);
        if (bed != null && EntityPlayer.getBedSpawnLocation(player.world, bed, player.isSpawnForced(player.dimension)) != null)
            return;

        PlaceAtVillageSpawn(player, true);
    }

    /**
     * Mirrors ServerPlayerMixin.overrideFudgeSpawnLocation: refresh (or first find) the village
     * spawn, then stand the player on it, facing the village.
     */
    private static void PlaceAtVillageSpawn(EntityPlayerMP player, boolean already_in_world)
    {
        MinecraftServer server = player.getServer();
        if (server == null)
            return;

        WorldServer overworld = server.getWorld(0);
        // Use our village spawn position if that's still what we're using for server spawn.
        if (!IsWorldSpawnOurs(overworld))
            return; // Otherwise, let the original logic happen (it does voodoo to find a safe spot).

        CommonClass.m_VillageLocator.RefreshSpawnPos(overworld);
        BlockPos spawn_pos = overworld.getSpawnPoint();
        float yaw = CommonClass.m_VillageLocator.GetVillageSpawnYaw();
        double x = spawn_pos.getX() + 0.5;
        double y = spawn_pos.getY() + 0.1;
        double z = spawn_pos.getZ() + 0.5;

        if (already_in_world)
        {
            // The player and their client both exist; a connection teleport keeps them in step.
            player.connection.setPlayerLocation(x, y, z, yaw, 0.0F);
        }
        else
        {
            // Not yet added to the world or told about it; just move the entity.
            player.setLocationAndAngles(x, y, z, yaw, 0.0F);
        }
        Constants.LOG.info("[Better Village Spawn Point] Placing {} at the village spawn {} instead of letting vanilla search around it.", player.getName(), spawn_pos);
    }
}
