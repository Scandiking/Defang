# Defang README illustration base — Cool-down Lock
#
# Builds the meshes for the cool-down illustration: an app-icon tile with a
# padlock resting on it, wrapped by a clock arc with tick marks suggesting the
# 30-minute cool-down before the app unlocks again. Geometry only — no
# materials, no render.
#
# Run inside Blender (Scripting tab) or headless:
#   blender --background --python mesh_cooldown_lock.py
#
# Requires Blender 3.0+.

import math

import bmesh
import bpy
from mathutils import Matrix

COLLECTION_NAME = "Defang_CooldownLock"


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
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co.x *= size[0]
        v.co.y *= size[1]
        v.co.z *= size[2]
    bmesh.ops.bevel(bm, geom=list(bm.edges), offset=radius,
                    segments=segments, profile=0.5, affect='EDGES')
    return link_object(name, bm, collection, location)


def cylinder(name, radius, depth, collection, location=(0.0, 0.0, 0.0), segments=32):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=segments,
                          radius1=radius, radius2=radius, depth=depth)
    return link_object(name, bm, collection, location)


def arc_ring(name, radius, tube, angle_deg, collection, location=(0.0, 0.0, 0.0),
             steps=64, start_deg=0.0):
    """Partial torus (open-ended tube swept around Z)."""
    bm = bmesh.new()
    bmesh.ops.create_circle(bm, cap_ends=False, radius=tube, segments=16)
    bmesh.ops.rotate(bm, verts=bm.verts[:], cent=(0, 0, 0),
                     matrix=Matrix.Rotation(math.radians(90.0), 3, 'X'))
    bmesh.ops.translate(bm, verts=bm.verts[:], vec=(radius, 0.0, 0.0))
    bmesh.ops.rotate(bm, verts=bm.verts[:], cent=(0, 0, 0),
                     matrix=Matrix.Rotation(math.radians(start_deg), 3, 'Z'))
    bmesh.ops.spin(bm, geom=bm.verts[:] + bm.edges[:], cent=(0, 0, 0),
                   axis=(0, 0, 1), angle=math.radians(angle_deg), steps=steps,
                   use_merge=False, use_duplicate=False)
    return link_object(name, bm, collection, location)


def build():
    coll = get_collection(COLLECTION_NAME)

    # --- App icon tile, lying flat (a watched app, now locked) ---------------
    rounded_box("App_Tile", (2.4, 2.4, 0.35), 0.30, coll,
                location=(0.0, 0.0, 0.0), segments=6)

    # --- Padlock resting on the tile -----------------------------------------
    # Body: rounded block standing upright (thin in Y).
    rounded_box("Lock_Body", (1.30, 0.60, 1.15), 0.16, coll,
                location=(0.0, 0.0, 0.75), segments=5)

    # Shackle: half-torus arch plus two straight legs dropping into the body.
    shackle_r = 0.42
    shackle_tube = 0.09
    arch = arc_ring("Lock_Shackle_Arch", radius=shackle_r, tube=shackle_tube,
                    angle_deg=180.0, start_deg=0.0, collection=coll,
                    location=(0.0, 0.0, 1.75), steps=32)
    # Built flat around Z; stand it up in the XZ plane so it arches over the body.
    arch.rotation_euler = (math.radians(90.0), 0.0, 0.0)

    leg_len = 0.45
    for side, x in (("L", -shackle_r), ("R", shackle_r)):
        cylinder(f"Lock_Shackle_Leg_{side}", radius=shackle_tube,
                 depth=leg_len, collection=coll,
                 location=(x, 0.0, 1.75 - leg_len / 2.0), segments=16)

    # Keyhole: small cylinder proud of the body front face.
    keyhole = cylinder("Lock_Keyhole", radius=0.13, depth=0.10,
                       collection=coll, location=(0.0, -0.34, 0.80),
                       segments=24)
    keyhole.rotation_euler = (math.radians(90.0), 0.0, 0.0)

    # --- Cool-down clock arc around the tile ---------------------------------
    # 270° swept = three quarters of the cool-down still to wait out.
    arc_ring("Cooldown_Arc", radius=2.15, tube=0.07, angle_deg=270.0,
             start_deg=90.0, collection=coll, location=(0.0, 0.0, 0.0))

    # Twelve tick marks around the full circle, clock-face style.
    for i in range(12):
        a = math.radians(i * 30.0)
        tick = rounded_box(f"Cooldown_Tick_{i + 1:02d}", (0.22, 0.06, 0.06),
                           0.02, coll,
                           location=(2.45 * math.cos(a), 2.45 * math.sin(a), 0.0),
                           segments=2)
        tick.rotation_euler = (0.0, 0.0, a)


if __name__ == "__main__":
    build()
