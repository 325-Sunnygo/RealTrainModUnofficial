#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "usage: $0 <source.blend|source.fbx> <pack_id> <display_name> [output_dir]" >&2
  exit 1
fi

SOURCE_FILE="$1"
PACK_ID="$2"
DISPLAY_NAME="$3"
OUTPUT_ROOT="${4:-$(pwd)/dist/generated_vehicle_packs}"

if [[ ! -f "$SOURCE_FILE" ]]; then
  echo "source file not found: $SOURCE_FILE" >&2
  exit 1
fi

find_blender() {
  if [[ -n "${RTMU_BLENDER:-}" && -x "${RTMU_BLENDER}" ]]; then
    printf '%s\n' "${RTMU_BLENDER}"
    return 0
  fi
  if [[ -x "/Applications/Blender.app/Contents/MacOS/Blender" ]]; then
    printf '%s\n' "/Applications/Blender.app/Contents/MacOS/Blender"
    return 0
  fi
  if command -v blender >/dev/null 2>&1; then
    command -v blender
    return 0
  fi
  return 1
}

BLENDER_BIN="$(find_blender || true)"
if [[ -z "${BLENDER_BIN}" ]]; then
  echo "Blender executable not found. Set RTMU_BLENDER if needed." >&2
  exit 1
fi

PACK_DIR="${OUTPUT_ROOT}/${PACK_ID}"
MODEL_DIR="${PACK_DIR}/models"
META_DIR="${PACK_DIR}/meta"
mkdir -p "${MODEL_DIR}" "${META_DIR}"

BLENDER_SCRIPT="${META_DIR}/export_vehicle_pack.py"
cat > "${BLENDER_SCRIPT}" <<'PY'
import bpy, json, math, os, sys
from mathutils import Vector

argv = sys.argv
if "--" in argv:
    argv = argv[argv.index("--") + 1:]
else:
    argv = []
if len(argv) < 4:
    raise SystemExit("usage: export_vehicle_pack.py <input> <output_obj> <output_meta> <display_name>")

source_file, output_obj, output_meta, display_name = argv[:4]
source_ext = os.path.splitext(source_file)[1].lower()
os.makedirs(os.path.dirname(output_obj), exist_ok=True)
os.makedirs(os.path.dirname(output_meta), exist_ok=True)

bpy.ops.wm.read_factory_settings(use_empty=True)
for obj in list(bpy.data.objects):
    bpy.data.objects.remove(obj, do_unlink=True)

if source_ext == ".fbx":
    bpy.ops.import_scene.fbx(filepath=source_file, automatic_bone_orientation=True)
elif source_ext == ".blend":
    with bpy.data.libraries.load(source_file, link=False) as (data_from, data_to):
        data_to.collections = data_from.collections
        data_to.objects = data_from.objects
    for coll in [c for c in data_to.collections if c is not None]:
        if coll.name not in bpy.context.scene.collection.children:
            bpy.context.scene.collection.children.link(coll)
    for obj in [o for o in data_to.objects if o is not None and o.name not in bpy.context.scene.objects]:
        bpy.context.scene.collection.objects.link(obj)
else:
    raise SystemExit(f"unsupported source extension: {source_ext}")

mesh_objects = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
if not mesh_objects:
    raise SystemExit("no mesh objects found")

depsgraph = bpy.context.evaluated_depsgraph_get()
mins = [float("inf")] * 3
maxs = [float("-inf")] * 3
for obj in mesh_objects:
    evaluated = obj.evaluated_get(depsgraph)
    for corner in evaluated.bound_box:
        world = evaluated.matrix_world @ Vector(corner)
        mins[0] = min(mins[0], world.x)
        mins[1] = min(mins[1], world.y)
        mins[2] = min(mins[2], world.z)
        maxs[0] = max(maxs[0], world.x)
        maxs[1] = max(maxs[1], world.y)
        maxs[2] = max(maxs[2], world.z)

length = maxs[1] - mins[1]
width = maxs[0] - mins[0]
height = maxs[2] - mins[2]
center_x = (mins[0] + maxs[0]) * 0.5
center_y = (mins[1] + maxs[1]) * 0.5
center_z = mins[2]

bpy.ops.object.select_all(action="DESELECT")
for obj in mesh_objects:
    obj.select_set(True)
bpy.context.view_layer.objects.active = mesh_objects[0]
bpy.ops.wm.obj_export(
    filepath=output_obj,
    export_selected_objects=True,
    export_materials=True,
    apply_modifiers=True,
    export_normals=False,
    export_uv=True,
    path_mode="COPY",
    forward_axis="NEGATIVE_Z",
    up_axis="Y",
)

meta = {
    "displayName": display_name,
    "meshCount": len(mesh_objects),
    "bounds": {
        "min": mins,
        "max": maxs,
        "length": length,
        "width": width,
        "height": height,
        "center": [center_x, center_y, center_z],
    },
    "defaults": {
        "trainDistance": max(4.5, round(length * 0.9, 3)),
        "bogiePos": [
            [0.0, 0.0, round(length * 0.32, 3)],
            [0.0, 0.0, round(-length * 0.32, 3)],
        ],
        "playerPos": [
            [0.78, 0.0, round(length * 0.42, 3)],
            [-0.78, 0.0, round(-length * 0.42, 3)],
        ],
        "offset": [round(-center_x, 4), round(-center_z, 4), round(-center_y, 4)],
    },
}

with open(output_meta, "w", encoding="utf-8") as fh:
    json.dump(meta, fh, ensure_ascii=False, indent=2)
PY

OUTPUT_OBJ="${MODEL_DIR}/${PACK_ID}.obj"
OUTPUT_META="${META_DIR}/${PACK_ID}.json"
"${BLENDER_BIN}" -b --python "${BLENDER_SCRIPT}" -- "${SOURCE_FILE}" "${OUTPUT_OBJ}" "${OUTPUT_META}" "${DISPLAY_NAME}"
rm -f "${BLENDER_SCRIPT}"

python3 - <<'PY' "${PACK_ID}" "${DISPLAY_NAME}" "${PACK_DIR}" "${OUTPUT_META}"
import json, os, pathlib, sys

pack_id, display_name, pack_dir, meta_file = sys.argv[1:5]
pack_path = pathlib.Path(pack_dir)
meta = json.loads(pathlib.Path(meta_file).read_text(encoding="utf-8"))
defaults = meta["defaults"]

json_path = pack_path / f"modeltrain_{pack_id}.json"
data = {
    "trainName": pack_id,
    "displayName": display_name,
    "trainDistance": defaults["trainDistance"],
    "playerPos": defaults["playerPos"],
    "bogiePos": defaults["bogiePos"],
    "trainModel2": {
        "modelFile": f"models/{pack_id}.obj",
        "offset": defaults["offset"],
        "scale": 1.0,
        "smoothing": True,
        "doCulling": True,
        "renderLight": False,
        "notDisplayCab": True
    }
}
json_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
PY

(
  cd "${OUTPUT_ROOT}"
  rm -f "${PACK_ID}.zip"
  zip -r "${PACK_ID}.zip" "${PACK_ID}" >/dev/null
)

echo "Generated pack: ${OUTPUT_ROOT}/${PACK_ID}.zip"
