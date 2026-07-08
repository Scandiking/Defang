"""
Defang — Intent Gate illustration
Paste into Blender's Scripting workspace and run.
Renders a phone slab with a glowing translucent barrier in front of it.

Output: //renders/intent_gate.png  (relative to your .blend file, or set OUTPUT below)
Tested on Blender 4.x
"""

import bpy
import math

OUTPUT = "//renders/intent_gate.png"
RESOLUTION = (1200, 800)

# ── Reset ─────────────────────────────────────────────────────────────────────
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete()
for mat in bpy.data.materials:
    bpy.data.materials.remove(mat)

# ── Materials ─────────────────────────────────────────────────────────────────
def emission_mat(name, color, strength=2.0):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    nodes.clear()
    out = nodes.new("ShaderNodeOutputMaterial")
    em  = nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = (*color, 1.0)
    em.inputs["Strength"].default_value = strength
    mat.node_tree.links.new(em.outputs[0], out.inputs[0])
    return mat

def glass_mat(name, color, alpha=0.18):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    nodes.clear()
    out  = nodes.new("ShaderNodeOutputMaterial")
    mix  = nodes.new("ShaderNodeMixShader")
    diff = nodes.new("ShaderNodeBsdfDiffuse")
    tran = nodes.new("ShaderNodeBsdfTransparent")
    diff.inputs["Color"].default_value = (*color, 1.0)
    mix.inputs["Fac"].default_value = 1.0 - alpha
    mat.node_tree.links.new(tran.outputs[0], mix.inputs[1])
    mat.node_tree.links.new(diff.outputs[0], mix.inputs[2])
    mat.node_tree.links.new(mix.outputs[0],  out.inputs[0])
    mat.blend_method = 'BLEND'
    return mat

def dark_mat(name):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    nodes.clear()
    out  = nodes.new("ShaderNodeOutputMaterial")
    bsdf = nodes.new("ShaderNodeBsdfPrincipled")
    bsdf.inputs["Base Color"].default_value = (0.04, 0.04, 0.06, 1.0)
    bsdf.inputs["Metallic"].default_value   = 0.8
    bsdf.inputs["Roughness"].default_value  = 0.2
    mat.node_tree.links.new(bsdf.outputs[0], out.inputs[0])
    return mat

mat_phone   = dark_mat("Phone")
mat_screen  = emission_mat("Screen", (0.05, 0.05, 0.12), strength=1.0)
mat_gate    = glass_mat("Gate", (0.44, 0.31, 0.18), alpha=0.22)
mat_glow    = emission_mat("GateGlow", (0.69, 0.49, 0.29), strength=4.0)
mat_floor   = dark_mat("Floor")

# ── Phone body ────────────────────────────────────────────────────────────────
bpy.ops.mesh.primitive_cube_add(size=1)
phone = bpy.context.active_object
phone.name = "Phone"
phone.scale = (0.5, 0.08, 0.9)
phone.location = (0, 0.3, 0)
phone.data.materials.append(mat_phone)

# Screen (slightly in front of phone face)
bpy.ops.mesh.primitive_plane_add(size=1)
screen = bpy.context.active_object
screen.name = "Screen"
screen.scale = (0.42, 0.75, 1)
screen.location = (0, 0.22, 0)
screen.rotation_euler = (math.radians(90), 0, 0)
screen.data.materials.append(mat_screen)

# ── Gate (barrier plane) ──────────────────────────────────────────────────────
bpy.ops.mesh.primitive_plane_add(size=1)
gate = bpy.context.active_object
gate.name = "Gate"
gate.scale = (0.7, 1.1, 1)
gate.location = (0, -0.15, 0)
gate.rotation_euler = (math.radians(90), 0, 0)
gate.data.materials.append(mat_gate)

# Gate edge glow (thin emissive strip)
for x_off in (-0.7, 0.7):
    bpy.ops.mesh.primitive_cube_add(size=1)
    edge = bpy.context.active_object
    edge.scale = (0.012, 0.005, 1.1)
    edge.location = (x_off, -0.15, 0)
    edge.rotation_euler = (0, 0, 0)
    edge.data.materials.append(mat_glow)

# ── Floor ─────────────────────────────────────────────────────────────────────
bpy.ops.mesh.primitive_plane_add(size=6)
floor = bpy.context.active_object
floor.name = "Floor"
floor.location = (0, 0, -0.9)
floor.data.materials.append(mat_floor)

# ── Lighting ──────────────────────────────────────────────────────────────────
bpy.ops.object.light_add(type='AREA', location=(2, -2, 3))
key = bpy.context.active_object
key.data.energy = 300
key.data.size = 2
key.rotation_euler = (math.radians(45), 0, math.radians(30))

bpy.ops.object.light_add(type='AREA', location=(-1, 1, 2))
fill = bpy.context.active_object
fill.data.energy = 80
fill.data.color = (0.6, 0.7, 1.0)

# ── Camera ────────────────────────────────────────────────────────────────────
bpy.ops.object.camera_add(location=(1.6, -2.2, 0.8))
cam = bpy.context.active_object
cam.rotation_euler = (math.radians(80), 0, math.radians(36))
bpy.context.scene.camera = cam
cam.data.lens = 50

# ── World (dark background) ────────────────────────────────────────────────────
bpy.context.scene.world.use_nodes = True
bg = bpy.context.scene.world.node_tree.nodes["Background"]
bg.inputs["Color"].default_value    = (0.01, 0.01, 0.02, 1.0)
bg.inputs["Strength"].default_value = 0.0

# ── Render settings ───────────────────────────────────────────────────────────
scene = bpy.context.scene
scene.render.engine             = 'CYCLES'
scene.cycles.samples            = 128
scene.render.resolution_x       = RESOLUTION[0]
scene.render.resolution_y       = RESOLUTION[1]
scene.render.filepath           = OUTPUT
scene.render.image_settings.file_format = 'PNG'

bpy.ops.render.render(write_still=True)
print("Rendered:", OUTPUT)
