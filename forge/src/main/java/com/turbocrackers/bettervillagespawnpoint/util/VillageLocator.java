package com.turbocrackers.bettervillagespawnpoint.util;

import com.turbocrackers.bettervillagespawnpoint.CommonClass;
import com.turbocrackers.bettervillagespawnpoint.Constants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockStem;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkGeneratorSettings;
import net.minecraft.world.gen.FlatGeneratorInfo;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.fluids.IFluidBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 1.12.2 port. There is no structure registry: a structure is a name the chunk generator answers
 * to ("Village", "Mansion", and whatever a village mod registers), found through
 * IChunkGenerator.getNearestStructurePos and read back from the generator's MapGenStructureData.
 * Block tags do not exist either, so the block-class families stand in for them. Everything
 * else -- the outward chunk walk over the village's bounding box, the per-block checks, the
 * saved record and the refresh on join -- is the same search as the newer branches.
 */
public class VillageLocator
{
    private BlockPos m_VillageSpawnPos = SpawnInitData.NO_POS;
    private float m_VillageSpawnYaw = 0.0F;
    private SpawnInitData.VillageSpawnPointState m_VillageSpawnPointGenerationState = SpawnInitData.VillageSpawnPointState.NOT_STARTED;
    private SpawnInitData.VillageSpawnPointFailureReason m_VillageSpawnPointFailureReason = SpawnInitData.VillageSpawnPointFailureReason.NONE;

    public SpawnInitData.VillageSpawnPointFailureReason GetFailureReason()
    {
        return m_VillageSpawnPointFailureReason;
    }
    private ArrayList<Block> m_BlockWhitelist = new ArrayList<>();
    private BlockPos m_VillagePos = SpawnInitData.NO_POS;
    private String m_VillageID = "";
    // The debug commands read CommonClass.m_BlockDebugger, so record into that same instance.
    private final BlockDebugger m_BlockDebugger = CommonClass.m_BlockDebugger;

    // Vanilla 1.12.2 structure names, keyed by their lower-case form so a namespaced entry from a
    // newer config ("minecraft:village", "#minecraft:village") maps onto the name the generator uses.
    private static final String[] VANILLA_STRUCTURE_NAMES = { "Village", "Mineshaft", "Stronghold", "Temple", "Monument", "Mansion", "Fortress", "EndCity" };
    private static final String VANILLA_VILLAGE = "Village";

    /** The generator's getNearestStructurePos predicts village regions; this many duds are checked before giving up on a name. */
    private static final int MAX_STRUCTURE_CANDIDATES = 8;

    private static final class Candidate
    {
        final String name;
        final BlockPos pos;
        final double dist_sq;
        Candidate(String name, BlockPos pos, double dist_sq) { this.name = name; this.pos = pos; this.dist_sq = dist_sq; }
    }

    /**
     * The newer branches hand the chunk generator every configured structure and let it pick the
     * nearest. 1.12.2 can only ask for one name at a time, so ask for each and keep whichever is
     * nearest to the origin.
     */
    private boolean FindNearestVillageAndSpawn(WorldServer level, List<String> structure_names, BlockPos origin, int searchRadius)
    {
        // We are searching in chunks- NOT blocks.
        searchRadius = searchRadius / 16;
        // The newer branches pass that chunk count to a search that steps one village region (32
        // chunks) per ring, so it is really a region count. Keep the same arithmetic: a candidate
        // further out than searchRadius regions is out of range.
        long max_distance_blocks = (long) searchRadius * 32L * 16L;

        Candidate nearest = null;
        for (String name : structure_names)
        {
            Candidate candidate = FindStructure(level, name, origin, max_distance_blocks);
            if (candidate != null && (nearest == null || candidate.dist_sq < nearest.dist_sq))
                nearest = candidate;
        }
        if (nearest == null)
            return false;

        Constants.LOG.info("[Better Village Spawn Point] Found {} at {}", nearest.name, nearest.pos);
        return findSpawnPosInVillage(level, nearest.pos, nearest.name);
    }

    /**
     * getNearestStructurePos only predicts the region a structure will start in; the start itself
     * exists once that chunk has generated, and a village can still turn out too small to be placed.
     * Generate the chunk, read the start back, and if there is none move on to the next unexplored
     * candidate, which is how the newer branches' search (which only returns real starts) behaves.
     */
    private Candidate FindStructure(WorldServer level, String name, BlockPos origin, long max_distance_blocks)
    {
        boolean find_unexplored = false;
        for (int attempt = 0; attempt < MAX_STRUCTURE_CANDIDATES; ++attempt)
        {
            BlockPos pos = level.getChunkProvider().getNearestStructurePos(level, name, origin, find_unexplored);
            if (pos == null)
                return null;

            long distance = Math.max(Math.abs((long) pos.getX() - origin.getX()), Math.abs((long) pos.getZ() - origin.getZ()));
            if (distance > max_distance_blocks)
            {
                Constants.LOG.info("[Better Village Spawn Point] The nearest {} is {} blocks out, beyond villageSearchRadius. Ignoring it.", name, distance);
                return null;
            }

            int chunk_x = pos.getX() >> 4;
            int chunk_z = pos.getZ() >> 4;
            // Generating the chunk creates the structure start -- or proves there is none.
            level.getChunk(chunk_x, chunk_z);
            StructureStart start = LoadStructureStart(level, name, chunk_x, chunk_z);
            if (start != null && start.isSizeableStructure())
            {
                return new Candidate(name, pos, pos.distanceSq(origin));
            }

            Constants.LOG.info("[Better Village Spawn Point] The predicted {} at chunk {}, {} did not produce a structure; looking further out.", name, chunk_x, chunk_z);
            find_unexplored = true;
        }
        return null;
    }

    /**
     * Every MapGenStructure writes its starts into a MapGenStructureData named after the structure,
     * keyed "[chunkX,chunkZ]". Reading that back is the only loader-agnostic way at a StructureStart
     * on 1.12.2 (the generator's own map is private), and it works for any mod that registers its
     * start class with MapGenStructureIO.
     */
    private static StructureStart LoadStructureStart(WorldServer level, String name, int chunk_x, int chunk_z)
    {
        MapGenStructureData data = (MapGenStructureData) level.getPerWorldStorage().getOrLoadData(MapGenStructureData.class, name);
        if (data == null)
            return null;

        NBTTagCompound starts = data.getTagCompound();
        String key = "[" + chunk_x + "," + chunk_z + "]";
        if (!starts.hasKey(key, 10))
            return null;

        return MapGenStructureIO.getStructureStart(starts.getCompoundTag(key), level);
    }

    public BlockPos GetVillageSpawnPos()
    {
        return m_VillageSpawnPos;
    }

    public float GetVillageSpawnYaw()
    {
        return m_VillageSpawnYaw;
    }

    public void RefreshSpawnPos(WorldServer level)
    {
        // If it wasn't valid, load the village position from save data if we have it
        SpawnInitData save_data = SpawnInitData.get(level);
        if( save_data.m_State.equals( SpawnInitData.VillageSpawnPointState.SUCCESS ) )
        {
            // Make sure the chunk is loaded before we check if the spawn pos is valid
            ChunkPos chunk_pos_spawn_pos = new ChunkPos(save_data.m_VillageSpawnPos.getX() >> 4, save_data.m_VillageSpawnPos.getZ() >> 4);
            level.getChunk(chunk_pos_spawn_pos.x, chunk_pos_spawn_pos.z);

            if( IsValidSpawnPos( level, save_data.m_VillageSpawnPos.down(), m_BlockWhitelist ) )
                return;

            ChunkPos chunk_pos_village_start = new ChunkPos(save_data.m_VillagePos.getX() >> 4, save_data.m_VillagePos.getZ() >> 4);
            level.getChunk(chunk_pos_village_start.x, chunk_pos_village_start.z);
            findSpawnPosInVillage( level, save_data.m_VillagePos, m_VillageID );
        }
        // If it failed, we shouldn't be here. But if we ended up here anyway, just return.
        else if( save_data.m_State.equals( SpawnInitData.VillageSpawnPointState.FAILURE ) )
        {
            return;
        }
        // We haven't searched for a position yet, so do that now. But only do it if the server hasn't already loaded.
        else if( level.getMinecraftServer() != null && level.getMinecraftServer().serverIsInRunLoop() )
        {
            FindVillageAndSpawn( level.getMinecraftServer() );
        }
    }

    private Boolean findSpawnPosInVillage(WorldServer level, BlockPos nearest_village_coords, String nearest_village_tag_or_id)
    {
        m_BlockDebugger.Clear(); // one search's worth of results at a time

        // Forcibly load the chunk the village point is in
        ChunkPos chunk_pos = new ChunkPos(nearest_village_coords.getX() >> 4, nearest_village_coords.getZ() >> 4);
        level.getChunk(chunk_pos.x, chunk_pos.z);

        // Search within that chunk for the village
        StructureStart village_start = LoadStructureStart(level, nearest_village_tag_or_id, chunk_pos.x, chunk_pos.z);
        if (village_start == null)
        {
            Constants.LOG.error("[Better Village Spawn Point] How did we get to the point of pre-generating a chunk and the structure start wasn't found?? Something is very wrong.");
            OnFailedToGenerateSpawnPos(level, SpawnInitData.VillageSpawnPointFailureReason.UNKNOWN_LOADING_ERROR);
            return false;
        }
        StructureBoundingBox village_bounding_box = village_start.getBoundingBox();
        int max_section = village_bounding_box.maxY >> 4;
        int min_section = village_bounding_box.minY >> 4;
        int center_section = CenterOf(village_bounding_box).getY() >> 4;

        // Setup block whitelist for special cases. The newer branches whitelist the floor blocks of
        // Lio's spiral tower village; that mod has no 1.12.2 release, so nothing is listed here yet.
        ArrayList<Block> block_whitelist = new ArrayList<>();

        ChunkPos minChunk = new ChunkPos(village_bounding_box.minX >> 4, village_bounding_box.minZ >> 4);
        ChunkPos maxChunk = new ChunkPos(village_bounding_box.maxX >> 4, village_bounding_box.maxZ >> 4);
        ChunkPos startChunk = new ChunkPos(village_start.getChunkPosX(), village_start.getChunkPosZ());
        int maxChunkRadius = Math.max(2, Math.max(Math.abs( maxChunk.x - startChunk.x ), Math.abs( maxChunk.z - startChunk.z )));
        for (int chunk_radius = 0; chunk_radius < maxChunkRadius; ++chunk_radius)
        {
            for (int chunk_dx = -chunk_radius; chunk_dx <= chunk_radius; ++chunk_dx)
            {
                for (int chunk_dz = -chunk_radius; chunk_dz <= chunk_radius; ++chunk_dz)
                {
                    // Skip inner area
                    if (Math.abs(chunk_dx) != chunk_radius && Math.abs(chunk_dz) != chunk_radius)
                    {
                        continue;
                    }

                    int chunk_x = startChunk.x + chunk_dx;
                    int chunk_z = startChunk.z + chunk_dz;

                    // Because our radius is probably bigger in either X or Z, clamp it.
                    if (chunk_x >= minChunk.x && chunk_x <= maxChunk.x && chunk_z >= minChunk.z && chunk_z <= maxChunk.z)
                    {
                        // GENERATE THE CHUNK
                        EnsurePopulated(level, chunk_x, chunk_z);

                        int chunkCenterX = (chunk_x << 4) + 8; // chunkX * 16 + 8
                        int chunkCenterZ = (chunk_z << 4) + 8; // chunkZ * 16 + 8

                        // Starting from the center, see if the ocean floor is anywhere in here as a freebie
                        for (int block_radius = 0; block_radius < 8; ++block_radius)
                        {
                            for (int x_offset = -block_radius; x_offset < block_radius; ++x_offset)
                            {
                                for (int z_offset = -block_radius; z_offset < block_radius; ++z_offset)
                                {
                                    int ocean_floor_y = OceanFloorHeight(level, chunkCenterX + x_offset, chunkCenterZ + z_offset) - 1;
                                    if (ocean_floor_y < village_bounding_box.maxY && ocean_floor_y > village_bounding_box.minY)
                                    {
                                        BlockPos test_pos = new BlockPos(chunkCenterX + x_offset, ocean_floor_y, chunkCenterZ + z_offset);
                                        if (IsValidSpawnPos(level, test_pos, block_whitelist))
                                        {
                                            SetVillageSpawnPos(level, test_pos, nearest_village_coords, nearest_village_tag_or_id, village_bounding_box);
                                            return true;
                                        }
                                    }
                                }
                            }
                        }

                        // Fall back on iterating through sections
                        int max_y_section_offset = (max_section - min_section);
                        for (int section_y_offset = 0; section_y_offset < max_y_section_offset; ++section_y_offset)
                        {
                            // Try to find a valid position in this section
                            int up_section = center_section + section_y_offset;
                            if ((up_section <= max_section) && TryFindAndSetValidSpawnPosInSection(level, chunk_x, up_section, chunk_z, village_bounding_box, block_whitelist, nearest_village_coords, nearest_village_tag_or_id))
                            {
                                return true;
                            }
                            int down_section = center_section - section_y_offset;
                            if ((down_section >= min_section) && TryFindAndSetValidSpawnPosInSection(level, chunk_x, down_section, chunk_z, village_bounding_box, block_whitelist, nearest_village_coords, nearest_village_tag_or_id))
                            {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * The newer branches ask for a chunk at ChunkStatus.FULL, which includes its features. 1.12.2
     * places a chunk's features (a village's pieces among them) when the chunks around it exist,
     * offset by half a chunk, so loading the 3x3 neighbourhood is what gets this chunk's blocks in.
     */
    private static void EnsurePopulated(WorldServer level, int chunk_x, int chunk_z)
    {
        for (int dx = -1; dx <= 1; ++dx)
        {
            for (int dz = -1; dz <= 1; ++dz)
            {
                level.getChunk(chunk_x + dx, chunk_z + dz);
            }
        }
    }

    private static BlockPos CenterOf(StructureBoundingBox box)
    {
        return new BlockPos(box.minX + (box.maxX - box.minX + 1) / 2, box.minY + (box.maxY - box.minY + 1) / 2, box.minZ + (box.maxZ - box.minZ + 1) / 2);
    }

    // ---- heightmap stand-ins -----------------------------------------------------------------
    // 1.12.2 chunks only keep a sky-light heightmap. These reproduce the two heightmaps the newer
    // branches use, with the same "one above the block" convention as Level.getHeight.

    /** Like Heightmap.Types.WORLD_SURFACE: one above the topmost non-air block. */
    private static int WorldSurfaceHeight(World level, int x, int z)
    {
        Chunk chunk = level.getChunk(x >> 4, z >> 4);
        int y = chunk.getHeightValue(x & 15, z & 15); // one above the topmost light-blocking block
        // Glass, fences, plants and torches do not block light, so they can sit above that. Climb past them.
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (y < 256 && !level.isAirBlock(pos.setPos(x, y, z)))
        {
            ++y;
        }
        return y;
    }

    /** Like Heightmap.Types.OCEAN_FLOOR_WG: one above the topmost block that blocks motion, ignoring fluids and leaves. */
    private static int OceanFloorHeight(World level, int x, int z)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = WorldSurfaceHeight(level, x, z) - 1; y >= 0; --y)
        {
            Material material = level.getBlockState(pos.setPos(x, y, z)).getMaterial();
            if (material.blocksMovement() && !material.isLiquid() && material != Material.LEAVES)
            {
                return y + 1;
            }
        }
        return 0;
    }

    private Boolean TryFindAndSetValidSpawnPosInSection(WorldServer level, int section_x, int section_y, int section_z, StructureBoundingBox village_bounding_box, ArrayList<Block> block_whitelist, BlockPos village_pos, String village_id)
    {
        if (IsSectionUnderwater(level, section_x, section_y, section_z, village_bounding_box))
        {
            Constants.LOG.info("[Better Village Spawn Point] ERROR: Section is underwater.");
            return false;
        }

        int min_y = Math.max(village_bounding_box.minY, section_y << 4);
        int max_y = Math.min(village_bounding_box.maxY, (section_y << 4) + 15);

        int section_center_x = (section_x << 4) + 8;
        int section_center_z = (section_z << 4) + 8;
        BlockPos.MutableBlockPos test_pos = new BlockPos.MutableBlockPos();
        for (int block_radius = 0; block_radius <= 8; ++block_radius)
        {
            for (int block_dx = -block_radius; block_dx <= block_radius; ++block_dx)
            {
                for (int block_dz = -block_radius; block_dz <= block_radius; ++block_dz)
                {
                    // Skip inner area
                    if (Math.abs(block_dx) != block_radius && Math.abs(block_dz) != block_radius)
                    {
                        continue;
                    }

                    int block_x = section_center_x + block_dx;
                    int block_z = section_center_z + block_dz;
                    int world_surface_y = WorldSurfaceHeight(level, block_x, block_z) - 1;
                    for (int block_y = min_y; block_y <= max_y; ++block_y)
                    {
                        // Don't search any higher than the highest block in the world. There's nothing up there. I promise. :)
                        if (block_y > world_surface_y)
                        {
                            break;
                        }

                        test_pos.setPos(block_x, block_y, block_z);
                        if (IsValidSpawnPos(level, test_pos, block_whitelist))
                        {
                            SetVillageSpawnPos(level, test_pos, village_pos, village_id, village_bounding_box);
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private Boolean IsSectionUnderwater(WorldServer level, int section_x, int section_y, int section_z, StructureBoundingBox village_bounding_box)
    {
        int min_y = Math.max(village_bounding_box.minY, section_y << 4);
        int max_y = Math.min(village_bounding_box.maxY, (section_y << 4) + 15);
        int min_x = Math.max(village_bounding_box.minX, section_x << 4);
        int max_x = Math.min(village_bounding_box.maxX, (section_x << 4) + 15);
        int min_z = Math.max(village_bounding_box.minZ, section_z << 4);
        int max_z = Math.min(village_bounding_box.maxZ, (section_z << 4) + 15);

        // Check to see if we're underwater
        BlockPos.MutableBlockPos water_test_pos = new BlockPos.MutableBlockPos();
        int water_block_count = 0;
        int INCREMENT_VAL = 4;
        int TOTAL_BLOCKS_TESTED = (int) Math.pow((double) 16 / INCREMENT_VAL, 3);
        double percent_for_underwater = 0.5;
        int NUM_BLOCKS_FOR_UNDERWATER = (int) (percent_for_underwater * (double) TOTAL_BLOCKS_TESTED);
        for (int block_y = max_y; block_y >= min_y; block_y -= INCREMENT_VAL)
        {
            for (int block_x = min_x; block_x < max_x; block_x += INCREMENT_VAL)
            {
                for (int block_z = min_z; block_z < max_z; block_z += INCREMENT_VAL)
                {
                    int world_surface_y = WorldSurfaceHeight(level, block_x, block_z) - 1;

                    // We KNOW this position isn't underwater if it's above the top-most non-air block (which includes water)
                    if (world_surface_y < block_y)
                    {
                        continue;
                    }

                    water_test_pos.setPos(block_x, block_y, block_z);
                    if (IsFluid(level.getBlockState(water_test_pos)))
                    {
                        ++water_block_count;

                        if (water_block_count >= NUM_BLOCKS_FOR_UNDERWATER)
                        {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // Block tags do not exist on 1.12.2; the block-class families are the closest equivalent and,
    // like the tags, cover modded blocks built on the vanilla classes.
    private static final ArrayList<Block> BODY_BLOCK_BLACKLIST = new ArrayList<>(Arrays.asList(Blocks.REEDS,        // sugar cane
                                                                                               Blocks.DOUBLE_PLANT)); // tall grass and the other two-high plants

    private static final ArrayList<Class<? extends Block>> BODY_BLOCK_CLASS_BLACKLIST = new ArrayList<>(Arrays.asList(BlockFence.class,
                                                                                                                      BlockWall.class,
                                                                                                                      BlockSapling.class,
                                                                                                                      BlockCrops.class,   // wheat, carrots, potatoes, beetroot
                                                                                                                      BlockStem.class     // melon and pumpkin stems
                                                                                                                     ));

    private static final ArrayList<Block> GROUND_BLOCK_BLACKLIST = new ArrayList<>(Arrays.asList(Blocks.FARMLAND,
                                                                                                 Blocks.HAY_BLOCK,
                                                                                                 Blocks.MAGMA,
                                                                                                 Blocks.CACTUS,
                                                                                                 Blocks.BROWN_MUSHROOM_BLOCK,
                                                                                                 Blocks.RED_MUSHROOM_BLOCK,
                                                                                                 Blocks.CAULDRON
                                                                                                ));
    private static final ArrayList<Class<? extends Block>> GROUND_BLOCK_CLASS_BLACKLIST = new ArrayList<>(Arrays.asList(BlockTrapDoor.class,
                                                                                                                        BlockLeaves.class,
                                                                                                                        BlockFence.class,
                                                                                                                        BlockFenceGate.class,
                                                                                                                        BlockWall.class,
                                                                                                                        BlockStairs.class,
                                                                                                                        BlockBed.class,
                                                                                                                        BlockRailBase.class,
                                                                                                                        BlockAnvil.class));

    private static boolean IsAnyOf(Block block, List<Class<? extends Block>> families)
    {
        for (Class<? extends Block> family : families)
        {
            if (family.isInstance(block))
                return true;
        }
        return false;
    }

    private static boolean IsFluid(IBlockState state)
    {
        return state.getMaterial().isLiquid() || state.getBlock() instanceof BlockLiquid || state.getBlock() instanceof IFluidBlock;
    }

    /** VoxelShape.isEmpty() stand-in: does the block have any collision box at all? */
    private static boolean HasCollision(World level, BlockPos pos)
    {
        return level.getBlockState(pos).getCollisionBoundingBox(level, pos) != Block.NULL_AABB;
    }

    /** "Collides and is not carpet": the newer branches' movement test. */
    private static boolean BlocksMovement(World level, BlockPos pos)
    {
        return HasCollision(level, pos) && !(level.getBlockState(pos).getBlock() instanceof BlockCarpet);
    }

    /** VoxelShape.max(Axis.Y) stand-in: the top of the block's collision box, relative to the block. */
    private static double CollisionTop(World level, BlockPos pos)
    {
        AxisAlignedBB box = level.getBlockState(pos).getCollisionBoundingBox(level, pos);
        return box == null ? 0.0 : box.maxY;
    }

    private Boolean IsValidSpawnPos(World level, BlockPos pos, ArrayList<Block> block_whitelist)
    {
        IBlockState ground_state = level.getBlockState(pos);

        boolean is_whitelisted = block_whitelist.isEmpty() || block_whitelist.contains(ground_state.getBlock());
        if (!is_whitelisted)
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.GROUND_NOT_WHITELISTED);
            return false;
        }

        // Check ground block
        if (GROUND_BLOCK_BLACKLIST.contains(ground_state.getBlock()))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.GROUND_BLACKLISTED);
            return false;
        }

        // Check ground block family (the tag check on the newer branches)
        if (IsAnyOf(ground_state.getBlock(), GROUND_BLOCK_CLASS_BLACKLIST))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.GROUND_BLACKLISTED);
            return false;
        }

        IBlockState bottom = level.getBlockState(pos.up(1));
        IBlockState top = level.getBlockState(pos.up(2));

        // Make sure there's not a tall plant in our body.
        if (bottom.getBlock() instanceof BlockDoublePlant)
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.TALL_PLANT_IN_BODY);
            return false;
        }

        // Make sure nothing that's blacklisted is intersecting our body
        if (BODY_BLOCK_BLACKLIST.contains(bottom.getBlock()))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.BODY_BLACKLISTED);
            return false;
        }

        // Check for blocks intersecting body
        if (IsAnyOf(bottom.getBlock(), BODY_BLOCK_CLASS_BLACKLIST))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.BODY_BLACKLISTED);
            return false;
        }

        // Make sure there's only air where our head goes
        if (!level.isAirBlock(pos.up(2)))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.HEAD_NOT_EMPTY);
            return false;
        }

        // Check if the block we'd be standing on is either a block or a slab
        if (!ground_state.isFullCube() && !(CollisionTop(level, pos) >= 0.9))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.NOT_FULL_BLOCK);
            return false;
        }

        // Don't spawn on an interactable block with an inventory or special logic
        if (level.getTileEntity(pos) != null)
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.INTERACTABLE);
            return false;
        }

        // Make sure a bush isn't in our body
        if (top.getBlock() instanceof BlockBush || bottom.getBlock() instanceof BlockBush)
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.BUSH_IN_BODY);
            return false;
        }

        // Check if movement is blocked. Carpet has collision but is an exception
        if (BlocksMovement(level, pos.up(1)))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.MOVE_BLOCKED);
            return false;
        }

        // Make sure there's only air right above our head
        if (!level.isAirBlock(pos.up(3)))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.ABOVE_NOT_CLEAR);
            return false;
        }

        // We don't want to spawn standing in fluid
        if (IsFluid(ground_state) || IsFluid(bottom) || IsFluid(top))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.IN_WATER);
            return false;
        }

        // Don't spawn in a tree
        if (ground_state.getBlock() instanceof BlockLog)
        {
            int TREE_CHECK_RADIUS = 2;
            for (int x = -TREE_CHECK_RADIUS; x < TREE_CHECK_RADIUS; ++x)
            {
                for (int y = -TREE_CHECK_RADIUS; y < TREE_CHECK_RADIUS; ++y)
                {
                    for (int z = -TREE_CHECK_RADIUS; z < TREE_CHECK_RADIUS; ++z)
                    {
                        // Skip the block we're on
                        if (x == 0 && y == 0 && z == 0)
                        {
                            continue;
                        }

                        IBlockState test_block_state = level.getBlockState(pos.add(x, y, z));

                        if (test_block_state.getBlock() instanceof BlockLeaves)
                        {
                            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.TREE);
                            return false;
                        }
                    }
                }
            }
        }

        // Make sure the surrounding blocks are clear
        if (BlocksMovement(level, pos.up(1).north()) || BlocksMovement(level, pos.up(2).north()) ||
            BlocksMovement(level, pos.up(1).south()) || BlocksMovement(level, pos.up(2).south()) ||
            BlocksMovement(level, pos.up(1).east())  || BlocksMovement(level, pos.up(2).east())  ||
            BlocksMovement(level, pos.up(1).west())  || BlocksMovement(level, pos.up(2).west()))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.COLLISION_NSEW);
            return false;
        }

        // Make sure the surrounding blocks (DIAGONALS) are clear
        if (BlocksMovement(level, pos.up(1).north().east()) || BlocksMovement(level, pos.up(2).north().east()) ||
            BlocksMovement(level, pos.up(1).south().east()) || BlocksMovement(level, pos.up(2).south().east()) ||
            BlocksMovement(level, pos.up(1).north().west()) || BlocksMovement(level, pos.up(2).north().west()) ||
            BlocksMovement(level, pos.up(1).south().west()) || BlocksMovement(level, pos.up(2).south().west()))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.COLLISION_DIAG);
            return false;
        }

        // Don't spawn in a cave
        if (IsPosInEnclosedSpace(level, pos.up()))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.IN_CAVE);
            return false;
        }

        // make sure we're not on top of any structures in this village
        int sameLevelBlocks = 0;
        for (EnumFacing dir : EnumFacing.HORIZONTALS)
        {
            BlockPos neighbor = pos.offset(dir);
            if (CollisionTop(level, neighbor) >= 0.9)
            {
                sameLevelBlocks++;
            }
        }
        if (sameLevelBlocks < 4)
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.ON_EDGE);
            return false;
        }

        int HEIGHT_TEST_RADIUS = 4;
        int MAX_VALID_HEIGHT_DIFFERENCE = 20;
        for (int x = -HEIGHT_TEST_RADIUS; x < HEIGHT_TEST_RADIUS; x++)
        {
            for (int z = -HEIGHT_TEST_RADIUS; z < HEIGHT_TEST_RADIUS; z++)
            {
                int test_height = OceanFloorHeight(level, pos.getX() + x, pos.getZ() + z) - 1;
                if (((pos.getY() - test_height) < MAX_VALID_HEIGHT_DIFFERENCE))
                {
                    if (pos.getY() - 2 > test_height)
                    {
                        m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.ON_CLIFF);
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void SetVillageSpawnPos(WorldServer level, BlockPos pos, BlockPos village_pos, String village_id, StructureBoundingBox village_bounding_box)
    {
        CommonClass.NEEDS_ERROR_MESSAGE = false;
        m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.SUCCESS);
        m_VillageSpawnPos = pos.add(0, 1, 0); // bump the spawn pos up one because otherwise we'll spawn inside it.
        m_VillageSpawnPointGenerationState = SpawnInitData.VillageSpawnPointState.SUCCESS;
        m_VillageSpawnPointFailureReason = SpawnInitData.VillageSpawnPointFailureReason.NONE;
        m_VillagePos = village_pos;
        m_VillageID = village_id;

        // Face the player into the village instead of due south. Same convention as Entity.lookAt:
        // yaw 0 looks toward +Z and increases clockwise when seen from above. 1.12.2's world spawn
        // has no facing of its own, so the yaw is kept with the record and applied on placement.
        BlockPos village_center = CenterOf(village_bounding_box);
        double dx = village_center.getX() - m_VillageSpawnPos.getX();
        double dz = village_center.getZ() - m_VillageSpawnPos.getZ();
        m_VillageSpawnYaw = (dx == 0 && dz == 0) ? 0.0F : MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx, dz)));

        level.setSpawnPoint(m_VillageSpawnPos);
        Constants.LOG.info("[Better Village Spawn Point] Set spawn to {}, {}, {} (standing on {}, {}, {}), facing the village centre at {}, {}, {}",
                           m_VillageSpawnPos.getX(), m_VillageSpawnPos.getY(), m_VillageSpawnPos.getZ(),
                           pos.getX(), pos.getY(), pos.getZ(),
                           village_center.getX(), village_center.getY(), village_center.getZ());

        // The overworld is where we save our spawn data
        SpawnInitData data = SpawnInitData.get(level);
        data.m_State = m_VillageSpawnPointGenerationState;
        data.m_VillageSpawnPos = m_VillageSpawnPos;
        data.m_VillageSpawnYaw = m_VillageSpawnYaw;
        data.m_BlockWhitelist = m_BlockWhitelist;
        data.m_VillagePos = m_VillagePos;
        data.m_VillageID = m_VillageID;
        data.m_FailureReason = m_VillageSpawnPointFailureReason;
        SpawnInitData.save(level);
    }

    private void OnFailedToGenerateSpawnPos(WorldServer level, SpawnInitData.VillageSpawnPointFailureReason failure_reason)
    {
        CommonClass.NEEDS_ERROR_MESSAGE = true;
        Constants.LOG.error("[Better Village Spawn Point] Failed to find a spawn point for village. Failure reason: {}", failure_reason);
        m_VillageSpawnPos = SpawnInitData.NO_POS;
        m_VillageSpawnYaw = 0.0F;
        m_VillagePos = SpawnInitData.NO_POS;
        m_VillageID = "";
        m_VillageSpawnPointGenerationState = SpawnInitData.VillageSpawnPointState.FAILURE;
        m_VillageSpawnPointFailureReason = failure_reason;
        SpawnInitData data = SpawnInitData.get(level);
        data.m_State = m_VillageSpawnPointGenerationState;
        data.m_VillageSpawnPos = m_VillageSpawnPos;
        data.m_VillageSpawnYaw = m_VillageSpawnYaw;
        data.m_BlockWhitelist = m_BlockWhitelist;
        data.m_VillagePos = m_VillagePos;
        data.m_VillageID = m_VillageID;
        data.m_FailureReason = m_VillageSpawnPointFailureReason;
        SpawnInitData.save(level);
    }

    private Boolean IsPosInEnclosedSpace(World level, BlockPos pos)
    {
        BlockPos.MutableBlockPos test_pos_top = new BlockPos.MutableBlockPos();

        int y_positive_count = 0;
        for (int y_offset = 0; y_offset <= 5; ++y_offset)
        {
            test_pos_top.setPos(pos.add(0, y_offset + 3, 0));
            if (HasCollision(level, test_pos_top))
            {
                ++y_positive_count;
            }
        }

        return y_positive_count > 2;
    }

    // True while a village search is running on the server thread. The search generates chunks
    // synchronously, and chunk generation can call back into anything that asks for the world
    // spawn (our own spawn placement included, and other village mods do it too). If one of
    // those ever re-entered the search it would recurse on the server thread and freeze the
    // client with no crash to point at.
    private boolean m_SearchInProgress = false;

    public void FindVillageAndSpawn(MinecraftServer server)
    {
        if( m_SearchInProgress )
        {
            Constants.LOG.warn("[Better Village Spawn Point] Village search was re-entered while already running; ignoring the nested call.");
            return;
        }

        m_SearchInProgress = true;
        try
        {
            FindVillageAndSpawnInternal(server);
        }
        finally
        {
            m_SearchInProgress = false;
        }
    }

    private void FindVillageAndSpawnInternal(MinecraftServer server)
    {
        // Grab our level and make sure the dimension is valid.
        WorldServer level = server.getWorld(0);
        if( level == null )
        {
            Constants.LOG.error("[Better Village Spawn Point] There is no overworld on this server, so there is nowhere to place a village spawn. Skipping.");
            return;
        }

        // Only set the spawn point once
        SpawnInitData data = SpawnInitData.get(level);
        m_VillageSpawnPointGenerationState = data.m_State;
        m_VillageSpawnPointFailureReason = data.m_FailureReason;
        if (data.m_State != SpawnInitData.VillageSpawnPointState.NOT_STARTED)
        {
            Constants.LOG.info("[Better Village Spawn Point] Spawn data state is {}.", m_VillageSpawnPointGenerationState.toString());
            if (data.m_State == SpawnInitData.VillageSpawnPointState.SUCCESS)
            {
                // Restore the FULL record. m_VillagePos and m_VillageID were previously left at
                // their defaults here, so after a restart RefreshSpawnPos would re-find a spawn
                // using an empty village id, fail to resolve the structure, and permanently mark
                // the world FAILURE the first time the recorded spawn block stopped being valid.
                m_VillageSpawnPos = data.m_VillageSpawnPos;
                m_VillageSpawnYaw = data.m_VillageSpawnYaw;
                m_VillagePos = data.m_VillagePos;
                m_VillageID = data.m_VillageID;
                m_BlockWhitelist = data.m_BlockWhitelist;
            }
            else
            {
                m_VillageSpawnPointFailureReason = data.m_FailureReason;
            }
            return;
        }

        // Set up our list of structure names
        List<String> village_names = new ArrayList<>();

        // Populate the list of names
        List<String> structure_ids = new ArrayList<>(CommonClass.m_Config.GetStructureList());
        for (String raw_entry : structure_ids)
        {
            String config_entry = raw_entry.trim();
            if (config_entry.isEmpty())
                continue;

            if (config_entry.toLowerCase(java.util.Locale.ROOT).contains("underwater"))
            {
                Constants.LOG.warn("[Better Village Spawn Point] '{}' is underwater. This mod doesn't support spawning in an underwater village! Skipping.", config_entry);
                continue;
            }

            String name = NormalizeStructureName(config_entry);
            if (!name.equals(config_entry))
            {
                Constants.LOG.warn("[Better Village Spawn Point] 1.12.2 has no structure IDs or tags; reading '{}' as the structure name '{}'.", config_entry, name);
            }

            if( IsExcluded(name) )
            {
                continue;
            }

            if( !WillVillageIdEverGenerate( level, name ))
            {
                Constants.LOG.warn("[Better Village Spawn Point] Structure '{}' won't ever generate in this world (structure generation is off, or the world type does not place it). Skipping.", config_entry);
                continue;
            }

            if (!village_names.contains(name))
                village_names.add(name);
        }

        // If every configured entry was rejected there is nothing to look for, and running the
        // search anyway just burns a full-radius chunk scan to rediscover that. Say so plainly --
        // this is the case people hit on superflat worlds, where a modded structure is configured
        // but nothing in the world can ever place it.
        if( village_names.isEmpty() )
        {
            Constants.LOG.warn("[Better Village Spawn Point] None of the configured villageTags entries ({}) can generate in this world. Nothing to search for.", structure_ids);
        }

        if( village_names.isEmpty() || !FindNearestVillageAndSpawn(level, village_names, BlockPos.ORIGIN, CommonClass.m_Config.GetSearchRadius()) )
        {
            if( !CommonClass.m_Config.UseVanillaFallback() )
            {
                OnFailedToGenerateSpawnPos(level, SpawnInitData.VillageSpawnPointFailureReason.NO_VILLAGE_FOUND);
                return;
            }

            // The blacklist applies to the fallback too, otherwise excluding a village
            // just hands it back the moment the primary search comes up empty.
            List<String> vanilla_names = new ArrayList<>();
            if( WillVillageIdEverGenerate(level, VANILLA_VILLAGE) && !IsExcluded(VANILLA_VILLAGE) )
            {
                vanilla_names.add(VANILLA_VILLAGE);
            }

            // Same short-circuit for the fallback: with nothing placeable, the scan can only fail.
            if( vanilla_names.isEmpty() )
            {
                Constants.LOG.warn("[Better Village Spawn Point] No vanilla village can generate in this world either, so the fallback has nothing to search for.");
                OnFailedToGenerateSpawnPos(level, SpawnInitData.VillageSpawnPointFailureReason.VANILLA_FALLBACK_FAILED);
                return;
            }

            if (FindNearestVillageAndSpawn(level, vanilla_names, BlockPos.ORIGIN, CommonClass.m_Config.GetSearchRadius()))
            {
                return;
            }

            OnFailedToGenerateSpawnPos(level, SpawnInitData.VillageSpawnPointFailureReason.VANILLA_FALLBACK_FAILED);
        }
    }

    /**
     * Accepts the newer branches' spellings ("#minecraft:village", "minecraft:village") as well as
     * the bare 1.12.2 generator names, and returns the name the chunk generator answers to.
     */
    static String NormalizeStructureName(String entry)
    {
        String name = entry.startsWith("#") ? entry.substring(1) : entry;
        if (name.startsWith("minecraft:"))
            name = name.substring("minecraft:".length());

        for (String vanilla : VANILLA_STRUCTURE_NAMES)
        {
            if (vanilla.equalsIgnoreCase(name))
                return vanilla;
        }
        // Not a vanilla name; a modded generator's names are case-sensitive, so pass it on as-is.
        return name;
    }

    /**
     * The "exclusions" config list is a blacklist applied to everything villageTags expands to
     * AND to the vanilla-village fallback. It uses the same format as villageTags. Each entry can be:
     *   - an exact structure name:  Village
     *   - a wildcard pattern:       *Village*   ('*' matches any run of characters)
     * Namespaced and '#'-prefixed spellings are normalised the same way villageTags entries are.
     */
    public Boolean IsExcluded( String village_name )
    {
        for( String exclusion_entry : CommonClass.m_Config.GetExclusionsList() )
        {
            String entry = exclusion_entry.trim();
            if( entry.isEmpty() )
                continue;

            boolean matches;
            if( entry.contains("*") )
                matches = GlobToPattern(entry).matcher(village_name).matches() || GlobToPattern(NormalizeStructureName(entry)).matcher(village_name).matches();
            else
                matches = village_name.equals(entry) || village_name.equals(NormalizeStructureName(entry));

            if( matches )
            {
                Constants.LOG.info("[Better Village Spawn Point] '{}' was excluded by '{}'", village_name, entry);
                return true;
            }
        }
        return false;
    }

    private static final Map<String, Pattern> GLOB_CACHE = new ConcurrentHashMap<>();

    private static Pattern GlobToPattern( String glob )
    {
        return GLOB_CACHE.computeIfAbsent(glob, g ->
        {
            StringBuilder regex = new StringBuilder();
            for( String part : g.split("\\*", -1) )
            {
                if( regex.length() > 0 )
                    regex.append(".*");
                regex.append(Pattern.quote(part));
            }
            return Pattern.compile(regex.toString());
        });
    }

    /**
     * 1.12.2 cannot be asked whether a generator knows a structure name or which biomes host it, so
     * this checks what it can: the world's structures option, and for vanilla villages the world
     * type's own village switch (the superflat preset, or useVillages in customized settings).
     */
    Boolean WillVillageIdEverGenerate( WorldServer level, String structure_name )
    {
        // 1) server / world option
        if (!level.getWorldInfo().isMapFeaturesEnabled())
            return false;

        if (!structure_name.equals(VANILLA_VILLAGE))
            return true; // a modded structure: nothing more can be told until the generator is asked

        // 2) does this world type place villages at all?
        String generator_options = level.getWorldInfo().getGeneratorOptions();
        try
        {
            if (level.getWorldType() == WorldType.FLAT)
            {
                FlatGeneratorInfo flat = FlatGeneratorInfo.createFlatGeneratorFromString(generator_options);
                return flat.getWorldFeatures().containsKey("village");
            }
            if (generator_options != null && !generator_options.isEmpty())
            {
                return ChunkGeneratorSettings.Factory.jsonToFactory(generator_options).build().useVillages;
            }
        }
        catch (Exception e)
        {
            Constants.LOG.warn("[Better Village Spawn Point] Could not read the world's generator options; assuming villages can generate.", e);
        }
        return true;
    }
}
