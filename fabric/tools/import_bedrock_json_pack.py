#!/usr/bin/env python3
from __future__ import annotations

import json
import shutil
import sys
import zipfile
from pathlib import Path


def usage() -> int:
    print(
        "usage: import_bedrock_json_pack.py <source.zip|source_dir> <pack_id> <display_name> [output_dir]",
        file=sys.stderr,
    )
    return 1


def ensure_clean_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def extract_source(source: Path, work_dir: Path) -> Path:
    extracted = work_dir / "source"
    if source.is_dir():
        shutil.copytree(source, extracted)
        return extracted
    if source.suffix.lower() not in {".zip", ".jar"}:
        raise SystemExit(f"unsupported source: {source}")
    with zipfile.ZipFile(source) as zf:
        zf.extractall(extracted)
    return extracted


def find_first(root: Path, patterns: list[str]) -> Path | None:
    for pattern in patterns:
        for path in root.rglob(pattern):
            if path.is_file():
                return path
    return None


def collect_files(root: Path, patterns: list[str]) -> list[Path]:
    found: list[Path] = []
    seen: set[Path] = set()
    for pattern in patterns:
        for path in root.rglob(pattern):
            if path.is_file() and path not in seen:
                seen.add(path)
                found.append(path)
    return sorted(found)


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def relative(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def copy_rel(path: Path | None, src_root: Path, dst_root: Path) -> str | None:
    if path is None:
        return None
    rel = relative(path, src_root)
    target = dst_root / rel
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(path, target)
    return rel


def infer_display_name(client_entity_json: dict, fallback: str) -> str:
    description = client_entity_json.get("minecraft:client_entity", {}).get("description", {})
    identifier = description.get("identifier", fallback)
    return identifier.split(":")[-1] if isinstance(identifier, str) else fallback


def main(argv: list[str]) -> int:
    if len(argv) < 4:
        return usage()

    source = Path(argv[1]).expanduser().resolve()
    pack_id = argv[2]
    display_name = argv[3]
    output_root = Path(argv[4]).expanduser().resolve() if len(argv) >= 5 else Path.cwd() / "dist" / "bedrock_imports"

    if not source.exists():
        raise SystemExit(f"source not found: {source}")

    pack_dir = output_root / pack_id
    ensure_clean_dir(pack_dir)
    extracted_root = extract_source(source, pack_dir)

    client_entity_path = find_first(extracted_root, ["entity/*.json", "entities/*.json"])
    model_paths = collect_files(extracted_root, ["models/entity/*.json", "model/entity/*.json"])
    texture_paths = collect_files(extracted_root, ["textures/**/*.png", "texture/**/*.png"])
    animation_paths = collect_files(extracted_root, ["animations/*.json", "animation_controllers/*.json"])
    function_paths = collect_files(extracted_root, ["functions/**/*.mcfunction"])
    sound_paths = collect_files(extracted_root, ["sounds/**/*.ogg"])

    inferred_name = display_name
    if client_entity_path is not None:
        try:
            inferred_name = infer_display_name(load_json(client_entity_path), display_name)
        except Exception:
            inferred_name = display_name

    import_index = {
        "source": str(source),
        "packId": pack_id,
        "displayName": inferred_name,
        "sourceFormat": "bedrock_json_addon",
        "clientEntity": relative(client_entity_path, extracted_root) if client_entity_path else None,
        "models": [relative(path, extracted_root) for path in model_paths],
        "textures": [relative(path, extracted_root) for path in texture_paths],
        "animations": [relative(path, extracted_root) for path in animation_paths],
        "functions": [relative(path, extracted_root) for path in function_paths],
        "sounds": [relative(path, extracted_root) for path in sound_paths],
    }
    (pack_dir / "import_index.json").write_text(json.dumps(import_index, ensure_ascii=False, indent=2), encoding="utf-8")

    stub_dir = pack_dir / "rtmu_stub"
    stub_dir.mkdir(parents=True, exist_ok=True)
    copied_models = [copy_rel(path, extracted_root, stub_dir) for path in model_paths]
    copied_entities = [
        copy_rel(path, extracted_root, stub_dir)
        for path in collect_files(extracted_root, ["entity/*.json", "entities/*.json"])
    ]
    copied_client_entity = copied_entities[0] if copied_entities else None
    copied_textures = [copy_rel(path, extracted_root, stub_dir) for path in texture_paths]
    copied_functions = [copy_rel(path, extracted_root, stub_dir) for path in function_paths]
    texture_target = copied_textures[0] if copied_textures else None

    vehicle_stub = {
        "trainName": pack_id,
        "displayName": inferred_name,
        "sourceFormat": "bedrock_json_addon",
        "importIndex": "import_index.json",
        "trainDistance": 20.0,
        "bogiePos": [[0.0, 0.0, -6.0], [0.0, 0.0, 6.0]],
        "playerPos": [[0.78, 0.0, 7.0], [-0.78, 0.0, -7.0]],
        "trainModel2": {
            "modelFile": copied_models[0] if copied_models else "",
            "buttonTexture": Path(texture_target).name if texture_target else "",
            "textures": [["default", texture_target]] if texture_target else [],
            "offset": [0.0, -1.05, 0.0],
            "scale": 1.0,
            "smoothing": False,
            "sourceFormat": "bedrock_json_addon",
        },
    }
    (stub_dir / f"modeltrain_{pack_id}.json").write_text(json.dumps(vehicle_stub, ensure_ascii=False, indent=2), encoding="utf-8")

    readme = [
        "RTMU Bedrock JSON import scaffold",
        "",
        f"source: {source}",
        f"pack id: {pack_id}",
        f"display name: {inferred_name}",
        "",
        "This tool extracts Bedrock addon files and builds a simple RTMU import index.",
        "It copies the geometry json, sibling client-entity json, and textures into rtmu_stub/.",
        f"functions copied: {len([x for x in copied_functions if x])}",
        "RTMU can now attempt to load modelFile: \"models/entity/...json\" directly as Bedrock geometry.",
        f"client entities copied: {len([x for x in copied_entities if x])}",
        f"geometry models copied: {len([x for x in copied_models if x])}",
    ]
    (pack_dir / "README_RT MU_IMPORT.txt").write_text("\n".join(readme), encoding="utf-8")

    print(f"generated: {pack_dir}")
    print(f"index: {pack_dir / 'import_index.json'}")
    print(f"stub: {stub_dir / f'modeltrain_{pack_id}.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
