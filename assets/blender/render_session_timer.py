"""
Defang — Session Timer illustration
Paste into Blender's Scripting workspace and run.
Renders a glowing circular countdown arc with a small HUD-style readout.

Output: //renders/session_timer.png
Tested on Blender 4.x
"""

import bpy
import math

OUTPUT = "//renders/session_timer.png"
RESOLUTION = (800, 800)

# ── Reset ─────────────────────────────────────────────────────────────────────
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete()
for mat in bpy.data.materials:
    bpy.data.materials.remove(mat)

# ── Helpers ───────────────────────────────────────────────────────────────────
def emission_mat(name, color, strength=3.0):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    nodes.clear()
    out = nodes.new("ShaderNodeOutputMaterial")
    em  = nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value    = (*color, 1.0)
    em.inputs["Strength"].default_value = strength
    mat.node_tree.links.new(em.outputs[0], out.inputs[0])
    return mat

def dark_mat(name, roughness=0.3, metallic=0.6):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    nodes.clear()
    out  = nodes.new("ShaderNodeOutputMaterial")
    bsdf = nodes.new("ShaderNodeBsdfPrincipled")
    bsdf.inputs["Base Color"].default_value = (0.03, 0.03, 0.05, 1.0)
    bsdf.inputs["Metallic"].default_value   = metallic
    bsdf.inputs["Roughness"].default_value  = roughness
    mat.node_tree.links.new(bsdf.outputs[0], out.inputs[0])
    return mat

mat_dim   = emission_mat("ArcDim",  (0.1, 0.1, 0.12), strength=0.4)
mat_warm  = emission_mat("ArcWarm", (0.69, 0.49, 0.29), strength=5.0)
mat_base  = dark_mat("Base")
mat_floor = dark_mat("Floor", roughness=0.05, metallic=0.9)

# ── Full circle (dim track) ───────────────────────────────────────────────────
SEGMENTS = 64
RADIUS   = 1.0
THICKNESS = 0.06
DEPTH    = 0.04

def make_arc(name, start_angle, end_angle, segments, mat):
    verts, faces = [], []
    step = (end_angle - start_angle) / segments
    for i in range(segments + 1):
        a = start_angle + i * step
        r_in  = RADIUS - THICKNESS / 2
        r_out = RADIUS + THICKNESS / 2
        verts += [
            (math.cos(a) * r_in,  math.sin(a) * r_in,  0),
            (math.cos(a) * r_out, math.sin(a) * r_out, 0),
            (math.cos(a) * r_in,  math.sin(a) * r_in,  DEPTH),
            (math.cos(a) * r_out, math.sin(a) * r_out, DEPTH),
        ]
    for i in range(segments):
        b = i * 4
        faces += [
            (b,   b+1, b+5, b+4),  # front
            (b+2, b+3, b+7, b+6),  # back
            (b,   b+2, b+6, b+4),  # inner
            (b+1, b+3, b+7, b+5),  # outer
        ]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    obj  = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(mat)
    return obj

# Full dim circle
make_arc("TrackDim", 0, math.tau, SEGMENTS, mat_dim)

# Active arc — ~70% remaining (roughly 10 of 15 minutes left)
fill_angle = math.tau * 0.70
make_arc("TrackActive", math.pi / 2, math.pi / 2 + fill_angle, int(SEGMENTS * 0.70), mat_warm)

# ── Centre disc ───────────────────────────────────────────────────────────────
bpy.ops.mesh.primitive_cylinder_add(radius=0.78, depth=0.02, vertices=64, location=(0, 0, 0))
disc = bpy.context.active_object
disc.data.materials.append(mat_base)

# ── Floor ─────────────────────────────────────────────────────────────────────
bpy.ops.mesh.primitive_plane_add(size=8, location=(0, 0, -0.1))
bpy.context.active_object.data.materials.append(mat_floor)

# ── Text in centre ────────────────────────────────────────────────────────────
bpy.ops.object.text_add(location=(-0.22, -0.08, 0.03))
txt = bpy.context.active_object
txt.data.body = "10:00"
txt.data.size = 0.28
txt.data.align_x = 'LEFT'
txt.data.materials.append(emission_mat("TextMat", (0.9, 0.85, 0.75), strength=2.0))

# ── Lighting ──────────────────────────────────────────────────────────────────
bpy.ops.object.light_add(type='AREA', location=(0, 0, 4))
top = bpy.context.active_object
top.data.energy = 200
top.data.size   = 3

bpy.ops.object.light_add(type='POINT', location=(2, -1.5, 1.5))
side = bpy.context.active_object
side.data.energy = 60
side.data.color  = (0.8, 0.6, 0.4)

# ── Camera ────────────────────────────────────────────────────────────────────
bpy.ops.object.camera_add(location=(0, 0, 4.5))
cam = bpy.context.active_object
cam.rotation_euler = (0, 0, 0)
cam.data.type = 'ORTHO'
cam.data.ortho_scale = 2.8
bpy.context.scene.camera = cam

# ── World ─────────────────────────────────────────────────────────────────────
bpy.context.scene.world.use_nodes = True
bg = bpy.context.scene.world.node_tree.nodes["Background"]
bg.inputs["Color"].default_value    = (0.01, 0.01, 0.02, 1.0)
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
