package com.turbocrackers.bettervillagespawnpoint.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Optional;

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

    public VillageSpawnPointState m_State = VillageSpawnPointState.NOT_STARTED;
    public BlockPos m_VillageSpawnPos = BlockPos.ZERO;
    public Boolean m_SentFailureMessage = false;
    public VillageSpawnPointFailureReason m_FailureReason = VillageSpawnPointFailureReason.NONE;
    public ArrayList<Block> m_BlockWhitelist = new ArrayList<Block>();
    public BlockPos m_VillagePos = BlockPos.ZERO;
    public String m_VillageID = "";


    public static SpawnInitData load(CompoundTag tag, HolderLookup.@NotNull Provider provider)
    {
        SpawnInitData data = new SpawnInitData();
        data.m_State = VillageSpawnPointState.valueOf(String.valueOf(tag.getString("VillageSpawnPointState")));
        data.m_VillageSpawnPos = new BlockPos(tag.getInt("VillageSpawnPosX"), tag.getInt("VillageSpawnPosY"), tag.getInt("VillageSpawnPosZ"));
        data.m_VillagePos = new BlockPos(tag.getInt("VillagePosX"), tag.getInt("VillagePosY"), tag.getInt("VillagePosZ"));
        data.m_VillageID = String.valueOf(tag.getString("VillageID"));
        data.m_SentFailureMessage = tag.getBoolean("VillageSpawnFailureMessageSent");
        data.m_FailureReason = VillageSpawnPointFailureReason.valueOf(tag.getString("VillageSpawnFailureReason"));

        // Load the block whitelist
        data.m_BlockWhitelist = new ArrayList<>();
        if (tag.contains("BlockWhitelist", Tag.TAG_LIST)) {
            ListTag blockListTag = tag.getList("BlockWhitelist", Tag.TAG_STRING);
            for (int i = 0; i < blockListTag.size(); i++) {
                String blockIdString = blockListTag.getString(i);
                ResourceLocation blockId = ResourceLocation.tryParse(blockIdString);
                if( blockId == null )
                    continue;

                Block block = BuiltInRegistries.BLOCK.get(blockId);
                data.m_BlockWhitelist.add(block);
            }
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider provider)
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
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            listTag.add(StringTag.valueOf(blockId.toString()));
        }
        tag.put("BlockWhitelist", listTag);
        return tag;
    }

    public static SpawnInitData get( ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        SpawnInitData::new,     // supplier: creates new data if none exists
                        SpawnInitData::load,    // loader: reads from NBT
                        DataFixTypes.LEVEL      // type: pick a relevant DataFixTypes, LEVEL is common
                ),
                "bettervillagespawnpoint_spawn_data" // the actual save file name
                                                                   );
    }
}