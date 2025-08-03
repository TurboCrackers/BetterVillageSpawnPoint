package com.turbocrackers.bettervillagespawnpoint;

import java.util.List;

public class CommonConfig
{
    public int GetSearchRadius()
    {
        throw new UnsupportedOperationException("CommonConfig.GetSearchRange needs to be implemented in child class.");
    }

    public Boolean UseVanillaFallback()
    {
        throw new UnsupportedOperationException("CommonConfig.UseVanillaFallback needs to be implemented in child class.");
    }

    public List<? extends String> GetStructureList()
    {
        throw new UnsupportedOperationException("CommonConfig.GetStructureList needs to be implemented in child class.");
    }
}
