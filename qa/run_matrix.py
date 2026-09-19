#!/usr/bin/env python3
"""
QA matrix for Better Village Spawn Point.

For each Prism instance (one per Minecraft version and loader):
  1. check out that Minecraft version's branch in a scratch worktree and build it,
  2. deploy the release jar into the instance with the repo's deploy task,
  3. start the branch's dev server (offline mode, RCON on) with a brand-new world,
  4. launch the Prism instance straight into that server,
  5. drive a scenario over RCON: first join, die, respawn-point set, die, respawn-point
     cleared, die, leave and rejoin, die again, then a server restart and rejoin,
  6. assert the player's position after every step against the village spawn the mod chose,
  7. close the game and the server, write a report, move to the next instance.

Run `python3 qa/run_matrix.py --list` to see the targets, `--instances 1.21.6-NeoForge,26.2-Forge`
to pick some, `--fetch-fabric-deps` to pull Fabric API and Cloth Config into Fabric instances
from Modrinth (they are required there and the instances do not have them).
"""
import argparse, json, os, re, shutil, socket, struct, subprocess, sys, time, urllib.request
from datetime import datetime
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
QA = REPO / "qa"
WORKTREES = QA / ".worktrees"
REPORTS = QA / "reports"
PRISM_EXE = Path("/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher")
RCON_PORT, RCON_PASS, SERVER_PORT = 25575, "bvsp-qa", 25565
MODRINTH = {"fabric-api": "P7dR8mSH", "cloth-config": "9s6osm5g"}
LOADERS = {  # loader uid fragment -> (deploy task, gradle run task, candidate run dirs, jar name fragment)
    "net.fabricmc.fabric-loader": ("deployFabricToInstance", ":fabric:runServer", ["fabric/runs/server"], "fabric"),
    "net.minecraftforge":         ("deployForgeToInstance",  ":forge:runServer",  ["forge/runs/server", "forge/run"], "forge"),
    "net.neoforged":              ("deployNeoToInstance",    ":neoforge:runServer", ["neoforge/runs/server", "neoforge/run"], "neoforge"),
}

# ----------------------------------------------------------------------------- helpers
def sh(cmd, cwd=None, check=True, capture=True, timeout=None):
    r = subprocess.run(cmd, cwd=cwd, text=True, capture_output=capture, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"{' '.join(map(str, cmd))} failed:\n{(r.stderr or '')[-2000:]}\n{(r.stdout or '')[-2000:]}")
    return r

def gradle_prop(key):
    for p in (Path.home() / ".gradle" / "gradle.properties",):
        if p.exists():
            m = re.search(rf"^{re.escape(key)}=(.*)$", p.read_text(), re.M)
            if m: return m.group(1).strip()
    return None

def prism_instances_dir():
    cfg = Path.home() / "Library/Application Support/PrismLauncher/prismlauncher.cfg"
    if cfg.exists():
        m = re.search(r"^InstanceDir=(.*)$", cfg.read_text(), re.M)
        if m: return Path(m.group(1).strip())
    root = gradle_prop("prismRoot") or os.environ.get("PRISM_ROOT") or str(Path.home() / "Library/Application Support/PrismLauncher")
    return Path(root) / "instances"

def discover_instances(inst_dir):
    out = []
    for d in sorted(inst_dir.iterdir()):
        pack = d / "mmc-pack.json"
        if not pack.exists(): continue
        comps = json.loads(pack.read_text()).get("components", [])
        mc = next((c.get("version") for c in comps if c.get("uid") == "net.minecraft"), None)
        loader = next((c["uid"] for c in comps if c.get("uid") in LOADERS), None)
        if mc and loader: out.append({"id": d.name, "dir": d, "mc": mc, "loader": loader})
    return out

def vkey(v): return [int(x) for x in re.findall(r"\d+", v)]

class Rcon:
    def __init__(self, host="127.0.0.1", port=RCON_PORT, password=RCON_PASS):
        self.s = socket.create_connection((host, port), timeout=15); self.rid = 0
        if self._send(3, password) is None: raise RuntimeError("RCON auth failed")
    def _send(self, kind, body):
        self.rid += 1
        payload = struct.pack("<ii", self.rid, kind) + body.encode() + b"\x00\x00"
        self.s.sendall(struct.pack("<i", len(payload)) + payload)
        raw = self._recv_exact(4); (length,) = struct.unpack("<i", raw); data = self._recv_exact(length)
        rid, rtype = struct.unpack("<ii", data[:8])
        if rid == -1: return None
        return data[8:-2].decode(errors="replace")
    def _recv_exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.s.recv(n - len(buf))
            if not chunk: raise RuntimeError("RCON connection closed")
            buf += chunk
        return buf
    def cmd(self, c): return self._send(2, c) or ""
    def close(self): self.s.close()

def rcon(command, retries=3):
    for i in range(retries):
        try:
            r = Rcon(); out = r.cmd(command); r.close(); return out
        except Exception as e:
            if i == retries - 1: raise
            time.sleep(3)

def wait_for(pred, timeout, every=3, what="condition"):
    t0 = time.time()
    while time.time() - t0 < timeout:
        v = pred()
        if v: return v
        time.sleep(every)
    raise TimeoutError(f"timed out after {timeout}s waiting for {what}")

def parse_pos(text):
    m = re.search(r"\[\s*(-?[\d.]+)d?,\s*(-?[\d.]+)d?,\s*(-?[\d.]+)d?\s*\]", text or "")
    return tuple(float(x) for x in m.groups()) if m else None

def dist_xz(a, b): return ((a[0]-b[0])**2 + (a[2]-b[2])**2) ** 0.5

def client_pids(inst_id):
    r = subprocess.run(["pgrep", "-fi", f"instances/{inst_id}/"], capture_output=True, text=True)
    return [int(p) for p in r.stdout.split() if p.strip()]

# ----------------------------------------------------------------------------- one target
class Target:
    def __init__(self, inst, args):
        self.inst, self.args = inst, args
        self.mc, self.loader = inst["mc"], inst["loader"]
        self.deploy_task, self.run_task, self.run_dirs, self.jar_frag = LOADERS[self.loader]
        self.wt = WORKTREES / self.mc
        self.steps = []; self.notes = []; self.server = None; self.server_log = None
        self.village_spawn = None; self.player = None

    def log(self, msg): print(f"[{self.inst['id']}] {msg}", flush=True)
    def step(self, name, ok, detail=""):
        self.steps.append((name, "PASS" if ok else "FAIL", detail)); self.log(f"{'PASS' if ok else 'FAIL'} {name}: {detail}")

    # --- build & deploy
    def prepare_worktree(self):
        sh(["git", "rev-parse", "--verify", self.mc], cwd=REPO)  # branch must exist
        if not (self.wt / ".git").exists():
            WORKTREES.mkdir(parents=True, exist_ok=True)
            sh(["git", "worktree", "add", "--detach", str(self.wt), self.mc], cwd=REPO)
        else:
            sh(["git", "checkout", "-q", "--detach", self.mc], cwd=self.wt)
        self.log(f"worktree at {self.wt} on {self.mc} ({sh(['git','rev-parse','--short','HEAD'], cwd=self.wt).stdout.strip()})")

    def deploy(self):
        if self.args.skip_deploy: return
        self.log(f"building and deploying with {self.deploy_task}")
        sh(["./gradlew", self.deploy_task, "-q"], cwd=self.wt, timeout=1800)
        mods = self.inst["dir"] / ".minecraft" / "mods"
        jars = sorted(p.name for p in mods.glob("*bettervillagespawnpoint*"))
        self.notes.append(f"deployed: {', '.join(jars)}")

    def fabric_deps(self):
        if self.jar_frag != "fabric": return
        mods = self.inst["dir"] / ".minecraft" / "mods"
        for name, pid in MODRINTH.items():
            if any(name.replace("-", "") in p.name.lower().replace("-", "").replace("_", "") for p in mods.glob("*.jar")): continue
            if not self.args.fetch_fabric_deps:
                self.notes.append(f"WARNING: {name} missing from instance mods and --fetch-fabric-deps not given; Fabric will refuse to load the mod"); continue
            url = f"https://api.modrinth.com/v2/project/{pid}/version?game_versions=%5B%22{self.mc}%22%5D&loaders=%5B%22fabric%22%5D"
            req = urllib.request.Request(url, headers={"User-Agent": "BetterVillageSpawnPoint-QA/1.0"})
            versions = json.loads(urllib.request.urlopen(req, timeout=30).read())
            if not versions: self.notes.append(f"WARNING: no {name} build on Modrinth for {self.mc}"); continue
            f = versions[0]["files"][0]
            urllib.request.urlretrieve(f["url"], mods / f["filename"]); self.notes.append(f"fetched {f['filename']} from Modrinth")

    # --- server
    def write_server_config(self):
        for rd in self.run_dirs:
            d = self.wt / rd; d.mkdir(parents=True, exist_ok=True)
            (d / "eula.txt").write_text("eula=true\n")
            (d / "server.properties").write_text("\n".join([
                f"server-port={SERVER_PORT}", "online-mode=false", "enforce-secure-profile=false",
                "enable-rcon=true", f"rcon.port={RCON_PORT}", f"rcon.password={RCON_PASS}",
                "level-name=world", "spawn-protection=0", "max-tick-time=-1", "difficulty=peaceful",
                "view-distance=8", "sync-chunk-writes=false", "motd=BVSP QA", ""]))
            if not self.args.keep_world: shutil.rmtree(d / "world", ignore_errors=True)

    def start_server(self, expect_search=True):
        self.server_log = REPORTS_DIR / f"{self.inst['id']}-server-{int(time.time())}.log"
        self.server = subprocess.Popen(["./gradlew", self.run_task], cwd=self.wt, stdout=open(self.server_log, "w"), stderr=subprocess.STDOUT)
        self.log(f"server starting ({self.run_task}); log {self.server_log.name}")
        def ready():
            t = self.server_log.read_text(errors="replace")
            if self.server.poll() is not None: raise RuntimeError("server process exited: " + t[-3000:])
            if expect_search:
                m = re.search(r"Set spawn to (-?\d+), (-?\d+), (-?\d+)", t) or re.search(r"Set spawn to 'BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\}'", t)
                if m and "Done (" in t: return m
                if re.search(r"Village search failed|VANILLA_FALLBACK_FAILED|NO_VILLAGE_FOUND|None of the configured", t): raise RuntimeError("mod reported a failed village search")
                return None
            return ("Done (" in t) or None
        r = wait_for(ready, self.args.server_timeout, what="server ready + village spawn")
        if expect_search:
            x, y, z = (int(v) for v in r.groups())
            # the older log format printed the ground block; the current one prints the standing block
            if "'BlockPos{" in r.group(0): y += 1
            self.village_spawn = (x + 0.5, float(y), z + 0.5)
            self.log(f"village spawn {self.village_spawn}")
        wait_for(lambda: self._rcon_ok(), 90, what="RCON")
        for g in ("gamerule doImmediateRespawn true", "gamerule spawnRadius 0", "gamerule keepInventory true", "difficulty peaceful"):
            rcon(g)

    def _rcon_ok(self):
        try: rcon("list"); return True
        except Exception: return False

    def stop_server(self):
        if not self.server: return
        try: rcon("stop")
        except Exception: pass
        try: self.server.wait(timeout=120)
        except Exception: self.server.kill()
        self.server = None

    # --- client
    def launch_client(self):
        subprocess.Popen([str(PRISM_EXE), "--launch", self.inst["id"], "--server", f"localhost:{SERVER_PORT}"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        def joined():
            out = rcon("list"); m = re.search(r"online:\s*(\S+)", out)
            return m.group(1).strip(",") if m else None
        self.player = wait_for(joined, self.args.join_timeout, what="client to join")
        time.sleep(4)
        return self.player

    def close_client(self):
        pids = client_pids(self.inst["id"])
        for p in pids:
            try: os.kill(p, 15)
            except ProcessLookupError: pass
        wait_for(lambda: not client_pids(self.inst["id"]), 60, every=2, what="client to exit")
        try: wait_for(lambda: "online: " not in rcon("list") or rcon("list").strip().endswith("online:"), 60, what="server to see the leave")
        except Exception: pass

    def join_position_from_log(self):
        t = self.server_log.read_text(errors="replace")
        ms = re.findall(r"logged in with entity id \d+ at \((-?[\d.]+), (-?[\d.]+), (-?[\d.]+)\)", t)
        return tuple(float(v) for v in ms[-1]) if ms else None

    def pos(self):
        self.ensure_player()
        for _ in range(5):
            p = parse_pos(rcon(f"data get entity {self.player} Pos"))
            if p: return p
            time.sleep(2)
        return None

    def player_online(self):
        return bool(self.player) and self.player in rcon("list")

    def ensure_player(self):
        """If the game exited or the client dropped mid-scenario (a closed window, a client crash),
        relaunch it rather than letting every later step fail for lack of a player."""
        if self.player_online(): return
        self.notes.append("client was not connected when a step needed it; relaunched the game")
        self.log("player not online; relaunching the client")
        if client_pids(self.inst["id"]):
            try: self.close_client()
            except Exception: pass
        self.launch_client()

    def kill_player(self):
        self.ensure_player()
        rcon(f"kill {self.player}"); time.sleep(7)

    def near(self, p, q, xz=1.5, y=3.0): return p is not None and q is not None and dist_xz(p, q) <= xz and abs(p[1]-q[1]) <= y
    def fmt(self, p): return "none" if p is None else f"({p[0]:.1f}, {p[1]:.1f}, {p[2]:.1f})"

    # --- the scenario
    def run(self):
        REPORTS_DIR.mkdir(parents=True, exist_ok=True)
        try:
            self.prepare_worktree(); self.deploy(); self.fabric_deps()
            self.write_server_config(); self.start_server()
            vs = self.village_spawn
            self.launch_client()
            jp = self.join_position_from_log()
            self.step("first join lands on village spawn", self.near(jp, vs, xz=1.0, y=2.0), f"joined at {self.fmt(jp)}, village spawn {self.fmt(vs)}")
            self.kill_player(); p = self.pos()
            self.step("die with no respawn point -> village spawn", self.near(p, vs, xz=2.5), f"respawned at {self.fmt(p)}, {dist_xz(p, vs):.1f} blocks from spawn" if p else "no position")
            bed = (vs[0] + 6, vs[1], vs[2] + 6)
            rcon(f"spawnpoint {self.player} {int(bed[0])} {int(bed[1])} {int(bed[2])}")
            self.kill_player(); p = self.pos()
            self.step("die with respawn point set -> respawn point, not village", self.near(p, bed, xz=3.0) and not self.near(p, vs, xz=1.0), f"respawned at {self.fmt(p)}, respawn point {self.fmt(bed)}")
            rcon(f"clearspawnpoint {self.player}")
            self.kill_player(); p = self.pos()
            self.step("die after clearing respawn point -> village spawn", self.near(p, vs, xz=2.5), f"respawned at {self.fmt(p)}")
            before = self.pos(); self.close_client(); self.launch_client(); jp = self.join_position_from_log()
            self.step("leave and rejoin -> same place", self.near(jp, before, xz=2.0), f"left at {self.fmt(before)}, rejoined at {self.fmt(jp)}")
            self.kill_player(); p = self.pos()
            self.step("die after rejoin -> village spawn", self.near(p, vs, xz=2.5), f"respawned at {self.fmt(p)}")
            if not self.args.skip_restart:
                self.close_client(); self.stop_server()
                self.args_keep = self.args.keep_world; self.args.keep_world = True
                self.write_server_config(); self.start_server(expect_search=False); self.args.keep_world = self.args_keep
                t = self.server_log.read_text(errors="replace")
                self.step("server restart restores saved spawn", "Spawn data state is SUCCESS" in t and "Set spawn to" not in t, "restore logged, no re-search" if "Spawn data state is SUCCESS" in t else "no 'Spawn data state is SUCCESS' line")
                self.launch_client(); jp = self.join_position_from_log()
                self.step("rejoin after restart -> same place", self.near(jp, vs, xz=3.0), f"rejoined at {self.fmt(jp)}")
                self.kill_player(); p = self.pos()
                self.step("die after restart -> village spawn", self.near(p, vs, xz=2.5), f"respawned at {self.fmt(p)}")
        except Exception as e:
            self.step("scenario aborted", False, str(e).splitlines()[0][:300] if str(e) else repr(e))
        finally:
            try: self.close_client()
            except Exception: pass
            self.stop_server()
            self.scan_logs()
            self.write_report()

    def scan_logs(self):
        bad = []
        if self.server_log and self.server_log.exists():
            t = self.server_log.read_text(errors="replace")
            for pat in (r"Mixin apply .*failed", r"Critical injection failure", r"InvalidInjectionException", r"Exception in thread", r"\[Better Village Spawn Point\].*(?:error|failed)"):
                for m in re.finditer(pat, t, re.I): bad.append(t[m.start():m.start()+160].splitlines()[0])
        for rd in self.run_dirs:
            for cr in (self.wt / rd / "crash-reports").glob("*.txt"): bad.append(f"crash report: {cr.name}")
        for cr in (self.inst["dir"] / ".minecraft" / "crash-reports").glob("*.txt"):
            if cr.stat().st_mtime > START_TIME: bad.append(f"client crash report: {cr.name}")
        self.step("no mixin errors, exceptions, or crash reports", not bad, "; ".join(sorted(set(bad))[:5]) if bad else "clean")

    def write_report(self):
        passed = sum(1 for s in self.steps if s[1] == "PASS"); total = len(self.steps)
        lines = [f"# {self.inst['id']}  ({'PASS' if passed == total else 'FAIL'} {passed}/{total})\n",
                 f"Minecraft {self.mc}, {self.jar_frag}, player {self.player or '?'}, village spawn {self.fmt(self.village_spawn)}\n", "| step | result | detail |", "|---|---|---|"]
        lines += [f"| {n} | {r} | {d} |" for n, r, d in self.steps]
        if self.notes: lines += ["", "Notes:"] + [f"- {n}" for n in self.notes]
        (REPORTS_DIR / f"{self.inst['id']}.md").write_text("\n".join(lines) + "\n")
        SUMMARY.append((self.inst["id"], passed, total))
        self.log(f"report written: {passed}/{total}")

# ----------------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--instances", help="comma-separated instance ids (default: every instance with a matching branch)")
    ap.add_argument("--loader", choices=["fabric", "forge", "neoforge"], help="only this loader")
    ap.add_argument("--list", action="store_true", help="list targets and exit")
    ap.add_argument("--skip-deploy", action="store_true", help="do not rebuild/deploy the jar into the instance")
    ap.add_argument("--fetch-fabric-deps", action="store_true", help="download Fabric API and Cloth Config from Modrinth into Fabric instances that lack them")
    ap.add_argument("--skip-restart", action="store_true", help="skip the server-restart part of the scenario")
    ap.add_argument("--keep-world", action="store_true", help="reuse the server world instead of generating a new one")
    ap.add_argument("--server-timeout", type=int, default=600); ap.add_argument("--join-timeout", type=int, default=420)
    args = ap.parse_args()
    global REPORTS_DIR, SUMMARY, START_TIME
    START_TIME = time.time(); SUMMARY = []
    inst_dir = prism_instances_dir()
    branches = set(sh(["git", "branch", "--format=%(refname:short)"], cwd=REPO).stdout.split())
    targets = [i for i in discover_instances(inst_dir) if i["mc"] in branches]
    if args.instances: want = set(args.instances.split(",")); targets = [t for t in targets if t["id"] in want]
    if args.loader: targets = [t for t in targets if LOADERS[t["loader"]][3] == args.loader]
    targets.sort(key=lambda t: (vkey(t["mc"]), t["id"]))
    if args.list or not targets:
        for t in targets: print(f"{t['id']:<20} {t['mc']:<8} {LOADERS[t['loader']][3]}")
        if not targets: print("no targets"); return 1
        return 0
    if not PRISM_EXE.exists(): print(f"Prism not found at {PRISM_EXE}"); return 1
    REPORTS_DIR = REPORTS / datetime.now().strftime("%Y-%m-%d_%H%M"); REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    print(f"{len(targets)} target(s); reports in {REPORTS_DIR}", flush=True)
    for t in targets: Target(t, args).run()
    rows = ["# QA matrix summary", "", "| instance | result | passed |", "|---|---|---|"] + [f"| {i} | {'PASS' if p == n else 'FAIL'} | {p}/{n} |" for i, p, n in SUMMARY]
    (REPORTS_DIR / "SUMMARY.md").write_text("\n".join(rows) + "\n"); print("\n".join(rows))
    return 0 if all(p == n for _, p, n in SUMMARY) else 2

if __name__ == "__main__": sys.exit(main())
