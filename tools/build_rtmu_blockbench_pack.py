#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import shutil
import sys
import zipfile
from pathlib import Path


def usage() -> int:
    print(
        "usage: build_rtmu_blockbench_pack.py <import_dir> <pack_name> [output_dir]",
        file=sys.stderr,
    )
    return 1


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def ensure_clean_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def find_files(root: Path, pattern: str) -> list[Path]:
    return sorted(path for path in root.rglob(pattern) if path.is_file())


def rel(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def choose_button_texture(rtmu_stub: Path, stem: str, fallback_texture: str | None) -> str:
    item = rtmu_stub / "textures" / "items" / f"{stem.rsplit('_', 1)[0]}.png"
    if item.is_file():
        return rel(item, rtmu_stub)
    if fallback_texture:
        return fallback_texture
    return ""


def find_entity_layout(rtmu_stub: Path, stem: str) -> tuple[float, list[list[float]], list[list[float]]]:
    candidates = sorted(rtmu_stub.rglob(f"{stem}.json"))
    best_seats: list[list[float]] = []
    for path in candidates:
        try:
            data = read_json(path)
        except Exception:
            continue
        entity = data.get("minecraft:entity")
        if not isinstance(entity, dict):
            continue
        groups = entity.get("component_groups", {})
        if not isinstance(groups, dict):
            continue
        for group in groups.values():
            if not isinstance(group, dict):
                continue
            rideable = group.get("minecraft:rideable")
            if not isinstance(rideable, dict):
                continue
            seats = rideable.get("seats")
            if not isinstance(seats, list):
                continue
            extracted: list[list[float]] = []
            for seat in seats:
                if not isinstance(seat, dict):
                    continue
                pos = seat.get("position")
                if not isinstance(pos, list) or len(pos) < 3:
                    continue
                try:
                    extracted.append([float(pos[0]), float(pos[1]), float(pos[2])])
                except Exception:
                    continue
            if len(extracted) > len(best_seats):
                best_seats = extracted

    if not best_seats:
        return 20.0, [[0.78, 0.0, 7.0], [-0.78, 0.0, -7.0]], [[0.78, 1.0, 4.0], [-0.78, 1.0, 4.0], [0.78, 1.0, -4.0], [-0.78, 1.0, -4.0]]

    max_y = max(seat[1] for seat in best_seats)
    max_abs_z = max(abs(seat[2]) for seat in best_seats)
    cab_threshold_y = max_y - 0.35
    cab_threshold_z = max_abs_z - 0.75
    player_positions = [
        seat for seat in best_seats
        if seat[1] >= cab_threshold_y or abs(seat[2]) >= cab_threshold_z
    ]
    if not player_positions:
        player_positions = sorted(best_seats, key=lambda seat: (seat[1], abs(seat[2])), reverse=True)[:2]

    seat_positions = [seat for seat in best_seats if seat not in player_positions]
    if not seat_positions:
        seat_positions = best_seats[:]

    train_distance = max(20.0, max_abs_z * 2.0 + 4.0)
    return train_distance, player_positions, seat_positions


def find_function_layout(rtmu_stub: Path, stem: str) -> tuple[float | None, float | None]:
    base = stem.rsplit("_", 1)[0]
    root = None
    for path in sorted(rtmu_stub.rglob("functions")):
        candidate = path / base
        if candidate.is_dir():
            root = candidate
            break
    if root is None:
        return None, None

    curve_length = None
    trace_spacing = None
    curve_pattern = re.compile(r"positioned\s+\^\^\^(-?\d+(?:\.\d+)?)")
    trace_pattern = re.compile(r"\btp\b.*\^\^\^(-?\d+(?:\.\d+)?)")

    for path in sorted(root.rglob("*.mcfunction")):
        text = path.read_text(encoding="utf-8", errors="ignore")
        for match in curve_pattern.finditer(text):
            value = abs(float(match.group(1)))
            curve_length = value if curve_length is None else max(curve_length, value)
        for match in trace_pattern.finditer(text):
            value = abs(float(match.group(1)))
            trace_spacing = value if trace_spacing is None else max(trace_spacing, value)
    return curve_length, trace_spacing


def infer_bogie_positions(player_positions: list[list[float]], seat_positions: list[list[float]], train_distance: float) -> list[list[float]]:
    source = seat_positions if seat_positions else player_positions
    max_abs_z = max((abs(pos[2]) for pos in source), default=max((abs(pos[2]) for pos in player_positions), default=train_distance * 0.5))
    bogie_z = max(6.0, min(train_distance * 0.5 - 2.75, max_abs_z - 2.0))
    return [[0.0, 0.0, -round(bogie_z, 3)], [0.0, 0.0, round(bogie_z, 3)]]


def write_vehicle_json(
    out_path: Path,
    train_name: str,
    display_name: str,
    model_file: str,
    button_texture: str,
    texture_path: str | None,
    car_index: int,
    train_distance: float,
    bogie_pos: list[list[float]],
    player_pos: list[list[float]],
    seat_pos: list[list[float]],
) -> None:
    data = {
        "trainName": train_name,
        "displayName": display_name,
        "sourceFormat": "bedrock_json_addon",
        "trainDistance": train_distance,
        "bogiePos": bogie_pos,
        "playerPos": player_pos,
        "seatPosF": seat_pos,
        "trainModel2": {
            "modelFile": model_file,
            "buttonTexture": button_texture,
            "textures": [["default", texture_path]] if texture_path else [],
            "offset": [0.0, -1.25, 0.0],
            "scale": 1.0,
            "vehicleType": "Train",
            "smoothing": False,
            "sourceFormat": "bedrock_json_addon",
        },
        "carIndex": car_index,
    }
    out_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def zip_dir(src_dir: Path, zip_path: Path) -> None:
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(src_dir.rglob("*")):
            if path.is_dir():
                continue
            zf.write(path, path.relative_to(src_dir).as_posix())


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        return usage()

    import_dir = Path(argv[1]).expanduser().resolve()
    pack_name = argv[2]
    output_root = Path(argv[3]).expanduser().resolve() if len(argv) >= 4 else import_dir.parent
    if not import_dir.is_dir():
        raise SystemExit(f"import dir not found: {import_dir}")

    rtmu_stub = import_dir / "rtmu_stub"
    if not rtmu_stub.is_dir():
        raise SystemExit(f"rtmu_stub not found: {rtmu_stub}")

    pack_dir = output_root / pack_name
    ensure_clean_dir(pack_dir)

    resources = [path for path in rtmu_stub.iterdir() if path.is_dir()]
    for resource_dir in resources:
        shutil.copytree(resource_dir, pack_dir / resource_dir.name)

    model_files = find_files(pack_dir, "mtc*.json")
    model_files = [path for path in model_files if "/models/entity/" in path.as_posix()]
    if not model_files:
        raise SystemExit("no geometry model json found")

    base_display = "Blockbench Train"
    index_path = import_dir / "import_index.json"
    if index_path.is_file():
        base_display = read_json(index_path).get("displayName", base_display)

    generated = []
    for i, model_path in enumerate(sorted(model_files), start=1):
        stem = model_path.stem
        train_distance, player_pos, seat_pos = find_entity_layout(pack_dir, stem)
        curve_length, trace_spacing = find_function_layout(pack_dir, stem)
        if curve_length is not None:
            train_distance = max(train_distance, round(curve_length * 2.0, 3))
        bogie_pos = infer_bogie_positions(player_pos, seat_pos, train_distance)
        if trace_spacing is not None and trace_spacing > 0.0:
            half_spacing = round(trace_spacing * 0.36, 3)
            bogie_pos = [[0.0, 0.0, -half_spacing], [0.0, 0.0, half_spacing]]
        texture_path = None
        tex_candidate = pack_dir / model_path.parent.parent.parent / "textures" / "entity" / f"{stem}.png"
        if tex_candidate.is_file():
            texture_path = rel(tex_candidate, pack_dir)
        button_texture = choose_button_texture(pack_dir, stem, texture_path)
        train_name = f"{pack_name}_{stem}"
        display_name = f"{base_display} {i}両目"
        out_path = pack_dir / f"modeltrain_{stem}.json"
        write_vehicle_json(
            out_path,
            train_name,
            display_name,
            rel(model_path, pack_dir),
            button_texture,
            texture_path,
            i,
            train_distance,
            bogie_pos,
            player_pos,
            seat_pos,
        )
        generated.append(out_path.name)

    readme = pack_dir / "README_RT MU_BLOCKBENCH_PACK.txt"
    readme.write_text(
        "\n".join(
            [
                "RTMU Blockbench/Bedrock JSON add-on pack",
                "",
                f"generated from: {import_dir}",
                f"pack name: {pack_name}",
                "",
                "Generated train json files:",
                *generated,
            ]
        ),
        encoding="utf-8",
    )

    zip_path = output_root / f"{pack_name}.zip"
    zip_dir(pack_dir, zip_path)
    print(f"pack_dir: {pack_dir}")
    print(f"pack_zip: {zip_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
