package com.turbocrackers.bettervillagespawnpoint.util;

import com.turbocrackers.bettervillagespawnpoint.Constants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Locale;

public class BlockDebugger
{
    public static final Boolean DEBUG_ENABLED = false;

    // Every marker carries this tag so hide can find exactly them, whatever else is in the world.
    private static final String MARKER_TAG = "bvsp_debug_marker";
    // A village search records thousands of rejected blocks. Each marker is an entity, so cap it.
    private static final int MAX_MARKERS = 1500;

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
        m_BlockResults.add(Pair.of(pos, reason));
    }

    public void Clear()
    {
        m_BlockResults.clear();
    }

    /**
     * 1.12.2 has neither the game-test marker packets nor text_display entities, so each result is
     * shown as an invisible marker armor stand whose name floats inside the block: red for
     * rejections and green for the block that was chosen. hide kills them by tag.
     */
    public void ToggleBlockFailureDebug( WorldServer level, Boolean show )
    {
        MinecraftServer server = level.getMinecraftServer();
        if (server == null)
            return;

        if( !show )
        {
            RunCommand(server, "kill @e[type=minecraft:armor_stand,tag=" + MARKER_TAG + "]");
            return;
        }

        int shown = 0;
        for( Pair<BlockPos, BlockResults> block_info : m_BlockResults )
        {
            if( shown++ >= MAX_MARKERS )
            {
                Constants.LOG.warn("[Better Village Spawn Point] {} block results recorded; only the first {} are shown.", m_BlockResults.size(), MAX_MARKERS);
                break;
            }

            BlockPos pos = block_info.getLeft();
            BlockResults reason = block_info.getRight();
            String colour = reason == BlockResults.SUCCESS ? "§a" : "§c";
            RunCommand(server, String.format(Locale.ROOT,
                    "summon minecraft:armor_stand %.1f %.1f %.1f {Invisible:1b,Marker:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:\"%s%s\",Tags:[\"%s\"]}",
                    pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, colour, reason.name(), MARKER_TAG));
        }
        Constants.LOG.info("[Better Village Spawn Point] Showing {} block result markers in {}.", Math.min(shown, MAX_MARKERS), level.provider.getDimensionType().getName());
    }

    private static void RunCommand( MinecraftServer server, String command )
    {
        server.getCommandManager().executeCommand(server, command);
    }
}
