#!/usr/bin/env python3
"""
Upload one Minecraft version's release jars to CurseForge.

    python3 publish/curseforge_upload.py --mc 26.2 --jars-dir <dir with the jars> --changelog <file.md> [--dry-run]

The API key is read from the CURSEFORGE_UPLOAD_API_KEY environment variable and never printed.
Files are tagged with the Minecraft version, the loader, and the Java version; Fabric jars get
Fabric API and Cloth Config as required dependencies; the display name follows the project's
existing "[Loader] <filename>" convention.
"""
import argparse, json, os, re, sys, urllib.request
from pathlib import Path

API = "https://minecraft.curseforge.com/api"
PROJECT_ID = 1319614  # better-village-spawn-point
JAVA_FOR = {"26.": "Java 25", "1.21.": "Java 21", "1.20.6": "Java 21", "1.20.5": "Java 21", "1.20.": "Java 17", "1.19.": "Java 17", "1.18.": "Java 17"}
LOADER_OF = {"fabric": "Fabric", "neoforge": "NeoForge", "forge": "Forge"}
FABRIC_DEPS = [{"slug": "fabric-api", "type": "requiredDependency"}, {"slug": "cloth-config", "type": "requiredDependency"}]

def token():
    t = os.environ.get("CURSEFORGE_UPLOAD_API_KEY")
    if not t: sys.exit("CURSEFORGE_UPLOAD_API_KEY is not set in this shell")
    return t

def curl(args, extra_config=""):
    """Run curl with the API token supplied through a stdin config file, so it never appears on the
    command line or in the process list. Uses curl rather than urllib because this Python build has
    no CA bundle for HTTPS verification."""
    import subprocess
    cfg = f'header = "X-Api-Token: {token()}"\nheader = "User-Agent: BetterVillageSpawnPoint-publish/1.0"\nsilent\nshow-error\n{extra_config}'
    r = subprocess.run(["curl", "-K", "-"] + args, input=cfg, capture_output=True, text=True)
    if r.returncode != 0: sys.exit(f"curl failed: {r.stderr.strip()}")
    return r.stdout

def get_json(path):
    return json.loads(curl([API + path]))

def version_ids(mc):
    versions = get_json("/game/versions"); types = {t["id"]: t["name"] for t in get_json("/game/version-types")}
    def pick(name, want_type):
        c = [v for v in versions if v["name"] == name and want_type(types.get(v["gameVersionTypeID"]) or "", v["gameVersionTypeID"])]
        if not c: sys.exit(f"no CurseForge game version named {name!r}")
        return c[0]["id"]
    # the Minecraft version lives under a "Minecraft 1.21"-style family type (or "26.2" for the new scheme), not the generic type 1
    mc_id = pick(mc, lambda tname, tid: tid != 1 and (tname.startswith("Minecraft") or re.match(r"^\d", tname)))
    java = next(j for prefix, j in JAVA_FOR.items() if mc.startswith(prefix))
    java_id = pick(java, lambda tname, tid: tname == "Java")
    loaders = {k: pick(v, lambda tname, tid: tname == "Modloader") for k, v in LOADER_OF.items()}
    # CurseForge requires an environment tag; the mod runs in singleplayer (integrated server) and on dedicated servers
    env = [pick("Client", lambda tname, tid: tname == "Environment"), pick("Server", lambda tname, tid: tname == "Environment")]
    return mc_id, java_id, java, loaders, env

def for_curseforge(text):
    """CurseForge shows the file's version and game version itself, so drop the GitHub release's
    leading title line and the bold markers; keep the rest of the markdown as is."""
    lines = text.strip().splitlines()
    while lines and (lines[0].startswith("#") or not lines[0].strip()): lines.pop(0)
    return "\n".join(lines).replace("**", "").strip() + "\n"

def loader_of(filename):
    n = filename.lower()
    return "neoforge" if "neoforge" in n else "fabric" if "fabric" in n else "forge"

def upload(jar, metadata, dry):
    if dry: return {"dry_run": True}
    meta_file = Path(jar.parent / (jar.name + ".metadata.json")); meta_file.write_text(json.dumps(metadata))
    try:
        out = curl(["-F", f"metadata=<{meta_file}", "-F", f"file=@{jar}", f"{API}/projects/{PROJECT_ID}/upload-file", "-w", "\\n%{http_code}"])
    finally:
        meta_file.unlink(missing_ok=True)
    body, _, code = out.rpartition("\n")
    try: parsed = json.loads(body)
    except Exception: parsed = {"raw": body[:500]}
    if not code.startswith("2"): parsed = {"error": code, **parsed}
    return parsed

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mc", required=True); ap.add_argument("--jars-dir", required=True); ap.add_argument("--changelog", required=True)
    ap.add_argument("--release-type", default="release", choices=["release", "beta", "alpha"]); ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    mc_id, java_id, java, loaders, env = version_ids(a.mc)
    changelog = for_curseforge(Path(a.changelog).read_text())
    jars = sorted(Path(a.jars_dir).glob("*.jar"))
    if not jars: sys.exit("no jars found")
    print(f"project {PROJECT_ID}, Minecraft {a.mc} (id {mc_id}), {java} (id {java_id}), loaders {loaders}, environment {env}, release type {a.release_type}")
    ok = True
    for jar in jars:
        ld = loader_of(jar.name)
        meta = {"changelog": changelog, "changelogType": "markdown", "displayName": f"[{LOADER_OF[ld]}] {jar.name}",
                "gameVersions": [mc_id, loaders[ld], java_id] + env, "releaseType": a.release_type}
        if ld == "fabric": meta["relations"] = {"projects": FABRIC_DEPS}
        shown = dict(meta); shown["changelog"] = f"<{len(changelog)} chars, starts: {changelog.splitlines()[0]!r}>"
        print(f"\n{jar.name} ({jar.stat().st_size} bytes)\n  {json.dumps(shown)}")
        r = upload(jar, meta, a.dry_run); print(f"  -> {r}")
        if "error" in r: ok = False
    return 0 if ok else 1

if __name__ == "__main__": sys.exit(main())
