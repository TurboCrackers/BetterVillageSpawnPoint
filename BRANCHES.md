# Branch layout

There is no main branch. Each supported Minecraft release has its own branch, named after the
release, and each branch ships one jar per loader built and tested against exactly that release.
Newer branches were created from the previous one, so a change made on the newest branch is
propagated backwards by cherry-picking (`git cherry-pick -x`) and compiling on each target.

| Branches | Notes |
|---|---|
| 1.12.2 | Forge only (Prism offers no Fabric for it). Java 8, ForgeGradle 6 with MCP `stable_39` names: there are no Mojang mappings before 1.14, so there is no `common` module and the code lives in `forge/` under the common package names. No Mixin either: the two spawn hooks are Forge events in `SpawnPlacement` (`PlayerEvent.LoadFromFile` for a brand-new player, `PlayerRespawnEvent` for a bed-less respawn). Structures are chunk-generator names (`Village`), found with `getNearestStructurePos` and read back from `MapGenStructureData`; block tags are stood in for by block-class families; the two heightmaps are computed by hand; the spawn facing lives in the saved record because the 1.12 world spawn has no angle. Config is Forge's annotation config (`BetterVillageSpawnPoint-common.cfg`). `@Mod` takes its version from `Constants.MOD_VERSION`, which `forge:checkModVersionConstant` keeps equal to `gradle.properties`. |
| 1.16.5 | Fabric and Forge, Java 8, no Parchment. Structures are `StructureFeature`s (`minecraft:village`); there are no structure tags, and the per-biome village variants cannot be searched for. Forge 1.16.5 uses MCP class names with Mojang member names, so `forge/build.gradle` copies the common sources into `build/generated/mcpCommon` with the class names translated (a table in that file) and compiles the copy. Cloth Config 4.17.101, Fabric API 0.42.0+1.16, Forge 36.2.42. |
| 1.18.2, 1.19, 1.19.1, 1.19.2 | `ConfiguredStructureFeature` and `Registry.STRUCTURE_REGISTRY`; VillageLocator diverges here, so cherry-picks usually need hand porting. No NeoForge module. |
| 1.19.3, 1.19.4, 1.20.1 | `Structure` API. No NeoForge module. ModDevGradle's legacyforge plugin builds common on 1.20.1. |
| 1.20.2, 1.20.3, 1.20.4, 1.20.6, 1.21.1, 1.21.3 to 1.21.8 | Fabric, Forge and NeoForge. Common source is identical across these, so cherry-picks apply cleanly. |
| 1.21.9, 1.21.10 | World spawn is `LevelData.RespawnData`. New players are placed by `PlayerSpawnFinder.findSpawn`, which the spawn mixin hooks. Gradle 8.14, Loom 1.11. |
| 1.21.11 | `ResourceLocation` became `Identifier`. Commands use `Commands.hasPermission`. Gradle 9, ForgeGradle 7 (no MixinGradle, no Mixin annotation processor on Forge). |
| 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | Minecraft is unobfuscated and targets Java 25. Fabric uses the plain `net.fabricmc.fabric-loom` plugin with `implementation` dependencies and `jar` output. No Parchment. Saved data is keyed by `Identifier` and lives under `data/bettervillagespawnpoint/`; a pre-26.1 record is migrated on first load. |

## Things that are the same everywhere

- `gradle/deploy.gradle` is shared verbatim. It reads `fabric_jar_task` from `gradle.properties`
  (`remapJar` while Minecraft was obfuscated, `jar` from 26.1) so the deploy tasks follow the loader.
- `minecraft_version_range` is pinned to exactly the branch's release. The API moves between patch
  releases, and a wider range only turns a clean loader refusal into a crash at startup.
- `checkMixinTargets` (see `common/build.gradle`) runs as part of `check` and fails the build if a
  common mixin targets a method Minecraft no longer has, or one that nothing in vanilla calls. It
  cannot tell whether vanilla calls it on the path you expect, so a port still needs a real join test.
  (1.12.2 has no mixins and no common module, so it has no such check.)
- `.github/workflows/build.yml` runs `gradle build` on every push, on whichever branch was pushed.

## Testing spawn placement

The spawn search only runs for a player the server has never seen. In singleplayer the host's saved
position is loaded whatever the username, so rejoining an existing world never exercises it. Create a
new world, or join a dedicated server with a fresh account.
