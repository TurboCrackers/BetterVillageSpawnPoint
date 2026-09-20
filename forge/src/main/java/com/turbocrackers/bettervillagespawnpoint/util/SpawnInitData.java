package com.turbocrackers.bettervillagespawnpoint.util;

import com.turbocrackers.bettervillagespawnpoint.Constants;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;

public class SpawnInitData extends WorldSavedData
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
     * "No position recorded". Deliberately NOT BlockPos.ORIGIN: the spawn placement decides whether
     * to take over by comparing the world spawn against ours, and a world whose spawn really is
     * 0,0,0 would collide with a ZERO sentinel. A y of Integer.MIN_VALUE cannot be a real spawn.
     */
    public static final BlockPos NO_POS = new BlockPos(0, Integer.MIN_VALUE, 0);

    // NBT tag type ids
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;

    public boolean m_Initialized = false;
    public VillageSpawnPointState m_State = VillageSpawnPointState.NOT_STARTED;
    public BlockPos m_VillageSpawnPos = BlockPos.ORIGIN;
    // 1.12.2's world spawn has no facing, so the "face the village" yaw is kept here instead.
    public float m_VillageSpawnYaw = 0.0F;
    public Boolean m_SentFailureMessage = false;
    public VillageSpawnPointFailureReason m_FailureReason = VillageSpawnPointFailureReason.NONE;
    public ArrayList<Block> m_BlockWhitelist = new ArrayList<Block>();
    public BlockPos m_VillagePos = BlockPos.ORIGIN;
    public String m_VillageID = "";
    public static String SAVE_DATA_ID = "bettervillagespawnpoint_spawn_data";

    public SpawnInitData()
    {
        super(SAVE_DATA_ID);
    }

    // MapStorage instantiates saved data reflectively through this constructor.
    public SpawnInitData(String name)
    {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        Constants.LOG.info("[Better Village Spawn Point] Spawn data loaded.");
        m_State = EnumOrDefault(VillageSpawnPointState.class, tag.getString("VillageSpawnPointState"), VillageSpawnPointState.NOT_STARTED);
        m_VillageSpawnPos = new BlockPos(tag.getInteger("VillageSpawnPosX"), tag.getInteger("VillageSpawnPosY"), tag.getInteger("VillageSpawnPosZ"));
        m_VillageSpawnYaw = tag.getFloat("VillageSpawnYaw");
        m_VillagePos = new BlockPos(tag.getInteger("VillagePosX"), tag.getInteger("VillagePosY"), tag.getInteger("VillagePosZ"));
        m_VillageID = tag.getString("VillageID");
        m_SentFailureMessage = tag.getBoolean("VillageSpawnFailureMessageSent");
        m_FailureReason = EnumOrDefault(VillageSpawnPointFailureReason.class, tag.getString("VillageSpawnFailureReason"), VillageSpawnPointFailureReason.NONE);

        // Load the block whitelist
        m_BlockWhitelist = new ArrayList<>();
        if (tag.hasKey("BlockWhitelist", TAG_LIST)) {
            NBTTagList blockListTag = tag.getTagList("BlockWhitelist", TAG_STRING);
            for (int i = 0; i < blockListTag.tagCount(); i++) {
                String blockIdString = blockListTag.getStringTagAt(i);
                Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockIdString));

                if (block != null && block != Blocks.AIR) { // Ensure it's valid
                    m_BlockWhitelist.add(block);
                }
            }
        }
    }

    /**
     * Tolerate a name we do not recognise instead of failing the whole load. Failure reasons have
     * been added and renamed across versions, and one stale name in an old save should not cost
     * the player their recorded village spawn.
     */
    private static <E extends Enum<E>> E EnumOrDefault(Class<E> type, String name, E fallback)
    {
        try
        {
            return Enum.valueOf(type, name);
        }
        catch (IllegalArgumentException ignored)
        {
            return fallback;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag)
    {
        tag.setString("VillageSpawnPointState", m_State.name());
        tag.setInteger( "VillageSpawnPosX", m_VillageSpawnPos.getX() );
        tag.setInteger( "VillageSpawnPosY", m_VillageSpawnPos.getY() );
        tag.setInteger( "VillageSpawnPosZ", m_VillageSpawnPos.getZ() );
        tag.setFloat( "VillageSpawnYaw", m_VillageSpawnYaw );
        tag.setInteger( "VillagePosX", m_VillagePos.getX() );
        tag.setInteger( "VillagePosY", m_VillagePos.getY() );
        tag.setInteger( "VillagePosZ", m_VillagePos.getZ() );
        tag.setString( "VillageID", m_VillageID );
        tag.setBoolean( "VillageSpawnFailureMessageSent", m_SentFailureMessage );
        tag.setString("VillageSpawnFailureReason", m_FailureReason.name());
        NBTTagList listTag = new NBTTagList();
        for (Block block : m_BlockWhitelist)
        {
            ResourceLocation blockId = block.getRegistryName();
            if (blockId != null)
                listTag.appendTag(new NBTTagString(blockId.toString()));
        }
        tag.setTag("BlockWhitelist", listTag);
        return tag;
    }

    /**
     * Marks the record dirty and flushes it now. The callers set the spawn once per world (or on
     * the rare re-find when the recorded spawn stopped being valid), so paying for an immediate
     * write is worth not losing the result to a crash and re-running the whole search on next load.
     */
    public static void save(WorldServer level)
    {
        MinecraftServer server = level.getMinecraftServer();
        Runnable flush = () ->
        {
            SpawnInitData data = get(level);
            data.markDirty();
            level.getPerWorldStorage().saveAllData();
        };
        // addScheduledTask runs the task immediately when already on the server thread.
        if (server != null)
            server.addScheduledTask(flush);
        else
            flush.run();
    }

    public static SpawnInitData get(WorldServer level)
    {
        // The overworld's per-world storage: <world>/data/bettervillagespawnpoint_spawn_data.dat,
        // the same file name every branch up to 26.1 used.
        MapStorage storage = level.getPerWorldStorage();
        SpawnInitData data = (SpawnInitData) storage.getOrLoadData(SpawnInitData.class, SAVE_DATA_ID);
        if (data == null)
        {
            data = new SpawnInitData();
            storage.setData(SAVE_DATA_ID, data);
        }
        return data;
    }
}
