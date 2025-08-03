package com.turbocrackers.bettervillagespawnpoint.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

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


    // Codec defines how to serialize/deserialize this class
    public static final Codec<SpawnInitData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("initialized").forGetter(data -> data.m_Initialized) ).apply(instance, SpawnInitData::new));

    public boolean m_Initialized = false;
    public VillageSpawnPointState m_State = VillageSpawnPointState.NOT_STARTED;
    public BlockPos m_VillageSpawnPos = BlockPos.ZERO;
    public Boolean m_SentFailureMessage = false;
    public VillageSpawnPointFailureReason m_FailureReason = VillageSpawnPointFailureReason.NONE;
    public ArrayList<Block> m_BlockWhitelist = new ArrayList<Block>();
    public BlockPos m_VillagePos = BlockPos.ZERO;
    public String m_VillageID = "";

    public boolean isInitialized()
    {
        return m_Initialized;
    }

    public void setInitialized(boolean initialized)
    {
        this.m_Initialized = initialized;
        this.setDirty();
    }

    public static SpawnInitData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static final SavedDataType<SpawnInitData> TYPE = new SavedDataType<>(
            "bettervillagespawnpoint_spawn_data",
            () -> new SpawnInitData(false),
            CODEC,
            DataFixTypes.LEVEL // or DataFixTypes.SAVED_DATA if you don't want migration
    );

    public SpawnInitData(boolean initialized)
    {
        this.m_Initialized = initialized;
    }

    public static SpawnInitData load(CompoundTag tag)
    {
        Boolean initialized = tag.getBoolean("is_initialized").get();
        SpawnInitData data = new SpawnInitData(initialized);
        data.m_State = VillageSpawnPointState.valueOf(String.valueOf(tag.getString("VillageSpawnPointState")));
        data.m_VillageSpawnPos = new BlockPos(tag.getInt("VillageSpawnPosX").get(), tag.getInt("VillageSpawnPosY").get(), tag.getInt("VillageSpawnPosZ").get());
        data.m_VillagePos = new BlockPos(tag.getInt("VillagePosX").get(), tag.getInt("VillagePosY").get(), tag.getInt("VillagePosZ").get());
        data.m_VillageID = String.valueOf(tag.getString("VillageID"));
        data.m_SentFailureMessage = tag.getBoolean("VillageSpawnFailureMessageSent").get();
        data.m_FailureReason = VillageSpawnPointFailureReason.valueOf(tag.getString("VillageSpawnFailureReason").get());

        // Load the block whitelist
        data.m_BlockWhitelist = new ArrayList<>();
        if (tag.contains("BlockWhitelist"))
        {
            ListTag blockListTag = tag.getList("BlockWhitelist").get();
            for (int i = 0; i < blockListTag.size(); i++)
            {
                String blockIdString = String.valueOf(blockListTag.getString(i));
                ResourceLocation blockId = ResourceLocation.parse(blockIdString);
                Block block = BuiltInRegistries.BLOCK.getValue(blockId);

                if (block != Blocks.AIR)
                {
                    data.m_BlockWhitelist.add(block);
                }
            }
        }
        return data;
    }

    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("is_initialized", this.m_Initialized);
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
}