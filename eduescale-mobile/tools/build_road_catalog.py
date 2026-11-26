"""Create a catalog of byte ranges for an indexed Valhalla tile extract."""

import argparse
import hashlib
import json
import tarfile
from pathlib import Path


def build_catalog(graph: Path) -> dict:
    digest = hashlib.sha256()
    with graph.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)

    tiles = []
    with tarfile.open(graph, mode="r:") as archive:
        for member in archive:
            parts = member.name.split("/")
            if not member.isfile() or len(parts) < 2 or parts[0] not in {"0", "1", "2"}:
                continue
            name = member.name
            if not name.endswith(".gph") or any(not part.isdigit() for part in parts[:-1]):
                raise ValueError(f"Unexpected tile path: {name}")
            suffix = parts[-1][:-4]
            if not suffix.isdigit() or member.size <= 0:
                raise ValueError(f"Unexpected tile: {name}")
            tile_id = int("".join(parts[1:-1]) + suffix)
            level = int(parts[0])
            tile_size = (4.0, 1.0, 0.25)[level]
            columns = int(360 / tile_size)
            longitude = -180 + (tile_id % columns) * tile_size
            latitude = -90 + (tile_id // columns) * tile_size
            if not -90 <= latitude < 90:
                raise ValueError(f"Tile outside world bounds: {name}")
            source = archive.extractfile(member)
            if source is None:
                raise ValueError(f"Cannot read tile: {name}")
            tiles.append({
                "path": name,
                "offset": member.offset_data,
                "size": member.size,
                "sha256": hashlib.sha256(source.read()).hexdigest(),
                "south": latitude,
                "west": longitude,
                "north": latitude + tile_size,
                "east": longitude + tile_size,
            })
    if not tiles:
        raise ValueError("The archive contains no routing tiles")
    return {
        "version": 1,
        "graphSize": graph.stat().st_size,
        "graphSha256": digest.hexdigest(),
        "tiles": tiles,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("graph", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    catalog = build_catalog(args.graph)
    args.output.write_text(json.dumps(catalog, separators=(",", ":")), encoding="utf-8")
    print(f"Cataloged {len(catalog['tiles'])} tiles from {args.graph}")


if __name__ == "__main__":
    main()
