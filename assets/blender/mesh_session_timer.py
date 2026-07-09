# Defang README illustration base — Session Timer
#
# Builds the meshes for the session-timer illustration: an hourglass (framed,
# with sand running out) and a floating HUD pill with a countdown arc, echoing
# the small in-session timer overlay. Geometry only — no materials, no render.
#
# Run inside Blender (Scripting tab) or headless:
#   blender --background --python mesh_session_timer.py
#
# Requires Blender 3.0+.

import math

import bmesh
import bpy
from mathutils import Matrix

COLLECTION_NAME = "Defang_SessionTimer"


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


def cylinder(name, radius, depth, collection, location=(0.0, 0.0, 0.0), segments=32):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=segments,
                          radius1=radius, radius2=radius, depth=depth)
    return link_object(name, bm, collection, location)


def cone(name, radius, depth, collection, location=(0.0, 0.0, 0.0), segments=32):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=segments,
                          radius1=radius, radius2=0.0, depth=depth)
    return link_object(name, bm, collection, location)


def lathe(name, profile, collection, location=(0.0, 0.0, 0.0), steps=48):
    """Revolve a list of (radius, z) profile points 360° around the Z axis."""
    bm = bmesh.new()
    verts = [bm.verts.new((r, 0.0, z)) for r, z in profile]
    for a, b in zip(verts, verts[1:]):
        bm.edges.new((a, b))
    bmesh.ops.spin(
        bm,
        geom=bm.verts[:] + bm.edges[:],
        cent=(0, 0, 0),
        axis=(0, 0, 1),
        angle=2.0 * math.pi,
        steps=steps,
        use_merge=True,
        use_duplicate=False,
    )
    return link_object(name, bm, collection, location)


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


def arc_ring(name, radius, tube, angle_deg, collection, location=(0.0, 0.0, 0.0),
             steps=48, start_deg=0.0):
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

    # --- Hourglass glass body (lathe profile, waisted in the middle) --------
    glass_profile = [
        (0.95, -1.50),
        (1.05, -1.10),
        (1.02, -0.70),
        (0.75, -0.35),
        (0.35, -0.14),
        (0.13, 0.00),
        (0.35, 0.14),
        (0.75, 0.35),
        (1.02, 0.70),
        (1.05, 1.10),
        (0.95, 1.50),
    ]
    lathe("Hourglass_Glass", glass_profile, coll)

    # --- Frame: top/bottom plates and three pillars --------------------------
    cylinder("Hourglass_PlateBottom", radius=1.35, depth=0.22, collection=coll,
             location=(0.0, 0.0, -1.63))
    cylinder("Hourglass_PlateTop", radius=1.35, depth=0.22, collection=coll,
             location=(0.0, 0.0, 1.63))
    for i in range(3):
        a = math.radians(90.0 + i * 120.0)
        cylinder(f"Hourglass_Pillar_{i + 1}", radius=0.09, depth=3.04,
                 collection=coll,
                 location=(1.18 * math.cos(a), 1.18 * math.sin(a), 0.0),
                 segments=16)

    # --- Sand: mostly run out — the 15 minutes are nearly up -----------------
    # Small remainder in the upper bulb, thin falling stream, pile below.
    cone("Sand_Top", radius=0.42, depth=0.24, collection=coll,
         location=(0.0, 0.0, 0.30))
    cylinder("Sand_Stream", radius=0.035, depth=1.15, collection=coll,
             location=(0.0, 0.0, -0.60), segments=12)
    cone("Sand_Pile", radius=0.62, depth=0.55, collection=coll,
         location=(0.0, 0.0, -1.22))

    # --- HUD pill, floating beside the hourglass -----------------------------
    # Echoes the small in-session countdown overlay: a capsule-shaped pill
    # with a nearly-depleted countdown arc on its left end.
    pill = rounded_box("HUD_Pill", (2.2, 0.18, 0.75), 0.30, coll,
                       location=(2.6, 0.0, 1.1), segments=6)
    pill.rotation_euler = (0.0, math.radians(-12.0), 0.0)

    ring = arc_ring("HUD_CountdownArc", radius=0.24, tube=0.045,
                    angle_deg=80.0, start_deg=90.0, collection=coll,
                    location=(1.95, -0.14, 1.1 + 0.62 * math.sin(math.radians(12.0))))
    ring.rotation_euler = (math.radians(90.0), math.radians(-12.0), 0.0)


if __name__ == "__main__":
    build()
