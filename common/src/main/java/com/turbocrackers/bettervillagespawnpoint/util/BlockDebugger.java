package com.turbocrackers.bettervillagespawnpoint.util;

import com.mojang.datafixers.util.Pair;
import com.turbocrackers.bettervillagespawnpoint.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;

public class BlockDebugger
{
    public static final Boolean DEBUG_ENABLED = false;

    public enum BlockResults
    {
        SUCCESS,
        GROUND_NOT_WHITELISTED,
        GROUND_BLACKLISTED,
        BODY_BLACKLISTED,
        HEAD_NOT_EMPTY,
        NOT_FULL_BLOCK,
        INTERACTABLE,
        BUSH_IN_BODY,
        MOVE_BLOCKED,
        ABOVE_NOT_CLEAR,
        IN_WATER,
        TREE,
        COLLISION_NSEW,
        COLLISION_DIAG,
        IN_CAVE,
        ON_EDGE,
        ON_CLIFF,
        TALL_PLANT_IN_BODY
    }

    private final ArrayList<Pair<BlockPos, BlockResults>> m_BlockResults = new ArrayList<>();

    public void AddBlockResult(BlockPos pos, BlockResults reason)
    {
        m_BlockResults.add(new Pair<>(pos, reason));
    }

    // 1.21.9 removed DebugPackets (and with it the game-test marker packets this used to send),
    // so the per-block results are written to the log instead of drawn in-world.
    public void ToggleBlockFailureDebug( ServerLevel level, Boolean show )
    {
        if( !show )
            return;

        Constants.LOG.info("[Better Village Spawn Point] {} block results in {}:", m_BlockResults.size(), level.dimension().identifier());
        for( Pair<BlockPos, BlockResults> block_info : m_BlockResults)
        {
            Constants.LOG.info("[Better Village Spawn Point]   {} -> {}", block_info.getFirst().toShortString(), block_info.getSecond());
        }
    }
}
