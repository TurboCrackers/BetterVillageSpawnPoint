package com.turbocrackers.bettervillagespawnpoint;

import com.turbocrackers.bettervillagespawnpoint.util.BlockDebugger;
import com.turbocrackers.bettervillagespawnpoint.util.VillageLocator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.Objects;

// On the newer branches this class lives in the common module, shared between the loaders. 1.12.2 is
// Forge only, so it sits in the Forge module under the same package; the split is kept so the code
// stays recognisable next to the other branches.
public class CommonClass
{
    public static CommonConfig m_Config;
    public static BlockDebugger m_BlockDebugger = new BlockDebugger();
    public static VillageLocator m_VillageLocator = new VillageLocator();
    public static boolean NEEDS_ERROR_MESSAGE = false;

    public static void init()
    {
    }

    public static void SendErrorMessageIfNeeded( EntityPlayerMP player )
    {
        Objects.requireNonNull(player.getServer()).addScheduledTask(() -> {
            if (NEEDS_ERROR_MESSAGE)
            {
                switch( m_VillageLocator.GetFailureReason() )
                {
                    case VANILLA_FALLBACK_FAILED:
                    {
                        player.sendMessage( Red("[Better Village Spawn Point] No valid village was found, and the vanilla fallback failed. Vanilla villages might not be able to spawn in your modpack.") );
                        break;
                    }

                    default:
                    {
                        player.sendMessage( Red("[Better Village Spawn Point] Village search failed. Failure reason: " + m_VillageLocator.GetFailureReason()) );
                        break;
                    }
                }
                NEEDS_ERROR_MESSAGE = false;
            }
        });
    }

    private static TextComponentString Red( String text )
    {
        TextComponentString component = new TextComponentString(text);
        component.setStyle(new Style().setColor(TextFormatting.RED));
        return component;
    }
}
