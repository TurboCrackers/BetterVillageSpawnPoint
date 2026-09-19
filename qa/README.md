# QA matrix

`run_matrix.py` deploys the release jar into each Prism instance and drives a join / die / respawn
scenario against it, one instance after another, writing a report per instance under `qa/reports/`.

What one target does:

1. Checks out the instance's Minecraft version branch in `qa/.worktrees/<version>` and builds it.
2. Deploys the jar into the instance with the repo's `deploy<Loader>ToInstance` task.
3. Starts that branch's dev server (offline mode, RCON on, immediate respawn, spawn radius 0)
   on a brand-new world and waits for the mod to log its village spawn.
4. Launches the Prism instance with `--server localhost` so your account joins that world.
5. Over RCON: first join, die, `spawnpoint` set then die, `clearspawnpoint` then die, close the
   game and relaunch it, die again, restart the server, rejoin, die again. Every step checks the
   player's position against the village spawn (or the respawn point).
6. Scans the logs for mixin failures, exceptions and crash reports, closes everything, and
   writes `qa/reports/<timestamp>/<instance>.md` plus a `SUMMARY.md`.

Bed handling: "with a bed" and "without a bed" are modelled with the `spawnpoint` and
`clearspawnpoint` commands, which exercise the respawn-point-set and respawn-point-absent code
paths without needing to click on a bed.

    python3 qa/run_matrix.py --list                      # what would run
    python3 qa/run_matrix.py --instances 1.21.6-NeoForge # one instance
    python3 qa/run_matrix.py --loader forge              # every Forge instance
    python3 qa/run_matrix.py --fetch-fabric-deps         # Fabric instances need Fabric API + Cloth Config

Requirements: Prism Launcher installed at its default path, a logged-in Prism account, each
instance able to launch on its own (Java set up), and the RCON/server ports 25575 and 25565 free.
