package com.turbocrackers.bettervillagespawnpoint.util;

import com.mojang.datafixers.util.Pair;
import com.turbocrackers.bettervillagespawnpoint.Constants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Locale;

public class BlockDebugger
{
    public static final Boolean DEBUG_ENABLED = false;

    // Every marker carries this tag so hide can find exactly them, whatever else is in the world.
    private static final String MARKER_TAG = "bvsp_debug_marker";
    // A village search records thousands of rejected blocks. Each marker is an entity, so cap it.
    private static final int MAX_MARKERS = 1500;
    private static final int COLOUR_REJECTED = (int)0x90B00000L; // translucent red, ARGB
    private static final int COLOUR_CHOSEN   = (int)0x9000A000L; // translucent green, ARGB

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

    public void Clear()
    {
        m_BlockResults.clear();
    }

    /**
     * 1.21.9 removed DebugPackets, and with it the game-test marker packets this used to draw. The
     * replacement debug-subscription system needs a client-side renderer, so each result is shown as
     * a text_display entity instead: the reason floats inside the block, red for rejections and green
     * for the block that was chosen. They are ordinary entities, so hide just kills them by tag.
     */
    public void ToggleBlockFailureDebug( ServerLevel level, Boolean show )
    {
        CommandSourceStack source = level.getServer().createCommandSourceStack().withSuppressedOutput();

        if( !show )
        {
            RunCommand(level, source, "kill @e[type=minecraft:text_display,tag=" + MARKER_TAG + "]");
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

            BlockPos pos = block_info.getFirst();
            BlockResults reason = block_info.getSecond();
            int colour = reason == BlockResults.SUCCESS ? COLOUR_CHOSEN : COLOUR_REJECTED;
            RunCommand(level, source, String.format(Locale.ROOT,
                    "summon minecraft:text_display %.1f %.1f %.1f {text:\"%s\",billboard:\"center\",background:%d,see_through:1b,Tags:[\"%s\"]}",
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, reason.name(), colour, MARKER_TAG));
        }
        Constants.LOG.info("[Better Village Spawn Point] Showing {} block result markers in {}.", Math.min(shown, MAX_MARKERS), level.dimension().identifier());
    }

    private static void RunCommand( ServerLevel level, CommandSourceStack source, String command )
    {
        level.getServer().getCommands().performPrefixedCommand(source, command);
    }
}
