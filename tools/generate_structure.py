"""Generate the shared Ponder stage using only Python's standard library.

Explicit air blocks at the top establish the Ponder world's bounds. Minecraft
structure size alone does not do this. All teaching models are placed by Java.
"""
import gzip
import struct
from pathlib import Path


def text(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def tag(kind, name, payload):
    return bytes([kind]) + text(name) + payload


def integer(name, value):
    return tag(3, name, struct.pack(">i", value))


def integer_list(name, values):
    return tag(9, name, bytes([3]) + struct.pack(">i", len(values))
               + b"".join(struct.pack(">i", v) for v in values))


def compound_list(name, values):
    return tag(9, name, bytes([10]) + struct.pack(">i", len(values)) + b"".join(values))


palette = [tag(8, "Name", text("minecraft:smooth_stone")) + b"\0",
           tag(8, "Name", text("minecraft:air")) + b"\0"]
blocks = [integer("state", 0) + integer_list("pos", [x, 0, z]) + b"\0"
          for x in range(9) for z in range(9)]
blocks += [integer("state", 1) + integer_list("pos", [x, y, z]) + b"\0"
           for x in range(9) for y in range(1, 5) for z in range(9)]
# Use the oldest supported version: this stone/air stage is shared by 1.20.1 and 1.21.1.
root = (integer("DataVersion", 3465) + integer_list("size", [9, 5, 9])
        + compound_list("palette", palette) + compound_list("blocks", blocks)
        + compound_list("entities", []) + b"\0")
destination = Path(__file__).resolve().parents[1] / "src/main/resources/assets/ae2ponder/ponder/stage.nbt"
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_bytes(gzip.compress(tag(10, "", root), mtime=0))
print(f"Generated 9 x 5 x 9 stage ({len(blocks)} blocks)")
