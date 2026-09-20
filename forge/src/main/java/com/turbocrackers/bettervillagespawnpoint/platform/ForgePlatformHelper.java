package com.turbocrackers.bettervillagespawnpoint.platform;

import com.turbocrackers.bettervillagespawnpoint.platform.services.IPlatformHelper;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Loader;

public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {

        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return Loader.isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        // FML 1.12.2 records this on the LaunchWrapper blackboard when running from a dev workspace.
        return Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"));
    }
}
