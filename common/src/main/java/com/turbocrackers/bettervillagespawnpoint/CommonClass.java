package com.turbocrackers.bettervillagespawnpoint;

import com.turbocrackers.bettervillagespawnpoint.util.BlockDebugger;
import com.turbocrackers.bettervillagespawnpoint.util.VillageLocator;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

// This class is part of the common project meaning it is shared between all supported loaders. Code written here can only
// import and access the vanilla codebase, libraries used by vanilla, and optionally third party libraries that provide
// common compatible binaries. This means common code can not directly use loader specific concepts such as Forge events
// however it will be compatible with all supported mod loaders.
public class CommonClass
{
    public static CommonConfig m_Config;
    public static BlockDebugger m_BlockDebugger = new BlockDebugger();
    public static VillageLocator m_VillageLocator = new VillageLocator();
    public static boolean NEEDS_ERROR_MESSAGE = false;

    // The loader specific projects are able to import and use any code from the common project. This allows you to
    // write the majority of your code here and load it from your loader specific projects. This example has some
    // code that gets invoked by the entry point of the loader specific projects.
    public static void init()
    {
    }

    public static void SendErrorMessageIfNeeded( ServerPlayer player )
    {
        Objects.requireNonNull(player.getServer()).execute(() -> {
            if (NEEDS_ERROR_MESSAGE)
            {
                switch( VillageLocator.m_VillageSpawnPointFailureReason )
                {
                    case VANILLA_FALLBACK_FAILED:
                    {
                        player.sendMessage( (new TextComponent("[Better Village Spawn Point] No valid village was found, and the vanilla fallback failed. Vanilla villages might not be able to spawn in your modpack.")).withStyle(ChatFormatting.RED), ChatType.SYSTEM, Util.NIL_UUID);
                        break;
                    }

                    default:
                    {
                        player.sendMessage( (new TextComponent("\"[Better Village Spawn Point] Village search failed. Failure reason: \" + VillageLocator.m_VillageSpawnPointFailureReason")).withStyle(ChatFormatting.RED), ChatType.SYSTEM, Util.NIL_UUID);
                        break;
                    }
                }
                NEEDS_ERROR_MESSAGE = false;
            }
        });
    }
}