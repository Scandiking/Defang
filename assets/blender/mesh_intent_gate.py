# Defang README illustration base — Intent Gate
#
# Builds the meshes for the intent-gate illustration: a phone slab with a
# floating overlay panel, three declared-intent cards, and a partial ring
# suggesting the 8-second countdown. Geometry only — no materials, no render.
#
# Run inside Blender (Scripting tab) or headless:
#   blender --background --python mesh_intent_gate.py -- (then save manually or add a save call)
#
# Requires Blender 3.0+.

import math

import bmesh
import bpy
from mathutils import Matrix

COLLECTION_NAME = "Defang_IntentGate"


def get_collection(name):
    coll = bpy.data.collections.get(name)
    if coll is None:
        coll = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(coll)
    return coll


def link_object(name, bm, collection, location=(0.0, 0.0, 0.0)):
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    obj.location = location
    collection.objects.link(obj)
    return obj


def rounded_box(name, size, radius, collection, location=(0.0, 0.0, 0.0), segments=4):
    """Box of the given (x, y, z) full size with beveled edges."""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co.x *= size[0]
        v.co.y *= size[1]
        v.co.z *= size[2]
    bmesh.ops.bevel(
        bm,
        geom=list(bm.edges),
        offset=radius,
        segments=segments,
        profile=0.5,
        affect='EDGES',
    )
    return link_object(name, bm, collection, location)


def arc_ring(name, radius, tube, angle_deg, collection, location=(0.0, 0.0, 0.0),
             steps=48, start_deg=0.0):
    """Partial torus (open-ended tube swept around Z) for countdown arcs."""
    bm = bmesh.new()
    bmesh.ops.create_circle(bm, cap_ends=False, radius=tube, segments=16)
    # Stand the profile circle up in the XZ plane and push it out to the ring radius.
    bmesh.ops.rotate(bm, verts=bm.verts[:], cent=(0, 0, 0),
                     matrix=Matrix.Rotation(math.radians(90.0), 3, 'X'))
    bmesh.ops.translate(bm, verts=bm.verts[:], vec=(radius, 0.0, 0.0))
    bmesh.ops.rotate(bm, verts=bm.verts[:], cent=(0, 0, 0),
                     matrix=Matrix.Rotation(math.radians(start_deg), 3, 'Z'))
    bmesh.ops.spin(
        bm,
        geom=bm.verts[:] + bm.edges[:],
        cent=(0, 0, 0),
        axis=(0, 0, 1),
        angle=math.radians(angle_deg),
        steps=steps,
        use_merge=False,
        use_duplicate=False,
    )
    return link_object(name, bm, collection, location)


def build():
    coll = get_collection(COLLECTION_NAME)

    # --- Phone -------------------------------------------------------------
    # Standing upright in the XZ plane, screen facing +Y.
    rounded_box("Phone_Body", (3.6, 0.35, 7.4), 0.28, coll)
    rounded_box("Phone_Screen", (3.25, 0.06, 7.0), 0.14, coll,
                location=(0.0, 0.20, 0.0))

    # --- Intent gate overlay panel, floating in front of the screen ---------
    rounded_box("Gate_Panel", (2.9, 0.10, 5.6), 0.16, coll,
                location=(0.0, 0.75, 0.0))

    # Three declared-intent choice cards ("post", "DM", "look something up").
    card_size = (2.3, 0.10, 0.85)
    for i, z in enumerate((0.55, -0.60, -1.75)):
        rounded_box(f"Gate_IntentCard_{i + 1}", card_size, 0.10, coll,
                    location=(0.0, 0.90, z))

    # --- Countdown ring (8 s), upper part of the panel ----------------------
    # 315 degrees of arc = a countdown most of the way around.
    ring = arc_ring("Gate_CountdownRing", radius=0.85, tube=0.075,
                    angle_deg=315.0, start_deg=90.0, collection=coll,
                    location=(0.0, 0.90, 1.85))
    # Rings are built flat around Z; tilt to face the viewer like the panel.
    ring.rotation_euler = (math.radians(90.0), 0.0, 0.0)

    # Dot marking the remaining-time gap in the ring (middle of the 45° gap).
    gap_mid = math.radians(67.5)
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=16, v_segments=12, radius=0.11)
    link_object("Gate_CountdownDot", bm, coll,
                location=(0.85 * math.cos(gap_mid), 0.90,
                          1.85 + 0.85 * math.sin(gap_mid)))


if __name__ == "__main__":
    build()
