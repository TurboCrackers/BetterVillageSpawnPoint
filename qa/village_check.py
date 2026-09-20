#!/usr/bin/env python3
"""Is a position inside a village? Reads the saved world's structure data directly (region files),
with no third-party library, and reports the village structure covering that chunk, how many pieces
it has, and whether the X/Z lies inside any piece's bounding box.

    python3 qa/village_check.py --world /path/to/world --x -518 --z -308

Exit code 0 = inside a village piece, 1 = inside the village's overall box but not any piece,
2 = no village structure starts here. Works for 1.18+ worlds (chunk NBT "structures"/"starts").
"""
import argparse, gzip, struct, sys, zlib
from pathlib import Path


# ----------------------------------------------------------------------------- minimal NBT reader
class NBT:
    def __init__(self, data): self.d = data; self.p = 0
    def u8(self): v = self.d[self.p]; self.p += 1; return v
    def i16(self): v = struct.unpack_from(">h", self.d, self.p)[0]; self.p += 2; return v
    def i32(self): v = struct.unpack_from(">i", self.d, self.p)[0]; self.p += 4; return v
    def i64(self): v = struct.unpack_from(">q", self.d, self.p)[0]; self.p += 8; return v
    def f32(self): v = struct.unpack_from(">f", self.d, self.p)[0]; self.p += 4; return v
    def f64(self): v = struct.unpack_from(">d", self.d, self.p)[0]; self.p += 8; return v
    def string(self):
        n = struct.unpack_from(">H", self.d, self.p)[0]; self.p += 2
        s = self.d[self.p:self.p + n].decode("utf-8", "replace"); self.p += n; return s
    def payload(self, t):
        if t == 1: return self.u8() - 256 if self.d[self.p] > 127 else self.u8()
        if t == 2: return self.i16()
        if t == 3: return self.i32()
        if t == 4: return self.i64()
        if t == 5: return self.f32()
        if t == 6: return self.f64()
        if t == 7: n = self.i32(); v = self.d[self.p:self.p + n]; self.p += n; return v
        if t == 8: return self.string()
        if t == 9:
            et = self.u8(); n = self.i32(); return [self.payload(et) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                et = self.u8()
                if et == 0: return out
                name = self.string(); out[name] = self.payload(et)
        if t == 11: n = self.i32(); v = list(struct.unpack_from(f">{n}i", self.d, self.p)); self.p += 4 * n; return v
        if t == 12: n = self.i32(); v = list(struct.unpack_from(f">{n}q", self.d, self.p)); self.p += 8 * n; return v
        raise ValueError(f"unknown NBT tag {t}")
    def root(self):
        t = self.u8(); assert t == 10, "root is not a compound"; self.string(); return self.payload(10)


def read_chunk(world: Path, cx: int, cz: int):
    region = world / "region" / f"r.{cx >> 5}.{cz >> 5}.mca"
    if not region.exists(): return None
    data = region.read_bytes()
    idx = 4 * ((cx & 31) + (cz & 31) * 32)
    off = (struct.unpack_from(">I", data, idx)[0] >> 8) * 4096
    if off == 0: return None
    length = struct.unpack_from(">I", data, off)[0]; comp = data[off + 4]; raw = data[off + 5:off + 4 + length]
    if comp == 1: raw = gzip.decompress(raw)
    elif comp == 2: raw = zlib.decompress(raw)
    elif comp == 3: pass
    else: raise ValueError(f"unsupported chunk compression {comp} (LZ4 needs a newer reader)")
    return NBT(raw).root()


def village_starts(chunk):
    starts = (chunk.get("structures") or chunk.get("Level", {}).get("Structures", {})).get("starts", {})
    return {k: v for k, v in starts.items() if v.get("id", k) != "INVALID"}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--world", required=True); ap.add_argument("--x", type=float, required=True); ap.add_argument("--z", type=float, required=True)
    ap.add_argument("--match", default="village", help="substring of the structure id to look for (default: village)")
    a = ap.parse_args()
    world = Path(a.world); x, z = int(a.x // 1), int(a.z // 1); cx, cz = x >> 4, z >> 4
    # the start that owns this area may live in a neighbouring chunk: scan the 5x5 chunks around the position
    found = []
    for dcx in range(-2, 3):
        for dcz in range(-2, 3):
            chunk = read_chunk(world, cx + dcx, cz + dcz)
            if not chunk: continue
            for sid, start in village_starts(chunk).items():
                if a.match not in sid: continue
                pieces = start.get("Children", [])
                bbs = [p["BB"] for p in pieces if "BB" in p]
                if not bbs: continue
                found.append((sid, cx + dcx, cz + dcz, bbs, start.get("BB")))
    if not found:
        print(f"no '{a.match}' structure start within 2 chunks of ({x}, {z})"); return 2
    best = None
    for sid, scx, scz, bbs, whole in found:
        inside = [bb for bb in bbs if bb[0] <= x <= bb[3] and bb[2] <= z <= bb[5]]
        def dist(bb):
            dx = max(bb[0] - x, 0, x - bb[3]); dz = max(bb[2] - z, 0, z - bb[5]); return (dx * dx + dz * dz) ** 0.5
        nearest = min(dist(bb) for bb in bbs)
        minx, minz = min(bb[0] for bb in bbs), min(bb[2] for bb in bbs); maxx, maxz = max(bb[3] for bb in bbs), max(bb[5] for bb in bbs)
        in_whole = minx <= x <= maxx and minz <= z <= maxz
        print(f"{sid} (start chunk {scx},{scz}): {len(bbs)} pieces, village extends x {minx}..{maxx}, z {minz}..{maxz}")
        print(f"  ({x}, {z}) is {'INSIDE' if inside else 'outside'} a piece box ({len(inside)} covering); nearest piece edge {nearest:.1f} blocks away; {'inside' if in_whole else 'outside'} the village's overall footprint")
        if best is None or (bool(inside), -nearest) > (bool(best[0]), -best[1]): best = (inside, nearest, in_whole)
    return 0 if best[0] else (1 if best[2] else 2)


if __name__ == "__main__": sys.exit(main())
