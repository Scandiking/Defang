"""
Defang — Cool-down lockout illustration
Paste into Blender's Scripting workspace and run.
Renders a padlock with a warm ambient glow on a dark reflective surface.

Output: //renders/cooldown.png
Tested on Blender 4.x
"""

import bpy
import math

OUTPUT     = "//renders/cooldown.png"
RESOLUTION = (800, 900)

# ── Reset ─────────────────────────────────────────────────────────────────────
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete()
for mat in bpy.data.materials:
    bpy.data.materials.remove(mat)

# ── Materials ─────────────────────────────────────────────────────────────────
def principled_mat(name, base_color, metallic=0.0, roughness=0.4, emission=None, em_strength=0.0):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    nodes.clear()
    out  = nodes.new("ShaderNodeOutputMaterial")
    bsdf = nodes.new("ShaderNodeBsdfPrincipled")
    bsdf.inputs["Base Color"].default_value   = (*base_color, 1.0)
    bsdf.inputs["Metallic"].default_value     = metallic
    bsdf.inputs["Roughness"].default_value    = roughness
    if emission and em_strength > 0:
        bsdf.inputs["Emission Color"].default_value    = (*emission, 1.0)
        bsdf.inputs["Emission Strength"].default_value = em_strength
    mat.node_tree.links.new(bsdf.outputs[0], out.inputs[0])
    return mat

mat_body  = principled_mat("LockBody",  (0.08, 0.07, 0.06), metallic=0.9, roughness=0.15,
                            emission=(0.69, 0.49, 0.29), em_strength=0.3)
mat_shack = principled_mat("Shackle",   (0.12, 0.10, 0.08), metallic=1.0, roughness=0.1)
mat_keyhole = principled_mat("Keyhole", (0.01, 0.01, 0.01), metallic=0.0, roughness=1.0)
mat_floor = principled_mat("Floor",     (0.03, 0.03, 0.04), metallic=0.8, roughness=0.05)
mat_glow  = principled_mat("GlowRing",  (0.69, 0.49, 0.29), metallic=0.0, roughness=1.0,
                            emission=(0.69, 0.49, 0.29), em_strength=6.0)

# ── Lock body ─────────────────────────────────────────────────────────────────
bpy.ops.mesh.primitive_cube_add(size=1)
body = bpy.context.active_object
body.name  = "LockBody"
body.scale = (0.7, 0.35, 0.65)
body.location = (0, 0, 0)
body.data.materials.append(mat_body)

# ── Shackle (U-bar) — two vertical cylinders + top torus segment ──────────────
for x, name in ((-0.28, "ShackleL"), (0.28, "ShackleR")):
    bpy.ops.mesh.primitive_cylinder_add(radius=0.055, depth=0.55, location=(x, 0, 0.6))
    cyl = bpy.context.active_object
    cyl.name = name
    cyl.data.materials.append(mat_shack)

bpy.ops.mesh.primitive_torus_add(
    major_radius=0.28, minor_radius=0.055,
    major_segments=32, minor_segments=12,
    location=(0, 0, 0.875),
)
torus = bpy.context.active_object
torus.name = "ShackleTop"
torus.rotation_euler = (math.radians(90), 0, 0)
# Keep only the top half by applying a boolean — simpler: just use the full torus
# (top half is above the lock body anyway in this composition)
torus.data.materials.append(mat_shack)

# ── Keyhole (small dark oval) ─────────────────────────────────────────────────
bpy.ops.mesh.primitive_cylinder_add(radius=0.07, depth=0.04, vertices=32,
                                    location=(0, -0.37, -0.05))
kh_top = bpy.context.active_object
kh_top.rotation_euler = (math.radians(90), 0, 0)
kh_top.data.materials.append(mat_keyhole)

bpy.ops.mesh.primitive_cone_add(radius1=0.055, radius2=0.0, depth=0.1, vertices=4,
                                 location=(0, -0.37, -0.16))
kh_bot = bpy.context.active_object
kh_bot.rotation_euler = (math.radians(90), 0, math.radians(45))
kh_bot.data.materials.append(mat_keyhole)

# ── Subtle glow ring on floor ─────────────────────────────────────────────────
bpy.ops.mesh.primitive_torus_add(major_radius=0.55, minor_radius=0.03,
                                  major_segments=64, minor_segments=8,
                                  location=(0, 0, -0.63))
glow = bpy.context.active_object
glow.name = "GlowRing"
glow.data.materials.append(mat_glow)

# ── Floor ─────────────────────────────────────────────────────────────────────
bpy.ops.mesh.primitive_plane_add(size=6, location=(0, 0, -0.65))
bpy.context.active_object.data.materials.append(mat_floor)

# ── Lighting ──────────────────────────────────────────────────────────────────
bpy.ops.object.light_add(type='AREA', location=(1.5, -2, 2.5))
key = bpy.context.active_object
key.data.energy = 250
key.data.size   = 2
key.rotation_euler = (math.radians(50), 0, math.radians(30))

bpy.ops.object.light_add(type='AREA', location=(-1, 1, 1.5))
rim = bpy.context.active_object
rim.data.energy = 80
rim.data.color  = (0.69, 0.49, 0.29)
rim.data.size   = 1

# ── Camera ────────────────────────────────────────────────────────────────────
bpy.ops.object.camera_add(location=(0, -2.8, 0.6))
cam = bpy.context.active_object
cam.rotation_euler = (math.radians(88), 0, 0)
cam.data.lens = 70
bpy.context.scene.camera = cam

# ── World ─────────────────────────────────────────────────────────────────────
bpy.context.scene.world.use_nodes = True
bg = bpy.context.scene.world.node_tree.nodes["Background"]
bg.inputs["Color"].default_value    = (0.01, 0.01, 0.015, 1.0)
bg.inputs["Strength"].default_value = 0.0

# ── Render ────────────────────────────────────────────────────────────────────
scene = bpy.context.scene
scene.render.engine             = 'CYCLES'
scene.cycles.samples            = 128
scene.render.resolution_x       = RESOLUTION[0]
scene.render.resolution_y       = RESOLUTION[1]
scene.render.filepath           = OUTPUT
scene.render.image_settings.file_format = 'PNG'

bpy.ops.render.render(write_still=True)
print("Rendered:", OUTPUT)
