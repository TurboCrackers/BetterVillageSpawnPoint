package com.turbocrackers.bettervillagespawnpoint.util;

import com.turbocrackers.bettervillagespawnpoint.CommonClass;
import com.turbocrackers.bettervillagespawnpoint.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 1.16.5 port. Structures are {@link StructureFeature}s here (the same names /locate takes:
 * minecraft:village, minecraft:mansion, repurposed_structures:village_badlands ...). The per-biome
 * village variants (village_plains, village_snowy ...) are ConfiguredStructureFeatures that the
 * structure search cannot be pointed at, and structure tags do not exist before 1.18.2, so the
 * config lists take structure IDs only. Everything else is the same search as the newer branches.
 */
public class VillageLocator
{
    private BlockPos m_VillageSpawnPos = SpawnInitData.NO_POS;
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

    /**
     * The newer branches hand the chunk generator one HolderSet holding every configured structure
     * and let it pick the nearest. 1.16.5 can only search for one StructureFeature at a time, so
     * search for each and keep whichever start is nearest to the origin.
     */
    private boolean FindNearestVillageAndSpawn(ServerLevel level, List<StructureFeature<?>> structures, BlockPos origin, int searchRadius, boolean skipKnownStructures)
    {
        // We are searching in chunks- NOT blocks.
        searchRadius = searchRadius / 16;

        BlockPos nearest_pos = null;
        StructureFeature<?> nearest_structure = null;
        for (StructureFeature<?> structure : structures)
        {
            BlockPos pos = level.findNearestMapFeature(structure, origin, searchRadius, skipKnownStructures);
            if (pos == null)
                continue;

            if (nearest_pos == null || pos.distSqr(origin) < nearest_pos.distSqr(origin))
            {
                nearest_pos = pos;
                nearest_structure = structure;
            }
        }
        if (nearest_pos == null)
            return false;

        // Is valid?
        ResourceLocation id = Registry.STRUCTURE_FEATURE.getKey(nearest_structure);
        if (id == null)
        {
            return false;
        }

        Constants.LOG.info("[Better Village Spawn Point] Found {} at {}", id.toString(), nearest_pos);
        return findSpawnPosInVillage(level, nearest_pos, id.toString());
    }

    public BlockPos GetVillageSpawnPos()
    {
        return m_VillageSpawnPos;
    }

    public void RefreshSpawnPos(ServerLevel level)
    {
        // If it wasn't valid, load the village position from save data if we have it
        SpawnInitData save_data = SpawnInitData.get(level);
        if( save_data.m_State.equals( SpawnInitData.VillageSpawnPointState.SUCCESS ) )
        {
            // Make sure the chunk is loaded before we check if the spawn pos is valid
            ChunkPos chunk_pos_spawn_pos = new ChunkPos(save_data.m_VillageSpawnPos.getX() >> 4, save_data.m_VillageSpawnPos.getZ() >> 4);
            level.getChunk(chunk_pos_spawn_pos.x, chunk_pos_spawn_pos.z);

            if( IsValidSpawnPos( level, save_data.m_VillageSpawnPos.below(), m_BlockWhitelist ) )
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
        else if( level.getServer().isReady() )
        {
            FindVillageAndSpawn( level.getServer() );
        }
    }

    private Boolean findSpawnPosInVillage(ServerLevel level, BlockPos nearest_village_coords, String nearest_village_tag_or_id)
    {
        m_BlockDebugger.Clear(); // one search's worth of results at a time

        // Forcibly load the chunk the village point is in
        ChunkPos chunk_pos = new ChunkPos(nearest_village_coords.getX() >> 4, nearest_village_coords.getZ() >> 4);
        ChunkAccess chunk = level.getChunk(chunk_pos.x, chunk_pos.z);

        // Search within that chunk for the village
        ResourceLocation structureId = ResourceLocation.tryParse(nearest_village_tag_or_id);
        Optional<StructureFeature<?>> structure = structureId == null ? Optional.empty() : Registry.STRUCTURE_FEATURE.getOptional(structureId);
        if (!structure.isPresent())
        {
            Constants.LOG.error("[Better Village Spawn Point] How did we get to the point of pre-generating a chunk and the structure wasn't found?? Something is very wrong.");
            OnFailedToGenerateSpawnPos(level, SpawnInitData.VillageSpawnPointFailureReason.UNKNOWN_LOADING_ERROR);
            return false;
        }
        StructureStart<?> village_start = chunk.getStartForFeature(structure.get());
        if (village_start == null || !village_start.isValid())
        {
            Constants.LOG.error("[Better Village Spawn Point] How did we get to the point of pre-generating a chunk and the structure was found but the StructureStart wasn't?? Something is very wrong.");
            OnFailedToGenerateSpawnPos(level, SpawnInitData.VillageSpawnPointFailureReason.UNKNOWN_LOADING_ERROR);
            return false;
        }
        BoundingBox village_bounding_box = village_start.getBoundingBox();
        int max_section = SectionPos.blockToSectionCoord(village_bounding_box.y1);
        int min_section = SectionPos.blockToSectionCoord(village_bounding_box.y0);
        int center_section = SectionPos.blockToSectionCoord(CenterOf(village_bounding_box).getY());

        // Setup block whitelist for special cases
        ArrayList<Block> block_whitelist = new ArrayList<>();
        if (nearest_village_tag_or_id.equals("lios_outlandish_villages:spiral_tower_village") || nearest_village_tag_or_id.equals("lios_outlandish_villages:spiral_tower_village_sea"))
        {
            block_whitelist.add(Blocks.SPRUCE_PLANKS);
            block_whitelist.add(Blocks.STRIPPED_SPRUCE_WOOD);
            block_whitelist.add(Blocks.SPRUCE_WOOD);
            block_whitelist.add(Blocks.BRICKS);
            block_whitelist.add(Blocks.SPRUCE_LOG);
            block_whitelist.add(Blocks.SPRUCE_SLAB);
        }

        ChunkPos minChunk = new ChunkPos(village_bounding_box.x0 >> 4, village_bounding_box.z0 >> 4);
        ChunkPos maxChunk = new ChunkPos(village_bounding_box.x1 >> 4, village_bounding_box.z1 >> 4);
        ChunkPos startChunk = new ChunkPos(village_start.getChunkX(), village_start.getChunkZ());
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
                        level.getChunkSource().getChunk(chunk_x, chunk_z, ChunkStatus.FULL, true);

                        int chunkCenterX = (chunk_x << 4) + 8; // chunkX * 16 + 8
                        int chunkCenterZ = (chunk_z << 4) + 8; // chunkZ * 16 + 8

                        // Starting from the center, see if the ocean floor is anywhere in here as a freebie
                        for (int block_radius = 0; block_radius < 8; ++block_radius)
                        {
                            for (int x_offset = -block_radius; x_offset < block_radius; ++x_offset)
                            {
                                for (int z_offset = -block_radius; z_offset < block_radius; ++z_offset)
                                {
                                    int ocean_floor_y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, chunkCenterX + x_offset, chunkCenterZ + z_offset) - 1;
                                    if (ocean_floor_y < village_bounding_box.y1 && ocean_floor_y > village_bounding_box.y0)
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
                            SectionPos test_section_pos = SectionPos.of(chunk_x, up_section, chunk_z);
                            if ((up_section <= max_section) && TryFindAndSetValidSpawnPosInSection(level, test_section_pos, village_bounding_box, block_whitelist, nearest_village_coords, nearest_village_tag_or_id))
                            {
                                return true;
                            }
                            int down_section = center_section - section_y_offset;
                            test_section_pos = SectionPos.of(chunk_x, down_section, chunk_z);
                            if ((down_section >= min_section) && TryFindAndSetValidSpawnPosInSection(level, test_section_pos, village_bounding_box, block_whitelist, nearest_village_coords, nearest_village_tag_or_id))
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

    // 1.16.5 notes:
    //  - The newer branches read Heightmap.Types.OCEAN_FLOOR_WG. On 1.16.5 a fully generated chunk
    //    only keeps the post-worldgen heightmaps and getHeight throws for the _WG ones; OCEAN_FLOOR
    //    uses the same motion-blocking predicate, so the result is identical.
    //  - BlockPos.toShortString() is client-only on 1.16.5 (a dedicated server strips it), so log
    //    lines format the coordinates themselves.
    private static String Short(BlockPos pos)
    {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static BlockPos CenterOf(BoundingBox box)
    {
        return new BlockPos(box.x0 + (box.x1 - box.x0 + 1) / 2, box.y0 + (box.y1 - box.y0 + 1) / 2, box.z0 + (box.z1 - box.z0 + 1) / 2);
    }

    private Boolean TryFindAndSetValidSpawnPosInSection(ServerLevel level, SectionPos test_section_pos, BoundingBox village_bounding_box, ArrayList<Block> block_whitelist, BlockPos village_pos, String village_id)
    {
        if (IsSectionUnderwater(level, test_section_pos, village_bounding_box))
        {
            Constants.LOG.info("[Better Village Spawn Point] ERROR: Section is underwater.");
            return false;
        }

        int min_y = Math.max(village_bounding_box.y0, test_section_pos.minBlockY());
        int max_y = Math.min(village_bounding_box.y1, test_section_pos.maxBlockY());

        BlockPos curr_section_center_block_pos = test_section_pos.center();
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

                    int block_x = curr_section_center_block_pos.getX() + block_dx;
                    int block_z = curr_section_center_block_pos.getZ() + block_dz;
                    int world_surface_y = level.getHeight(Heightmap.Types.WORLD_SURFACE, block_x, block_z) - 1;
                    for (int block_y = min_y; block_y <= max_y; ++block_y)
                    {
                        // Don't search any higher than the highest block in the world. There's nothing up there. I promise. :)
                        if (block_y > world_surface_y)
                        {
                            break;
                        }

                        test_pos.set(block_x, block_y, block_z);
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

    private Boolean IsSectionUnderwater(ServerLevel level, SectionPos test_section_pos, BoundingBox village_bounding_box)
    {
        int min_y = Math.max(village_bounding_box.y0, test_section_pos.minBlockY());
        int max_y = Math.min(village_bounding_box.y1, test_section_pos.maxBlockY());
        int min_x = Math.max(village_bounding_box.x0, test_section_pos.minBlockX());
        int max_x = Math.min(village_bounding_box.x1, test_section_pos.maxBlockX());
        int min_z = Math.max(village_bounding_box.z0, test_section_pos.minBlockZ());
        int max_z = Math.min(village_bounding_box.z1, test_section_pos.maxBlockZ());

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
                    int world_surface_y = level.getHeight(Heightmap.Types.WORLD_SURFACE, block_x, block_z) - 1;

                    // We KNOW this position isn't underwater if it's above the top-most non-air block (which includes water)
                    if (world_surface_y < block_y)
                    {
                        continue;
                    }

                    water_test_pos.set(block_x, block_y, block_z);
                    if (level.getFluidState(water_test_pos).getAmount() > 0)
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

    private static final ArrayList<Block> BODY_BLOCK_BLACKLIST = new ArrayList<>(Arrays.asList(Blocks.SWEET_BERRY_BUSH,
                                                                                               Blocks.SUGAR_CANE,
                                                                                               Blocks.TALL_GRASS));

    private static final ArrayList<Tag<Block>> BODY_BLOCK_TAG_BLACKLIST = new ArrayList<>(Arrays.asList(BlockTags.FENCES,
                                                                                                        BlockTags.WALLS,
                                                                                                        BlockTags.SAPLINGS,
                                                                                                        BlockTags.CROPS
                                                                                                       ));

    // 1.16.5 has no cauldron tag (there is only the one cauldron block), so it is listed as a block.
    private static final ArrayList<Block> GROUND_BLOCK_BLACKLIST = new ArrayList<>(Arrays.asList(Blocks.FARMLAND,
                                                                                                 Blocks.HAY_BLOCK,
                                                                                                 Blocks.BELL,
                                                                                                 Blocks.MAGMA_BLOCK,
                                                                                                 Blocks.CACTUS,
                                                                                                 Blocks.BROWN_MUSHROOM_BLOCK,
                                                                                                 Blocks.RED_MUSHROOM_BLOCK,
                                                                                                 Blocks.MUSHROOM_STEM,
                                                                                                 Blocks.CAULDRON
                                                                                                ));
    private static final ArrayList<Tag<Block>> GROUND_BLOCK_TAG_BLACKLIST = new ArrayList<>(Arrays.asList(BlockTags.TRAPDOORS,
                                                                                                          BlockTags.LEAVES,
                                                                                                          BlockTags.FENCES,
                                                                                                          BlockTags.FENCE_GATES,
                                                                                                          BlockTags.WALLS,
                                                                                                          BlockTags.STAIRS,
                                                                                                          BlockTags.BEDS,
                                                                                                          BlockTags.CAMPFIRES,
                                                                                                          BlockTags.RAILS,
                                                                                                          BlockTags.ANVIL));

    private Boolean IsValidSpawnPos(Level level, BlockPos pos, ArrayList<Block> block_whitelist)
    {
        BlockState ground_state = level.getBlockState(pos);

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

        // Check ground block tag
        for (Tag<Block> block_type : GROUND_BLOCK_TAG_BLACKLIST)
        {
            if (ground_state.is(block_type))
            {
                m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.GROUND_BLACKLISTED);
                return false;
            }
        }

        BlockState bottom = level.getBlockState(pos.above(1));
        BlockState top = level.getBlockState(pos.above(2));
        BlockState above = level.getBlockState(pos.above(3));

        // Make sure there's not a tall plant in our body.
        if (bottom.getBlock() instanceof DoublePlantBlock)
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
        for (Tag<Block> block_type : BODY_BLOCK_TAG_BLACKLIST)
        {
            if (bottom.is(block_type))
            {
                m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.BODY_BLACKLISTED);
                return false;
            }
        }

        // Make sure there's only air where our head goes
        if (!top.isAir())
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.HEAD_NOT_EMPTY);
            return false;
        }

        // Check if the block we'd be standing on is either a block or a slab
        VoxelShape ground_collision_shape = ground_state.getCollisionShape(level, pos);
        if (!ground_state.getBlockSupportShape(level, pos).equals(Shapes.block()) && !(ground_collision_shape.max(Direction.Axis.Y) >= 0.9))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.NOT_FULL_BLOCK);
            return false;
        }

        // Don't spawn on an interactable block with an inventory or special logic
        if (level.getBlockEntity(pos) != null)
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.INTERACTABLE);
            return false;
        }

        // Make sure a bush isn't in our body
        if (top.getBlock() instanceof BushBlock || bottom.getBlock() instanceof BushBlock)
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.BUSH_IN_BODY);
            return false;
        }

        // Check if movement is blocked. Carpet has collision but is an exception
        if (!bottom.getCollisionShape(level, pos.above(1)).isEmpty() && !bottom.is(BlockTags.CARPETS))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.MOVE_BLOCKED);
            return false;
        }

        // Make sure there's only air right above our head
        if (!above.isAir())
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.ABOVE_NOT_CLEAR);
            return false;
        }

        // We don't want to spawn standing in fluid
        FluidState groundFluid = level.getFluidState(pos);
        FluidState bottomFluid = level.getFluidState(pos.above());
        FluidState topFluid = level.getFluidState(pos.above(2));
        if ((groundFluid.getAmount() > 0) || (bottomFluid.getAmount() > 0) || (topFluid.getAmount() > 0))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.IN_WATER);
            return false;
        }

        // Don't spawn in a tree
        if (ground_state.is(BlockTags.LOGS))
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

                        BlockState test_block_state = level.getBlockState(pos.offset(x, y, z));

                        if (test_block_state.is(BlockTags.LEAVES))
                        {
                            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.TREE);
                            return false;
                        }
                    }
                }
            }
        }

        // Make sure the surrounding blocks are clear
        BlockPos bottom_north_pos = pos.above(1).north();
        BlockPos top_north_pos = pos.above(2).north();
        BlockPos bottom_south_pos = pos.above(1).south();
        BlockPos top_south_pos = pos.above(2).south();
        BlockPos bottom_east_pos = pos.above(1).east();
        BlockPos top_east_pos = pos.above(2).east();
        BlockPos bottom_west_pos = pos.above(1).west();
        BlockPos top_west_pos = pos.above(2).west();
        if ((!level.getBlockState(bottom_north_pos).getCollisionShape(level, bottom_north_pos).isEmpty() && !level.getBlockState(bottom_north_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(top_north_pos).getCollisionShape(level, top_north_pos).isEmpty() && !level.getBlockState(top_north_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(bottom_south_pos).getCollisionShape(level, bottom_south_pos).isEmpty() && !level.getBlockState(bottom_south_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(top_south_pos).getCollisionShape(level, top_south_pos).isEmpty() && !level.getBlockState(top_south_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(bottom_east_pos).getCollisionShape(level, bottom_east_pos).isEmpty() && !level.getBlockState(bottom_east_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(top_east_pos).getCollisionShape(level, top_east_pos).isEmpty() && !level.getBlockState(top_east_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(bottom_west_pos).getCollisionShape(level, bottom_west_pos).isEmpty() && !level.getBlockState(bottom_west_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(top_west_pos).getCollisionShape(level, top_west_pos).isEmpty() && !level.getBlockState(top_west_pos).is(BlockTags.CARPETS)))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.COLLISION_NSEW);
            return false;
        }

        // Make sure the surrounding blocks (DIAGONALS) are clear
        BlockPos NE_bottom_pos = pos.above(1).north().east();
        BlockPos NE_top_pos = pos.above(2).north().east();
        BlockPos SE_bottom_pos = pos.above(1).south().east();
        BlockPos SE_top_pos = pos.above(2).south().east();
        BlockPos NW_bottom_pos = pos.above(1).north().west();
        BlockPos NW_top_pos = pos.above(2).north().west();
        BlockPos SW_bottom_pos = pos.above(1).south().west();
        BlockPos SW_top_pos = pos.above(2).south().west();
        if ((!level.getBlockState(NE_bottom_pos).getCollisionShape(level, NE_bottom_pos).isEmpty() && !level.getBlockState(NE_bottom_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(NE_top_pos).getCollisionShape(level, NE_top_pos).isEmpty() && !level.getBlockState(NE_top_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(SE_bottom_pos).getCollisionShape(level, SE_bottom_pos).isEmpty() && !level.getBlockState(SE_bottom_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(SE_top_pos).getCollisionShape(level, SE_top_pos).isEmpty() && !level.getBlockState(SE_top_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(NW_bottom_pos).getCollisionShape(level, NW_bottom_pos).isEmpty() && !level.getBlockState(NW_bottom_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(NW_top_pos).getCollisionShape(level, NW_top_pos).isEmpty() && !level.getBlockState(NW_top_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(SW_bottom_pos).getCollisionShape(level, SW_bottom_pos).isEmpty() && !level.getBlockState(SW_bottom_pos).is(BlockTags.CARPETS)) ||
            (!level.getBlockState(SW_top_pos).getCollisionShape(level, SW_top_pos).isEmpty() && !level.getBlockState(SW_top_pos).is(BlockTags.CARPETS)))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.COLLISION_DIAG);
            return false;
        }

        // Don't spawn in a cave
        if (IsPosInEnclosedSpace(level, pos.above()))
        {
            m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.IN_CAVE);
            return false;
        }

        // make sure we're not on top of any structures in this village
        int sameLevelBlocks = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL)
        {
            BlockPos neighbor = pos.relative(dir);
            VoxelShape neighbor_collision_shape = level.getBlockState(neighbor).getCollisionShape(level, neighbor);
            if (neighbor_collision_shape.max(Direction.Axis.Y) >= 0.9)
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
                int test_height = level.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX() + x, pos.getZ() + z) - 1;
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

    private void SetVillageSpawnPos(ServerLevel level, BlockPos pos, BlockPos village_pos, String village_id, BoundingBox village_bounding_box)
    {
        CommonClass.NEEDS_ERROR_MESSAGE = false;
        m_BlockDebugger.AddBlockResult(pos, BlockDebugger.BlockResults.SUCCESS);
        m_VillageSpawnPos = pos.offset(0, 1, 0); // bump the spawn pos up one because otherwise we'll spawn inside it.
        m_VillageSpawnPointGenerationState = SpawnInitData.VillageSpawnPointState.SUCCESS;
        m_VillageSpawnPointFailureReason = SpawnInitData.VillageSpawnPointFailureReason.NONE;
        m_VillagePos = village_pos;
        m_VillageID = village_id;

        // Face the player into the village instead of due south. Same convention as Entity.lookAt:
        // yaw 0 looks toward +Z and increases clockwise when seen from above.
        BlockPos village_center = CenterOf(village_bounding_box);
        double dx = village_center.getX() - m_VillageSpawnPos.getX();
        double dz = village_center.getZ() - m_VillageSpawnPos.getZ();
        float yaw = (dx == 0 && dz == 0) ? 0.0F : Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx, dz)));

        level.setDefaultSpawnPos(m_VillageSpawnPos, yaw);
        Constants.LOG.info("[Better Village Spawn Point] Set spawn to {} (standing on {}), facing the village centre at {}", Short(m_VillageSpawnPos), Short(pos), Short(village_center));

        // The overworld is where we save our spawn data
        SpawnInitData data = SpawnInitData.get(level);
        data.m_State = m_VillageSpawnPointGenerationState;
        data.m_VillageSpawnPos = m_VillageSpawnPos;
        data.m_BlockWhitelist = m_BlockWhitelist;
        data.m_VillagePos = m_VillagePos;
        data.m_VillageID = m_VillageID;
        data.m_FailureReason = m_VillageSpawnPointFailureReason;
        SpawnInitData.save(level);
    }

    private void OnFailedToGenerateSpawnPos(ServerLevel level, SpawnInitData.VillageSpawnPointFailureReason failure_reason)
    {
        CommonClass.NEEDS_ERROR_MESSAGE = true;
        Constants.LOG.error("[Better Village Spawn Point] Failed to find a spawn point for village. Failure reason: {}", failure_reason);
        m_VillageSpawnPos = SpawnInitData.NO_POS;
        m_VillagePos = SpawnInitData.NO_POS;
        m_VillageID = "";
        m_VillageSpawnPointGenerationState = SpawnInitData.VillageSpawnPointState.FAILURE;
        m_VillageSpawnPointFailureReason = failure_reason;
        SpawnInitData data = SpawnInitData.get(level);
        data.m_State = m_VillageSpawnPointGenerationState;
        data.m_VillageSpawnPos = m_VillageSpawnPos;
        data.m_BlockWhitelist = m_BlockWhitelist;
        data.m_VillagePos = m_VillagePos;
        data.m_VillageID = m_VillageID;
        data.m_FailureReason = m_VillageSpawnPointFailureReason;
        SpawnInitData.save(level);
    }

    private Boolean IsPosInEnclosedSpace(Level level, BlockPos pos)
    {
        BlockPos.MutableBlockPos test_pos_top = new BlockPos.MutableBlockPos();

        int y_positive_count = 0;
        for (int y_offset = 0; y_offset <= 5; ++y_offset)
        {
            test_pos_top.set(pos.offset(0, y_offset + 3, 0));
            if (!level.getBlockState(test_pos_top).getCollisionShape(level, test_pos_top).isEmpty())
            {
                ++y_positive_count;
            }
        }

        return y_positive_count > 2;
    }

    // True while a village search is running on the server thread. The search generates chunks
    // synchronously, and chunk generation can call back into anything that asks for the world
    // spawn (our own spawn mixins included, and other village mods do it too). If one of those
    // ever re-entered the search it would recurse on the server thread and freeze the client
    // with no crash to point at.
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
        ServerLevel level = server.getLevel(Level.OVERWORLD);
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

        // Set up our list of structure IDs
        List<StructureFeature<?>> village_structures = new ArrayList<>();

        // Populate the list of structures
        List<String> structure_ids = new ArrayList<>(CommonClass.m_Config.GetStructureList());
        for (String raw_entry : structure_ids)
        {
            String config_entry = raw_entry.trim();
            if (config_entry.isEmpty())
                continue;

            if (config_entry.contains("underwater"))
            {
                Constants.LOG.warn("[Better Village Spawn Point] '{}' is underwater. This mod doesn't support spawning in an underwater village! Skipping.", config_entry);
                continue;
            }

            // Structure tags only exist from 1.18.2. '#minecraft:village' is the default on the newer
            // branches, so be forgiving: '#namespace:name' is looked up as the structure ID
            // 'namespace:name' instead of being rejected outright.
            boolean explicit_tag = config_entry.startsWith("#");
            ResourceLocation resource_location = ResourceLocation.tryParse(explicit_tag ? config_entry.substring(1) : config_entry);
            if (resource_location == null)
            {
                Constants.LOG.warn("[Better Village Spawn Point] '{}' is not a valid structure ID! Skipping.", config_entry);
                continue;
            }
            if (explicit_tag)
            {
                Constants.LOG.warn("[Better Village Spawn Point] Structure tags do not exist in Minecraft 1.16.5; treating '{}' as the structure ID '{}'.", config_entry, resource_location);
            }

            Optional<StructureFeature<?>> structure = Registry.STRUCTURE_FEATURE.getOptional(resource_location);
            if (!structure.isPresent())
            {
                Constants.LOG.warn("[Better Village Spawn Point] '{}' is not a structure in the registry! Skipping. (1.16.5 structure IDs are the ones /locate lists, e.g. minecraft:village; the per-biome variants such as minecraft:village_plains cannot be searched for on this version.)", config_entry);
                continue;
            }

            if( IsExcluded(structure.get()) )
            {
                continue;
            }

            if( !WillVillageIdEverGenerate( level, resource_location ))
            {
                Constants.LOG.warn("[Better Village Spawn Point] Structure '{}' won't ever generate in this world (the chunk generator has no placement for it, no biome here can host it, or structure generation is off). Skipping.", config_entry);
                continue;
            }

            if (!village_structures.contains(structure.get()))
                village_structures.add(structure.get());
        }

        // If every configured entry was rejected there is nothing to look for, and running the
        // search anyway just burns a full-radius chunk scan to rediscover that. Say so plainly --
        // this is the case people hit on superflat worlds, where a modded structure is configured
        // but no structure set or biome in the world can ever place it.
        if( village_structures.isEmpty() )
        {
            Constants.LOG.warn("[Better Village Spawn Point] None of the configured villageTags entries ({}) can generate in this world. Nothing to search for.", structure_ids);
        }

        if( village_structures.isEmpty() || !FindNearestVillageAndSpawn(level, village_structures, BlockPos.ZERO, CommonClass.m_Config.GetSearchRadius(), false) )
        {
            if( !CommonClass.m_Config.UseVanillaFallback() )
            {
                OnFailedToGenerateSpawnPos(level, SpawnInitData.VillageSpawnPointFailureReason.NO_VILLAGE_FOUND);
                return;
            }

            // 1.16.5 has one vanilla village structure (minecraft:village); the biome variants are
            // its configured forms and are all found through it.
            List<StructureFeature<?>> vanilla_structures = new ArrayList<>();
            ResourceLocation vanilla_village_id = Registry.STRUCTURE_FEATURE.getKey(StructureFeature.VILLAGE);
            // The blacklist applies to the fallback too, otherwise excluding a village
            // just hands it back the moment the primary search comes up empty.
            if( vanilla_village_id != null && WillVillageIdEverGenerate(level, vanilla_village_id) && !IsExcluded(StructureFeature.VILLAGE) )
            {
                vanilla_structures.add(StructureFeature.VILLAGE);
            }

            // Same short-circuit for the fallback: with nothing placeable, the scan can only fail.
            if( vanilla_structures.isEmpty() )
            {
                Constants.LOG.warn("[Better Village Spawn Point] No vanilla village can generate in this world either, so the fallback has nothing to search for.");
                OnFailedToGenerateSpawnPos(level, SpawnInitData.VillageSpawnPointFailureReason.VANILLA_FALLBACK_FAILED);
                return;
            }

            BlockPos pos = level.findNearestMapFeature(StructureFeature.VILLAGE, BlockPos.ZERO, CommonClass.m_Config.GetSearchRadius() / 16, false);
            if( pos == null )
            {
                OnFailedToGenerateSpawnPos(level, SpawnInitData.VillageSpawnPointFailureReason.VANILLA_FALLBACK_FAILED);
                return;
            }

            Constants.LOG.info("[Better Village Spawn Point] Found {} at {}", vanilla_village_id, pos);
            if (findSpawnPosInVillage(level, pos, vanilla_village_id.toString()))
            {
                return;
            }

            OnFailedToGenerateSpawnPos(level, SpawnInitData.VillageSpawnPointFailureReason.VANILLA_FALLBACK_FAILED);
        }
    }

    /**
     * The "exclusions" config list is a blacklist applied to everything villageTags expands to
     * AND to the vanilla-village fallback. It uses the same format as villageTags. Each entry can be:
     *   - an exact structure id:  minecraft:village
     *   - a wildcard pattern:     repurposed_structures:*  or  *:village_*   ('*' matches any run of characters)
     * A '#namespace:name' entry (a structure tag on the newer branches) is matched as the ID
     * 'namespace:name' here, since 1.16.5 has no structure tags.
     */
    public Boolean IsExcluded( String village_id )
    {
        for( String exclusion_entry : CommonClass.m_Config.GetExclusionsList() )
        {
            String entry = exclusion_entry.trim();
            if( entry.isEmpty() )
                continue;

            boolean matches;
            if( entry.startsWith("#") )
                matches = village_id.equals(entry) || village_id.equals(entry.substring(1));
            else if( entry.contains("*") )
                matches = GlobToPattern(entry).matcher(village_id).matches();
            else
                matches = village_id.equals(entry);

            if( matches )
            {
                Constants.LOG.info("[Better Village Spawn Point] '{}' was excluded by '{}'", village_id, entry);
                return true;
            }
        }
        return false;
    }

    public Boolean IsExcluded( StructureFeature<?> structure )
    {
        ResourceLocation id = Registry.STRUCTURE_FEATURE.getKey(structure);
        if( id == null )
            return false;

        return IsExcluded(id.toString());
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

    Boolean WillVillageIdEverGenerate( ServerLevel level, ResourceLocation resource_location )
    {
        // 1) server / world option
        if (!level.getServer().getWorldData().worldGenSettings().generateFeatures())
            return false;

        // 2) structure feature
        Optional<StructureFeature<?>> opt = Registry.STRUCTURE_FEATURE.getOptional(resource_location);
        if (!opt.isPresent())
            return false;
        StructureFeature<?> structure = opt.get();

        // 3) does this level's chunk generator place it at all? (no separation settings = never)
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator.getSettings().getConfig(structure) == null)
            return false;

        // 4) any biome in this level can host it?
        for (Biome biome : generator.getBiomeSource().possibleBiomes())
        {
            if (biome.getGenerationSettings().isValidStart(structure))
                return true;
        }
        return false;
    }
}
