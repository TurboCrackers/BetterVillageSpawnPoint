package com.turbocrackers.bettervillagespawnpoint.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

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

    public boolean m_Initialized;
    public VillageSpawnPointState m_State;
    public BlockPos m_VillageSpawnPos;
    public BlockPos m_VillagePos;
    public String m_VillageID;
    public Boolean m_SentFailureMessage;
    public VillageSpawnPointFailureReason m_FailureReason;
    public ArrayList<Block> m_BlockWhitelist;

    /**
     * Every field is optionalFieldOf so that a save written by an older build -- which only ever
     * persisted "initialized" -- still loads instead of failing to parse and throwing the whole
     * record away. Missing fields fall back to the same defaults a fresh world starts with.
     */
    public static final Codec<SpawnInitData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("initialized", false)
                      .forGetter(data -> data.m_Initialized),
            EnumCodec(VillageSpawnPointState.class, VillageSpawnPointState.NOT_STARTED)
                      .optionalFieldOf("state", VillageSpawnPointState.NOT_STARTED)
                      .forGetter(data -> data.m_State),
            BlockPos.CODEC.optionalFieldOf("village_spawn_pos", NO_POS)
                      .forGetter(data -> data.m_VillageSpawnPos),
            BlockPos.CODEC.optionalFieldOf("village_pos", NO_POS)
                      .forGetter(data -> data.m_VillagePos),
            Codec.STRING.optionalFieldOf("village_id", "")
                      .forGetter(data -> data.m_VillageID),
            Codec.BOOL.optionalFieldOf("sent_failure_message", false)
                      .forGetter(data -> data.m_SentFailureMessage),
            EnumCodec(VillageSpawnPointFailureReason.class, VillageSpawnPointFailureReason.NONE)
                      .optionalFieldOf("failure_reason", VillageSpawnPointFailureReason.NONE)
                      .forGetter(data -> data.m_FailureReason),
            Codec.STRING.listOf().optionalFieldOf("block_whitelist", List.of())
                      .forGetter(SpawnInitData::GetBlockWhitelistIds)
                                                                                          ).apply(instance, SpawnInitData::new));

    /**
     * Enum codec that tolerates a name it does not recognise instead of failing the whole load.
     * Failure reasons have been added and renamed across versions, and one stale name in an old
     * save should not cost the player their recorded village spawn.
     */
    private static <E extends Enum<E>> Codec<E> EnumCodec(Class<E> type, E fallback)
    {
        return Codec.STRING.xmap(
                name ->
                {
                    try
                    {
                        return Enum.valueOf(type, name);
                    }
                    catch (IllegalArgumentException ignored)
                    {
                        return fallback;
                    }
                },
                Enum::name);
    }

    public SpawnInitData(boolean initialized)
    {
        this(initialized, VillageSpawnPointState.NOT_STARTED, NO_POS, NO_POS, "", false,
             VillageSpawnPointFailureReason.NONE, List.of());
    }

    public SpawnInitData(boolean initialized,
                         VillageSpawnPointState state,
                         BlockPos village_spawn_pos,
                         BlockPos village_pos,
                         String village_id,
                         boolean sent_failure_message,
                         VillageSpawnPointFailureReason failure_reason,
                         List<String> block_whitelist_ids)
    {
        this.m_Initialized = initialized;
        this.m_State = state;
        this.m_VillageSpawnPos = village_spawn_pos;
        this.m_VillagePos = village_pos;
        this.m_VillageID = village_id;
        this.m_SentFailureMessage = sent_failure_message;
        this.m_FailureReason = failure_reason;

        // Resolve block ids leniently: a block from a mod the player has since removed just drops
        // out of the whitelist rather than failing the load.
        this.m_BlockWhitelist = new ArrayList<>();
        for (String block_id_string : block_whitelist_ids)
        {
            ResourceLocation block_id = ResourceLocation.tryParse(block_id_string);
            if (block_id == null)
                continue;

            Block block = BuiltInRegistries.BLOCK.getValue(block_id);
            if (block != Blocks.AIR)
                this.m_BlockWhitelist.add(block);
        }
    }

    private List<String> GetBlockWhitelistIds()
    {
        List<String> ids = new ArrayList<>();
        for (Block block : m_BlockWhitelist)
        {
            ResourceLocation block_id = BuiltInRegistries.BLOCK.getKey(block);
            if (block_id != null)
                ids.add(block_id.toString());
        }
        return ids;
    }

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
            DataFixTypes.LEVEL
    );

    /**
     * Marks the record dirty and flushes it now. The callers set the spawn once per world (or on
     * the rare re-find when the recorded spawn stopped being valid), so paying for an immediate
     * write is worth not losing the result to a crash and re-running the whole search on next load.
     */
    public void save(ServerLevel level)
    {
        level.getServer().executeBlocking(() ->
        {
            setDirty();
            level.getDataStorage().saveAndJoin();
        });
    }
}
