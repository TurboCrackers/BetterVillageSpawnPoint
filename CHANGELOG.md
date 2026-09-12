# Changelog

The same changelog is kept on every branch. Entries note which Minecraft releases they apply to
when it is not all of them.

## 1.0.4

### Fixed
- The village search could end up with a chunk radius of 0 for small villages, or villages whose
  start lay at negative chunk coordinates, and then find nothing. The radius now has a floor of 2
  and uses the absolute chunk offsets.
- 1.21.9 and newer: new players are placed on the recorded village spawn again. Minecraft moved
  the spawn search to `PlayerSpawnFinder.findSpawn` during the connection's configuration phase,
  leaving the method the mod used to hook in place but unused, so vanilla's search around the world
  spawn ran instead and could drop players onto trees.
- 26.1 and newer: worlds upgraded from 1.21.x keep their recorded village. The saved-data record
  moved to a namespaced file, and the old one is migrated on first load.
- The "Set spawn to" log line now prints the block the player is placed on, not the ground block
  beneath it.
- `showBlockDebug` never showed anything: the village search recorded its block results into a
  private debugger instance while the command read the shared one. They now share an instance, and
  results are cleared at the start of each search.

### Changed
- `exclusions` uses the same format as `villageTags`: `#namespace:tag`, `namespace:id`, or a
  `*` wildcard pattern. The `#` on a tag is optional in both lists; a bare entry that is not a
  structure ID is tried as a tag.
- Players now face the centre of the village when they spawn instead of due south.
- The declared Minecraft version range is pinned to the exact release each branch is built for.
- 1.21.9 and newer: the `showBlockDebug` command draws its block results as in-world text markers
  again (as `text_display` entities; `hideBlockDebug` removes them). Minecraft removed the
  game-test marker packets the command used before.

### Build
- Ports for 1.21.7 through 26.2, one branch each. See `BRANCHES.md` for the toolchain boundaries.
- `checkMixinTargets` verifies at build time that every common mixin hook targets a method that
  still exists and that vanilla still calls somewhere.
- A GitHub Actions workflow builds every loader on push.
- Forge dev client on ForgeGradle 7 branches: source set outputs are merged (the two-module
  resolution failure) and `-XstartOnFirstThread` is passed on macOS.
