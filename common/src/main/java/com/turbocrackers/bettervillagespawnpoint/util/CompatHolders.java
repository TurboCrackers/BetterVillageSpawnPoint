package com.turbocrackers.bettervillagespawnpoint.util;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Backport of HolderSet.direct(...) for MC 1.19.2 and below.
 */
public final class CompatHolders {
    private CompatHolders() {} // prevent instantiation

    public static HolderSet<ConfiguredStructureFeature<?, ?>> direct(
            List<Holder<ConfiguredStructureFeature<?, ?>>> holders) {
        List<Holder<ConfiguredStructureFeature<?, ?>>> list = holders.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return HolderSet.direct(list);
        // or: return new HolderSet.Direct<>(list);
    }
}