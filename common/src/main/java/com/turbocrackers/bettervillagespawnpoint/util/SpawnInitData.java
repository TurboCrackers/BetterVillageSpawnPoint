package com.turbocrackers.bettervillagespawnpoint.util;

import com.turbocrackers.bettervillagespawnpoint.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.ArrayList;

public class SpawnInitData extends SavedData
{
    public enum VillageSpawnPointState
    {
        NOT_STARTED,
        SUCCESS,
        FAILURE
    }

    public enum VillageSpawnPointFailureReason
    {
        NONE,
        INVALID_STRUCTURE_ID,
        NO_VILLAGE_FOUND,
        VANILLA_VILLAGE_FOUND_BUT_NO_VALID_SPAWN_POS,
        VANILLA_FALLBACK_FAILED,
        VILLAGE_FOUND_BUT_NO_VALID_SPAWN_POS,
        STRUCTURE_FOUND_BUT_KEY_NOT_VALID,
        UNKNOWN_LOADING_ERROR
    }

    /**
     * "No position recorded". Deliberately NOT BlockPos.ZERO: the spawn mixins decide whether to
     * take over by comparing the level's shared spawn against ours, and a world whose spawn really
     * is 0,0,0 would collide with a ZERO sentinel. A y of Integer.MIN_VALUE cannot be a real spawn.
     */
    public static final BlockPos NO_POS = new BlockPos(0, Integer.MIN_VALUE, 0);

    public boolean m_Initialized = false;
    public VillageSpawnPointState m_State = VillageSpawnPointState.NOT_STARTED;
    public BlockPos m_VillageSpawnPos = BlockPos.ZERO;
    public Boolean m_SentFailureMessage = false;
    public VillageSpawnPointFailureReason m_FailureReason = VillageSpawnPointFailureReason.NONE;
    public ArrayList<Block> m_BlockWhitelist = new ArrayList<Block>();
    public BlockPos m_VillagePos = BlockPos.ZERO;
    public String m_VillageID = "";
    public static String SAVE_DATA_ID = "bettervillagespawnpoint_spawn_data";

    public static SpawnInitData load(CompoundTag tag) {
        Constants.LOG.info("[Better Village Spawn Point] Spawn data loaded.");
        SpawnInitData data = new SpawnInitData();
        data.m_State = VillageSpawnPointState.valueOf(tag.getString("VillageSpawnPointState"));
        data.m_VillageSpawnPos = new BlockPos(tag.getInt("VillageSpawnPosX"), tag.getInt("VillageSpawnPosY"), tag.getInt("VillageSpawnPosZ"));
        data.m_VillagePos = new BlockPos(tag.getInt("VillagePosX"), tag.getInt("VillagePosY"), tag.getInt("VillagePosZ"));
        data.m_VillageID = tag.getString("VillageID");
        data.m_SentFailureMessage = tag.getBoolean("VillageSpawnFailureMessageSent");
        data.m_FailureReason = VillageSpawnPointFailureReason.valueOf(tag.getString("VillageSpawnFailureReason"));

        // Load the block whitelist
        data.m_BlockWhitelist = new ArrayList<>();
        if (tag.contains("BlockWhitelist", Tag.TAG_LIST)) {
            ListTag blockListTag = tag.getList("BlockWhitelist", Tag.TAG_STRING);
            for (int i = 0; i < blockListTag.size(); i++) {
                String blockIdString = blockListTag.getString(i);
                ResourceLocation blockId = ResourceLocation.tryParse(blockIdString);
                Block block = Registry.BLOCK.get(blockId);

                if (block != Blocks.AIR) { // Ensure it's valid
                    data.m_BlockWhitelist.add(block);
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putString("VillageSpawnPointState", m_State.name());
        tag.putInt( "VillageSpawnPosX", m_VillageSpawnPos.getX() );
        tag.putInt( "VillageSpawnPosY", m_VillageSpawnPos.getY() );
        tag.putInt( "VillageSpawnPosZ", m_VillageSpawnPos.getZ() );
        tag.putInt( "VillagePosX", m_VillagePos.getX() );
        tag.putInt( "VillagePosY", m_VillagePos.getY() );
        tag.putInt( "VillagePosZ", m_VillagePos.getZ() );
        tag.putString( "VillageID", m_VillageID );
        tag.putBoolean( "VillageSpawnFailureMessageSent", m_SentFailureMessage );
        tag.putString("VillageSpawnFailureReason", m_FailureReason.name());
        ListTag listTag = new ListTag();
        for (Block block : m_BlockWhitelist)
        {
            ResourceLocation blockId = Registry.BLOCK.getKey(block);
            listTag.add(StringTag.valueOf(blockId.toString()));
        }
        tag.put("BlockWhitelist", listTag);
        return tag;
    }

    public static void save(ServerLevel level)
    {
        level.getServer().executeBlocking(() ->
        {
            SpawnInitData data = get(level);
            data.setDirty();

            level.getDataStorage().save();
        });
    }

    public static SpawnInitData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(SpawnInitData::load, SpawnInitData::new, SAVE_DATA_ID);
    }
}