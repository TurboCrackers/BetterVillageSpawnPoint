package com.turbocrackers.bettervillagespawnpoint.command;

import com.turbocrackers.bettervillagespawnpoint.CommonClass;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/**
 * The showBlockDebug / hideBlockDebug / spawn commands the newer branches register through
 * Brigadier. 1.12.2 predates Brigadier, so they are plain server commands, registered only when
 * BlockDebugger.DEBUG_ENABLED is true.
 */
public final class DebugCommands
{
    private DebugCommands() {}

    public static void Register(FMLServerStartingEvent event)
    {
        event.registerServerCommand(new ToggleCommand("showBlockDebug", true));
        event.registerServerCommand(new ToggleCommand("hideBlockDebug", false));
        event.registerServerCommand(new SpawnCommand());
    }

    private static WorldServer OverworldOf(MinecraftServer server, ICommandSender sender)
    {
        return sender.getEntityWorld() instanceof WorldServer ? (WorldServer) sender.getEntityWorld() : server.getWorld(0);
    }

    private static final class ToggleCommand extends CommandBase
    {
        private final String m_Name;
        private final boolean m_Show;

        ToggleCommand(String name, boolean show)
        {
            m_Name = name;
            m_Show = show;
        }

        @Override
        public String getName() { return m_Name; }

        @Override
        public String getUsage(ICommandSender sender) { return "/" + m_Name; }

        @Override
        public int getRequiredPermissionLevel() { return 2; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            CommonClass.m_BlockDebugger.ToggleBlockFailureDebug(OverworldOf(server, sender), m_Show);
        }
    }

    private static final class SpawnCommand extends CommandBase
    {
        @Override
        public String getName() { return "spawn"; }

        @Override
        public String getUsage(ICommandSender sender) { return "/spawn"; }

        @Override
        public int getRequiredPermissionLevel() { return 2; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
        {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            BlockPos shared_spawn_pos = server.getWorld(0).getSpawnPoint();
            player.connection.setPlayerLocation(shared_spawn_pos.getX() + 0.5, shared_spawn_pos.getY() + 0.1, shared_spawn_pos.getZ() + 0.5, player.rotationYaw, player.rotationPitch);
        }
    }
}
