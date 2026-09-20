#!/usr/bin/env python3
"""Join / die check against a REAL dedicated server (installed by the loader's own installer)
instead of the Gradle dev server that run_matrix.py uses.

Use this when a branch's dev server cannot start for reasons unrelated to the mod (for example
the ModDevGradle legacy-Forge server on 1.20.1), or when you want to test the exact release jar
on the server side as well as the client side.

    python3 qa/real_server_check.py --server-dir /path/to/installed/server \
        --jar /path/to/bettervillagespawnpoint-forge-1.20.1-3.0.jar --instance 1.20.1-Forge \
        [--java /path/to/java] [--extra-mod other.jar ...]

The server directory must already contain an installed server (Forge: run the installer with
--installServer; Fabric: fabric-server-launch.jar; vanilla-style: server.jar). The script wipes the
world, writes the same server.properties the matrix uses, copies the jar into mods/, starts the
server, launches the Prism instance against it, and checks first join and one death.
"""
import argparse, os, re, shutil, subprocess, sys, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_matrix as rm  # noqa: E402  (RCON helper, wait_for, parse_pos, client_pids, constants)


def launch_command(server_dir: Path, java: str):
    forge_args = sorted(server_dir.glob("libraries/net/minecraftforge/forge/*/unix_args.txt"))
    if forge_args:
        return [java, "@user_jvm_args.txt", f"@{forge_args[-1].relative_to(server_dir)}", "nogui"]
    neo_args = sorted(server_dir.glob("libraries/net/neoforged/neoforge/*/unix_args.txt"))
    if neo_args:
        return [java, "@user_jvm_args.txt", f"@{neo_args[-1].relative_to(server_dir)}", "nogui"]
    for name in ("fabric-server-launch.jar", "server.jar"):
        if (server_dir / name).exists():
            return [java, "-Xmx2G", "-jar", name, "nogui"]
    sys.exit(f"no installed server found in {server_dir}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--server-dir", required=True)
    ap.add_argument("--jar", help="the mod jar to test (copied into the server's mods/); omit for a control run without the mod")
    ap.add_argument("--instance", required=True, help="Prism instance id to launch as the client")
    ap.add_argument("--java", default="java")
    ap.add_argument("--extra-mod", action="append", default=[], help="another jar to copy into mods/ (dependencies)")
    ap.add_argument("--server-timeout", type=int, default=600)
    ap.add_argument("--join-timeout", type=int, default=420)
    a = ap.parse_args()

    server_dir = Path(a.server_dir).resolve(); jar = Path(a.jar).resolve() if a.jar else None
    control = jar is None  # no mod: only check that the client can join and stay connected
    reports = rm.REPORTS / time.strftime("%Y-%m-%d_%H%M-real"); reports.mkdir(parents=True, exist_ok=True)
    steps, notes = [], []
    def step(name, ok, detail=""):
        steps.append((name, "PASS" if ok else "FAIL", detail)); print(f"[{a.instance}] {'PASS' if ok else 'FAIL'} {name}: {detail}", flush=True)

    # --- server files
    mods = server_dir / "mods"; mods.mkdir(exist_ok=True)
    for old in mods.glob("*"):
        if "bettervillagespawnpoint" in old.name.lower(): old.unlink()
    if jar: shutil.copy(jar, mods / jar.name)
    for extra in a.extra_mod: shutil.copy(extra, mods / Path(extra).name)
    (server_dir / "eula.txt").write_text("eula=true\n")
    (server_dir / "server.properties").write_text("\n".join([
        f"server-port={rm.SERVER_PORT}", "online-mode=false", "enforce-secure-profile=false",
        "enable-rcon=true", f"rcon.port={rm.RCON_PORT}", f"rcon.password={rm.RCON_PASS}",
        "level-name=world", "spawn-protection=0", "max-tick-time=-1", "difficulty=peaceful",
        "view-distance=8", "sync-chunk-writes=false", "motd=BVSP QA (real server)", ""]))
    shutil.rmtree(server_dir / "world", ignore_errors=True)
    shutil.rmtree(server_dir / "crash-reports", ignore_errors=True)
    if not (server_dir / "user_jvm_args.txt").exists(): (server_dir / "user_jvm_args.txt").write_text("-Xmx2G\n")

    # --- start server
    log = reports / f"{a.instance}-real-server.log"
    cmd = launch_command(server_dir, a.java)
    print(f"[{a.instance}] starting server: {' '.join(cmd)}  (log {log.name})", flush=True)
    server = subprocess.Popen(cmd, cwd=server_dir, stdout=open(log, "w"), stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    player = None
    try:
        def ready():
            t = log.read_text(errors="replace")
            if server.poll() is not None: raise RuntimeError("server process exited: " + t[-3000:])
            if control: return ("Done (" in t) or None
            m = re.search(r"Set spawn to (-?\d+), (-?\d+), (-?\d+)", t)
            if m and "Done (" in t: return m
            if re.search(r"Village search failed|VANILLA_FALLBACK_FAILED|NO_VILLAGE_FOUND|None of the configured", t): raise RuntimeError("mod reported a failed village search")
            return None
        m = rm.wait_for(ready, a.server_timeout, what="server ready + village spawn")
        vs = None
        if not control:
            x, y, z = (int(v) for v in m.groups()); vs = (x + 0.5, float(y), z + 0.5)
            print(f"[{a.instance}] village spawn {vs}", flush=True)
        def rcon_ok():
            try: rm.rcon("list", retries=1); return True
            except Exception: return False
        rm.wait_for(rcon_ok, 90, what="RCON")
        for g in ("gamerule doImmediateRespawn true", "gamerule spawnRadius 0", "gamerule keepInventory true", "difficulty peaceful"): rm.rcon(g)

        # --- client
        subprocess.Popen([str(rm.PRISM_EXE), "--launch", a.instance, "--server", f"localhost:{rm.SERVER_PORT}"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        def joined():
            mm = re.search(r"online:\s*(\S+)", rm.rcon("list")); return mm.group(1).strip(",") if mm else None
        player = rm.wait_for(joined, a.join_timeout, what="client to join"); time.sleep(4)
        if control:
            time.sleep(10); still = player in rm.rcon("list")
            step("client joins the empty server and stays connected", still, "still online after 10 s" if still else "dropped within 10 s")
            raise StopIteration
        t = log.read_text(errors="replace")
        ms = re.findall(r"logged in with entity id \d+ at \((-?[\d.]+), (-?[\d.]+), (-?[\d.]+)\)", t)
        jp = tuple(float(v) for v in ms[-1]) if ms else None
        near = lambda p, q, xz, dy: p is not None and rm.dist_xz(p, q) <= xz and abs(p[1] - q[1]) <= dy
        fmt = lambda p: "none" if p is None else f"({p[0]:.1f}, {p[1]:.1f}, {p[2]:.1f})"
        step("first join lands on village spawn", near(jp, vs, 1.0, 2.0), f"joined at {fmt(jp)}, village spawn {fmt(vs)}")
        rm.rcon(f"kill {player}"); time.sleep(7)
        p = None
        for _ in range(5):
            p = rm.parse_pos(rm.rcon(f"data get entity {player} Pos"))
            if p: break
            time.sleep(2)
        step("die with no respawn point -> village spawn", near(p, vs, 2.5, 3.0), f"respawned at {fmt(p)}, {rm.dist_xz(p, vs):.1f} blocks from spawn" if p else "no position")
    except StopIteration:
        pass
    except Exception as e:
        step("scenario aborted", False, str(e).splitlines()[0][:300] if str(e) else repr(e))
    finally:
        for pid in rm.client_pids(a.instance):
            try: os.kill(pid, 15)
            except ProcessLookupError: pass
        try: rm.rcon("stop")
        except Exception: pass
        try: server.wait(90)
        except subprocess.TimeoutExpired: server.kill()
        bad = []
        t = log.read_text(errors="replace") if log.exists() else ""
        for pat in (r"Mixin apply .*failed", r"Critical injection failure", r"InvalidInjectionException", r"Exception in thread", r"\[Better Village Spawn Point\].*(?:error|failed)"):
            for mm in re.finditer(pat, t, re.I): bad.append(t[mm.start():mm.start() + 160].splitlines()[0])
        for cr in (server_dir / "crash-reports").glob("*.txt"): bad.append(f"crash report: {cr.name}")
        step("no mixin errors, exceptions, or crash reports", not bad, "; ".join(sorted(set(bad))[:5]) if bad else "clean")
        passed = sum(1 for s in steps if s[1] == "PASS")
        lines = [f"# {a.instance} against a real server  ({'PASS' if passed == len(steps) else 'FAIL'} {passed}/{len(steps)})\n",
                 f"server dir {server_dir}, jar {jar.name if jar else 'none (control run)'}, player {player or '?'}\n", "| step | result | detail |", "|---|---|---|"]
        lines += [f"| {n} | {r} | {d} |" for n, r, d in steps]
        if notes: lines += ["", "Notes:"] + [f"- {n}" for n in notes]
        (reports / f"{a.instance}.md").write_text("\n".join(lines) + "\n")
        print(f"[{a.instance}] report written: {passed}/{len(steps)} -> {reports / (a.instance + '.md')}", flush=True)
        return 0 if passed == len(steps) else 2


if __name__ == "__main__": sys.exit(main())
