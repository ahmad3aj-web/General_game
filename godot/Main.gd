extends Node3D

# The current branch includes a licensed Quaternius tank GLB under assets/models.

const TRACK_X := 6.2
const STEPS := 15
const ROW_Z := [-3.2, 0.0, 3.2]
const WEAPON_NAMES := ["الدبابة", "الطائرة", "المدفع"]

var progress := [[0, 0, 0], [0, 0, 0]]
var destroyed := [[false, false, false], [false, false, false]]
var current_player := 0
var die_value := 0
var waiting_for_weapon := false
var game_over := false
var game_mode := -1 # 0: two players, 1: General AI
var bullet_nodes: Array = []
var weapon_roots: Array = []
var weapon_areas: Array = []
var game_camera: Camera3D
var weapon_buttons: Array = []
var weapon_panel: PanelContainer
var ui_label: Label
var roll_button: Button
var reset_button: Button
var mode_panel: PanelContainer
var game_panel: PanelContainer
var die_root: Node3D
var die_pips: Array = []
var blue_material: StandardMaterial3D
var red_material: StandardMaterial3D
var bullet_material: StandardMaterial3D
var bullet_blue_material: StandardMaterial3D
var bullet_red_material: StandardMaterial3D
var art_texture_cache: Dictionary = {}
var gray_material: StandardMaterial3D
var flame_material: StandardMaterial3D

var showcase_root: Node3D
var blue_character: Node3D
var red_character: Node3D
var general_character: Node3D
var blue_flag: Node3D
var red_flag: Node3D

func _ready() -> void:
	randomize()
	get_viewport().physics_object_picking = true
	_setup_materials()
	_build_world()
	_build_tracks()
	_build_weapons()
	_build_die()
	_build_ui()
	# 2D battlefield mode: no 3D character showcase.
	_refresh_board()

func _setup_materials() -> void:
	blue_material = _material(Color("#087cf0"), 0.18, 0.24)
	red_material = _material(Color("#f02f3d"), 0.18, 0.24)
	bullet_material = _art_material(3, Color("#aeb7b5"))
	bullet_blue_material = _art_material(3, Color("#4d9be7"))
	bullet_red_material = _art_material(3, Color("#e45b5b"))
	gray_material = _material(Color("#777d7c"), 0.12, 0.82)
	flame_material = _material(Color("#ff7b16"), 0.0, 0.3)
	flame_material.emission_enabled = true
	flame_material.emission = Color("#ff4b08")
	flame_material.emission_energy_multiplier = 2.4

func _material(color: Color, metallic: float, roughness: float) -> StandardMaterial3D:
	var mat := StandardMaterial3D.new()
	mat.albedo_color = color
	mat.metallic = 0.0
	mat.roughness = 1.0
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mat.disable_receive_shadows = true
	return mat

func _build_world() -> void:
	# Flat, orthographic battlefield: all artwork sits on the XZ plane.
	var env := WorldEnvironment.new()
	var environment := Environment.new()
	environment.background_mode = Environment.BG_COLOR
	environment.background_color = Color("#8eae78")
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color.WHITE
	environment.ambient_light_energy = 1.0
	env.environment = environment
	add_child(env)

	var ground := MeshInstance3D.new()
	var ground_mesh := PlaneMesh.new()
	ground_mesh.size = Vector2(29.0, 23.0)
	ground.mesh = ground_mesh
	ground.position.y = -0.30
	ground.material_override = _material(Color("#6f9b59"), 0.0, 1.0)
	add_child(ground)

	# Main arena and six clean, clearly separated progress lanes.
	_add_box(self, Vector3(0, -0.12, 0), Vector3(18.0, 0.16, 13.0), _material(Color("#c7ad7a"), 0.0, 1.0))
	_add_box(self, Vector3(0, -0.025, 0), Vector3(17.55, 0.035, 12.55), _material(Color("#d7c08f"), 0.0, 1.0))
	for side_x in [-8.55, 8.55]:
		_add_box(self, Vector3(side_x, 0.02, 0), Vector3(0.10, 0.035, 12.2), _material(Color("#5d704c"), 0.0, 1.0))
	for lane in range(3):
		var z: float = ROW_Z[lane]
		for side in range(2):
			var lane_z: float = z + (0.38 if side == 0 else -0.38)
			_add_box(self, Vector3(0, 0.025, lane_z), Vector3(12.8, 0.035, 0.72), _material(Color("#ad956b"), 0.0, 1.0))
			_add_box(self, Vector3(0, 0.047, lane_z), Vector3(12.4, 0.018, 0.035), _material(Color("#d9c493"), 0.0, 1.0))
		_add_box(self, Vector3(0, 0.045, z), Vector3(13.0, 0.025, 0.045), _material(Color("#f0dfb7"), 0.0, 1.0))

	# Small flat field markings around the board; no raised 3D props.
	for i in range(30):
		var gx := -12.0 + float((i * 37) % 240) / 10.0
		var gz := -9.0 + float((i * 23) % 180) / 10.0
		if absf(gx) < 9.2 and absf(gz) < 6.8:
			continue
		_add_sphere(self, Vector3(gx, -0.20, gz), Vector3(0.16, 0.025, 0.10), _material(Color("#83aa64") if i % 2 == 0 else Color("#5f8d4d"), 0.0, 1.0))
	for x in [-8.9, 8.9]:
		for z in [-5.4, -2.0, 2.0, 5.4]:
			_add_box(self, Vector3(x, 0.025, z), Vector3(0.38, 0.06, 0.38), _material(Color("#96734d"), 0.0, 1.0))
			_add_box(self, Vector3(x, 0.060, z), Vector3(0.28, 0.025, 0.28), _material(Color("#c5a16d"), 0.0, 1.0))

	var camera := Camera3D.new()
	camera.position = Vector3(0, 23.0, 0.01)
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 19.5
	camera.near = 0.1
	camera.far = 100.0
	add_child(camera)
	camera.look_at(Vector3.ZERO, Vector3.FORWARD)
	camera.make_current()
	game_camera = camera

func _process(delta: float) -> void:
	# Gentle motion keeps the battlefield alive without obscuring the board.
	if blue_flag and is_instance_valid(blue_flag):
		blue_flag.rotation.z = sin(Time.get_ticks_msec() * 0.0018) * 0.035
	if red_flag and is_instance_valid(red_flag):
		red_flag.rotation.z = sin(Time.get_ticks_msec() * 0.0018 + 1.2) * 0.035
	if die_root and is_instance_valid(die_root) and not waiting_for_weapon:
		die_root.position.y = 0.72 + sin(Time.get_ticks_msec() * 0.0022) * 0.035

func _build_tracks() -> void:
	for side in range(2):
		for lane in range(3):
			var z: float = ROW_Z[lane] + (0.38 if side == 0 else -0.38)
			for step in range(STEPS):
				var bullet := _make_bullet()
				var x := -TRACK_X + (step * (TRACK_X * 2.0 / float(STEPS - 1)))
				if side == 1:
					x = TRACK_X - (step * (TRACK_X * 2.0 / float(STEPS - 1)))
				bullet.position = Vector3(x, 0.22, z)
				add_child(bullet)
				bullet_nodes.append({"node": bullet, "side": side, "lane": lane, "step": step})

func _make_bullet() -> Node3D:
	var root := Node3D.new()
	var visual := MeshInstance3D.new()
	var mesh := QuadMesh.new()
	mesh.size = Vector2(0.62, 0.30)
	visual.mesh = mesh
	visual.rotation_degrees.x = -90
	visual.material_override = bullet_material
	visual.set_meta("original_material", bullet_material)
	root.add_child(visual)
	return root

func _art_material(kind: int, tint: Color) -> StandardMaterial3D:
	var key := "%d_%s" % [kind, tint.to_html(false)]
	if art_texture_cache.has(key):
		var cached := StandardMaterial3D.new()
		cached.albedo_texture = art_texture_cache[key]
		cached.albedo_color = Color.WHITE
		cached.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		cached.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		cached.cull_mode = BaseMaterial3D.CULL_DISABLED
		return cached
	var sub := SubViewport.new()
	sub.size = Vector2i(256, 160)
	sub.transparent_bg = true
	sub.disable_3d = true
	sub.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	var painter := Node2D.new()
	painter.set_script(load("res://ArtPainter.gd"))
	painter.set("kind", kind)
	painter.set("team_color", tint)
	sub.add_child(painter)
	add_child(sub)
	art_texture_cache[key] = sub.get_texture()
	var mat := StandardMaterial3D.new()
	mat.albedo_texture = sub.get_texture()
	mat.albedo_color = Color.WHITE
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.cull_mode = BaseMaterial3D.CULL_DISABLED
	return mat

func _build_weapons() -> void:
	for lane in range(3):
		_make_weapon(0, lane, -7.35)
		_make_weapon(1, lane, 7.35)

func _make_weapon(side: int, lane: int, x: float) -> void:
	var root := Node3D.new()
	root.position = Vector3(x, 0.16, ROW_Z[lane])
	root.rotation_degrees.y = 0 if side == 0 else 180
	add_child(root)
	var team_color := Color("#66865a") if side == 0 else Color("#6689b5")
	var art := _art_material(lane, team_color)
	var visual := MeshInstance3D.new()
	var quad := QuadMesh.new()
	quad.size = Vector2(2.55, 1.60)
	visual.mesh = quad
	visual.rotation_degrees.x = -90
	visual.material_override = art
	visual.set_meta("original_material", art)
	root.add_child(visual)

	var label := Label3D.new()
	label.text = WEAPON_NAMES[lane]
	label.position = Vector3(0, 0.48, -1.02)
	label.font_size = 30
	label.outline_size = 7
	label.modulate = Color("#fff7df")
	root.add_child(label)

	var fire := Node3D.new()
	fire.name = "DestroyedFire"
	fire.visible = false
	fire.position = Vector3(0, 0.18, 0)
	var explosion := MeshInstance3D.new()
	var explosion_quad := QuadMesh.new()
	explosion_quad.size = Vector2(1.8, 1.5)
	explosion.mesh = explosion_quad
	explosion.rotation_degrees.x = -90
	var explosion_mat := _art_material(4, Color.WHITE)
	explosion.material_override = explosion_mat
	explosion.set_meta("original_material", explosion_mat)
	fire.add_child(explosion)
	root.add_child(fire)

	weapon_roots.append({"root": root, "side": side, "lane": lane})
	var area := Area3D.new()
	area.position = Vector3(0, 0.72, 0)
	area.input_ray_pickable = true
	area.monitoring = true
	area.monitorable = true
	area.collision_layer = 1
	area.collision_mask = 0
	area.set_meta("weapon_side", side)
	area.set_meta("weapon_lane", lane)
	var collision := CollisionShape3D.new()
	var shape := SphereShape3D.new()
	shape.radius = 2.25
	collision.shape = shape
	area.add_child(collision)
	root.add_child(area)
	area.input_event.connect(_on_weapon_area_input.bind(side, lane))
	weapon_areas.append(area)

func _add_sphere(parent: Node3D, pos: Vector3, scale_value: Vector3, mat: Material) -> void:
	var instance := MeshInstance3D.new()
	var mesh := SphereMesh.new()
	mesh.radius = 0.5
	mesh.height = 1.0
	instance.mesh = mesh
	instance.position = pos
	instance.scale = scale_value
	instance.material_override = mat
	instance.set_meta("original_material", mat)
	parent.add_child(instance)

func _add_cylinder_axis(parent: Node3D, pos: Vector3, radius: float, height: float, mat: Material, axis: Vector3) -> void:
	var instance := MeshInstance3D.new()
	var mesh := CylinderMesh.new()
	mesh.top_radius = radius
	mesh.bottom_radius = radius
	mesh.height = height
	instance.mesh = mesh
	instance.position = pos
	instance.quaternion = Quaternion(Vector3.UP, axis.normalized())
	instance.material_override = mat
	instance.set_meta("original_material", mat)
	parent.add_child(instance)

func _add_box(parent: Node3D, pos: Vector3, size: Vector3, mat: Material) -> void:
	var mesh_instance := MeshInstance3D.new()
	var mesh := BoxMesh.new()
	mesh.size = size
	mesh_instance.mesh = mesh
	mesh_instance.position = pos
	mesh_instance.material_override = mat
	mesh_instance.set_meta("original_material", mat)
	parent.add_child(mesh_instance)

func _add_cylinder(parent: Node3D, pos: Vector3, radius: float, height: float, color: Color) -> void:
	var mesh_instance := MeshInstance3D.new()
	var mesh := CylinderMesh.new()
	mesh.top_radius = radius
	mesh.bottom_radius = radius
	mesh.height = height
	mesh_instance.mesh = mesh
	mesh_instance.position = pos
	mesh_instance.material_override = _material(color, 0.45, 0.55)
	mesh_instance.set_meta("original_material", mesh_instance.material_override)
	parent.add_child(mesh_instance)


func _build_showcase() -> void:
	showcase_root = Node3D.new()
	showcase_root.name = "OpeningShowcase"
	add_child(showcase_root)
	blue_character = _make_soldier(Color("#276fc1"), Color("#405c42"), false)
	blue_character.position = Vector3(-3.8, 0.0, -5.0)
	showcase_root.add_child(blue_character)
	red_character = _make_soldier(Color("#b83b3e"), Color("#6b5540"), false)
	red_character.position = Vector3(3.8, 0.0, -5.0)
	red_character.rotation_degrees.y = 180
	showcase_root.add_child(red_character)
	general_character = _make_soldier(Color("#283e58"), Color("#d5ae57"), true)
	general_character.position = Vector3(3.8, 0.0, -5.0)
	general_character.rotation_degrees.y = 180
	showcase_root.add_child(general_character)
	general_character.visible = false
	blue_flag = _make_flag(Color("#087cf0"), Vector3(-3.8, 0.0, -6.5), "اللاعب الأزرق")
	red_flag = _make_flag(Color("#f02f3d"), Vector3(3.8, 0.0, -6.5), "الخصم")
	showcase_root.add_child(blue_flag)
	showcase_root.add_child(red_flag)
	var title := Label3D.new()
	title.text = "GENERAL"
	title.position = Vector3(0, 3.3, -5.8)
	title.font_size = 68
	title.outline_size = 12
	title.modulate = Color("#fff4d4")
	showcase_root.add_child(title)
	var subtitle := Label3D.new()
	subtitle.text = "قوّاتك جاهزة للمعركة"
	subtitle.position = Vector3(0, 2.65, -5.8)
	subtitle.font_size = 28
	subtitle.outline_size = 7
	subtitle.modulate = Color("#253a2d")
	showcase_root.add_child(subtitle)

func _make_soldier(primary: Color, secondary: Color, is_general: bool) -> Node3D:
	var root := Node3D.new()
	var uniform := _material(primary, 0.08, 0.72)
	var armor := _material(secondary, 0.16, 0.66)
	var skin := _material(Color("#c98e68"), 0.0, 0.86)
	var boots := _material(Color("#252d2b"), 0.10, 0.84)
	var metal := _material(Color("#c8b77b") if is_general else Color("#8c9d83"), 0.58, 0.35)
	# Boots, articulated-looking legs and knee pads.
	for z in [-0.15, 0.15]:
		_add_box(root, Vector3(0.08, 0.13, z), Vector3(0.34, 0.22, 0.29), boots)
		_add_cylinder_axis(root, Vector3(0.0, 0.48, z), 0.105, 0.58, uniform, Vector3.UP)
		_add_sphere(root, Vector3(0.0, 0.55, z), Vector3(0.25, 0.19, 0.25), armor)
	# Torso, vest, belt, radio and shoulder armor.
	_add_box(root, Vector3(0, 1.05, 0), Vector3(0.62, 0.68, 0.42), uniform)
	_add_box(root, Vector3(0.17, 1.08, 0), Vector3(0.25, 0.55, 0.45), armor)
	_add_box(root, Vector3(0, 0.76, 0), Vector3(0.66, 0.10, 0.45), metal)
	_add_box(root, Vector3(-0.34, 1.13, -0.12), Vector3(0.20, 0.44, 0.27), armor)
	for z in [-0.34, 0.34]:
		_add_sphere(root, Vector3(0, 1.30, z), Vector3(0.28, 0.25, 0.28), armor)
		_add_cylinder_axis(root, Vector3(0.12, 1.10, z * 1.22), 0.095, 0.42, uniform, Vector3(0.4, -1.0, 0.0))
		_add_sphere(root, Vector3(0.28, 0.91, z * 1.22), Vector3(0.17, 0.15, 0.17), skin)
	# Neck, face, ears, eyes and helmet.
	_add_cylinder(root, Vector3(0, 1.47, 0), 0.105, 0.20, Color("#c98e68"))
	_add_sphere(root, Vector3(0.04, 1.72, 0), Vector3(0.48, 0.55, 0.43), skin)
	for z in [-0.10, 0.10]:
		_add_sphere(root, Vector3(0.255, 1.79, z), Vector3(0.055, 0.065, 0.045), _material(Color("#202523"), 0.0, 0.35))
	_add_sphere(root, Vector3(0.29, 1.68, 0), Vector3(0.09, 0.12, 0.10), skin)
	_add_cylinder(root, Vector3(0, 1.96, 0), 0.30, 0.09, primary)
	_add_sphere(root, Vector3(0, 2.07, 0), Vector3(0.56, 0.30, 0.50), uniform)
	_add_box(root, Vector3(0.27, 2.06, 0), Vector3(0.04, 0.15, 0.14), metal)
	# Decorative rifle and ammunition pouches.
	_add_cylinder_axis(root, Vector3(0.24, 1.18, 0.28), 0.035, 0.95, boots, Vector3.RIGHT)
	_add_box(root, Vector3(0.0, 1.15, 0.28), Vector3(0.28, 0.12, 0.11), boots)
	for z in [-0.18, 0.18]:
		_add_box(root, Vector3(0.20, 0.89, z), Vector3(0.16, 0.18, 0.15), metal)
	if is_general:
		_add_box(root, Vector3(0.34, 1.33, 0), Vector3(0.08, 0.10, 0.28), metal)
		_add_sphere(root, Vector3(0.35, 1.22, 0), Vector3(0.12, 0.12, 0.12), metal)
	return root

func _make_flag(color: Color, pos: Vector3, player_name: String) -> Node3D:
	var root := Node3D.new()
	root.position = pos
	_add_cylinder(root, Vector3(0, 1.05, 0), 0.035, 2.2, Color("#d8d8c8"))
	_add_box(root, Vector3(0.43, 1.73, 0), Vector3(0.86, 0.50, 0.055), _material(color, 0.05, 0.55))
	_add_sphere(root, Vector3(0.44, 1.74, 0.04), Vector3(0.16, 0.16, 0.07), _material(Color("#f4e6b2"), 0.15, 0.4))
	var label := Label3D.new()
	label.text = player_name
	label.position = Vector3(0.0, 2.42, 0)
	label.font_size = 30
	label.outline_size = 7
	label.modulate = Color("#fff6dc")
	root.add_child(label)
	return root

func _build_die() -> void:
	die_root = Node3D.new()
	die_root.position = Vector3(8.35, 0.72, 5.0)
	add_child(die_root)
	var cube := MeshInstance3D.new()
	var cube_mesh := BoxMesh.new()
	cube_mesh.size = Vector3(1.05, 0.07, 1.05)
	cube.mesh = cube_mesh
	cube.material_override = _material(Color("#e7e5d8"), 0.12, 0.24)
	die_root.add_child(cube)
	_update_die_face(1)

func _update_die_face(count: int) -> void:
	for pip in die_pips:
		pip.queue_free()
	die_pips.clear()
	var positions := [
		[Vector2(0, 0)],
		[Vector2(-0.22, -0.22), Vector2(0.22, 0.22)],
		[Vector2(-0.22, -0.22), Vector2(0, 0), Vector2(0.22, 0.22)],
		[Vector2(-0.22, -0.22), Vector2(0.22, -0.22), Vector2(-0.22, 0.22), Vector2(0.22, 0.22)],
		[Vector2(-0.22, -0.22), Vector2(0.22, -0.22), Vector2(0, 0), Vector2(-0.22, 0.22), Vector2(0.22, 0.22)],
		[Vector2(-0.22, -0.22), Vector2(0.22, -0.22), Vector2(-0.22, 0), Vector2(0.22, 0), Vector2(-0.22, 0.22), Vector2(0.22, 0.22)]
	]
	for p in positions[count - 1]:
		var bullet := MeshInstance3D.new()
		var bullet_mesh := CylinderMesh.new()
		bullet_mesh.top_radius = 0.035
		bullet_mesh.bottom_radius = 0.055
		bullet_mesh.height = 0.035
		bullet.mesh = bullet_mesh
		bullet.position = Vector3(p.x, 0.10, p.y)
		bullet.material_override = _material(Color("#b97828"), 0.65, 0.2)
		die_root.add_child(bullet)
		die_pips.append(bullet)

func _build_ui() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)
	var screen := Control.new()
	screen.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	layer.add_child(screen)

	mode_panel = PanelContainer.new()
	mode_panel.set_anchors_preset(Control.PRESET_CENTER)
	mode_panel.position = Vector2(-250, -155)
	mode_panel.size = Vector2(500, 310)
	screen.add_child(mode_panel)
	var menu := VBoxContainer.new()
	menu.add_theme_constant_override("separation", 18)
	mode_panel.add_child(menu)
	var title := Label.new()
	title.text = "GENERAL"
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_font_size_override("font_size", 42)
	menu.add_child(title)
	var subtitle := Label.new()
	subtitle.text = "اختر طريقة اللعب"
	subtitle.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	subtitle.add_theme_font_size_override("font_size", 24)
	menu.add_child(subtitle)
	var ai_button := Button.new()
	ai_button.text = "العب ضد جنيرال"
	ai_button.custom_minimum_size = Vector2(0, 64)
	ai_button.pressed.connect(_start_match.bind(1))
	menu.add_child(ai_button)
	var local_button := Button.new()
	local_button.text = "لاعبان على نفس الجهاز"
	local_button.custom_minimum_size = Vector2(0, 64)
	local_button.pressed.connect(_start_match.bind(0))
	menu.add_child(local_button)

	game_panel = PanelContainer.new()
	game_panel.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
	game_panel.position = Vector2(-320, -390)
	game_panel.size = Vector2(300, 370)
	screen.add_child(game_panel)
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 8)
	game_panel.add_child(column)
	ui_label = Label.new()
	ui_label.text = "اللاعب الأزرق"
	ui_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	ui_label.add_theme_font_size_override("font_size", 26)
	column.add_child(ui_label)
	roll_button = Button.new()
	roll_button.text = "ارمِ حجر النرد"
	roll_button.custom_minimum_size = Vector2(0, 72)
	roll_button.add_theme_font_size_override("font_size", 26)
	roll_button.pressed.connect(_on_roll_pressed)
	column.add_child(roll_button)
	# A separate, centered weapon chooser stays visible above the battlefield
	# after every roll; it is not clipped by the corner status panel.
	weapon_panel = PanelContainer.new()
	weapon_panel.set_anchors_preset(Control.PRESET_CENTER)
	weapon_panel.position = Vector2(-300, -105)
	weapon_panel.size = Vector2(600, 210)
	weapon_panel.visible = false
	screen.add_child(weapon_panel)
	var chooser_column := VBoxContainer.new()
	chooser_column.add_theme_constant_override("separation", 12)
	weapon_panel.add_child(chooser_column)
	var chooser_title := Label.new()
	chooser_title.text = "اختر سلاحاً لتنفيذ نتيجة النرد"
	chooser_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	chooser_title.add_theme_font_size_override("font_size", 27)
	chooser_column.add_child(chooser_title)
	var chooser_row := HBoxContainer.new()
	chooser_row.add_theme_constant_override("separation", 12)
	chooser_column.add_child(chooser_row)
	for lane in range(3):
		var weapon_button := Button.new()
		weapon_button.text = WEAPON_NAMES[lane]
		weapon_button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		weapon_button.custom_minimum_size = Vector2(170, 82)
		weapon_button.add_theme_font_size_override("font_size", 25)
		weapon_button.visible = true
		weapon_button.pressed.connect(_on_weapon_pressed.bind(lane))
		chooser_row.add_child(weapon_button)
		weapon_buttons.append(weapon_button)
	reset_button = Button.new()
	reset_button.text = "إعادة المباراة"
	reset_button.pressed.connect(_show_mode_menu)
	column.add_child(reset_button)
	game_panel.visible = false

func _start_match(mode: int) -> void:
	game_mode = mode
	mode_panel.visible = false
	game_panel.visible = true
	if showcase_root:
		red_character.visible = mode == 0
		general_character.visible = mode == 1
		red_flag.get_node("Label3D").text = "جنيرال" if mode == 1 else "اللاعب الأحمر"
		var intro := create_tween()
		intro.tween_property(showcase_root, "scale", Vector3(1.04, 1.04, 1.04), 0.35)
		intro.tween_property(showcase_root, "scale", Vector3.ONE, 0.35)
		intro.tween_interval(0.7)
		intro.tween_property(showcase_root, "position:y", -9.0, 0.55)
		intro.tween_callback(func(): showcase_root.visible = false)
	_reset_state()
	# Opening toss: highest of two fair dice starts; ties are rerolled.
	var first := randi_range(1, 6)
	var second := randi_range(1, 6)
	while first == second:
		first = randi_range(1, 6)
		second = randi_range(1, 6)
	current_player = 0 if first > second else 1
	ui_label.text = "قرعة البداية: %d مقابل %d — اللاعب %d يبدأ" % [first, second, current_player + 1]
	_refresh_board()
	if game_mode == 1 and current_player == 1:
		_ai_turn()

func _show_mode_menu() -> void:
	_reset_state()
	game_panel.visible = false
	mode_panel.visible = true
	if showcase_root:
		showcase_root.visible = true
		showcase_root.position = Vector3.ZERO
		showcase_root.scale = Vector3.ONE

func _reset_state() -> void:
	progress = [[0, 0, 0], [0, 0, 0]]
	destroyed = [[false, false, false], [false, false, false]]
	current_player = 0
	die_value = 0
	waiting_for_weapon = false
	game_over = false
	for weapon_button in weapon_buttons:
		weapon_button.visible = true
		weapon_button.disabled = true
	if weapon_panel:
		weapon_panel.visible = false
	roll_button.disabled = false
	ui_label.text = "ابدأ مباراة جديدة"
	_update_die_face(1)
	_refresh_board()

func _on_roll_pressed() -> void:
	if game_over or waiting_for_weapon or game_mode < 0:
		return
	die_value = randi_range(1, 6)
	_update_die_face(die_value)
	var tween := create_tween()
	tween.tween_property(die_root, "rotation", Vector3(deg_to_rad(360), deg_to_rad(360), deg_to_rad(360)), 0.45)
	tween.tween_callback(func(): die_root.rotation = Vector3.ZERO)
	waiting_for_weapon = true
	roll_button.disabled = true
	ui_label.text = "اللاعب %d — اختر سلاحاً (الرمية %d)" % [current_player + 1, die_value]
	var possible := false
	for lane in range(3):
		var can_use: bool = (not destroyed[current_player][lane]) and (progress[current_player][lane] + die_value <= STEPS)
		possible = possible or can_use
	if not possible:
		waiting_for_weapon = false
		ui_label.text = "لا توجد حركة ممكنة — انتهى الدور"
		current_player = 1 - current_player
		roll_button.disabled = false
		_refresh_board()
		if game_mode == 1 and current_player == 1:
			_ai_turn()
	else:
		_refresh_board()

func _input(event: InputEvent) -> void:
	# Read the touch before GUI controls consume it. We only act after a roll,
	# so the roll/reset buttons keep their normal behaviour.
	var screen_position := Vector2.ZERO
	var pressed := false
	if event is InputEventScreenTouch and event.pressed:
		screen_position = event.position
		pressed = true
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		screen_position = event.position
		pressed = true
	if pressed and waiting_for_weapon:
		_pick_weapon_at_screen_position(screen_position)

func _pick_weapon_at_screen_position(screen_position: Vector2) -> void:
	if not waiting_for_weapon or game_over or game_camera == null:
		return

	# Primary Android-safe picking: compare the finger with projected weapon
	# centers, using a generous radius for large touch screens.
	var viewport_size := get_viewport().get_visible_rect().size
	var tap_radius := minf(viewport_size.x, viewport_size.y) * 0.19
	var best_distance := INF
	var best_lane := -1
	for item in weapon_roots:
		var side: int = int(item["side"])
		var lane: int = int(item["lane"])
		if side != current_player:
			continue
		if destroyed[side][lane] or progress[side][lane] + die_value > STEPS:
			continue
		var root: Node3D = item["root"]
		var screen_target := game_camera.unproject_position(root.global_position + Vector3(0, 0.55, 0))
		var distance := screen_position.distance_to(screen_target)
		if distance < best_distance:
			best_distance = distance
			best_lane = lane

	# If perspective/projection differs slightly on a device, resolve the tap
	# against the ground plane and choose the closest lane on the player's half.
	if best_lane < 0 or best_distance > tap_radius:
		var ray_origin := game_camera.project_ray_origin(screen_position)
		var ray_direction := game_camera.project_ray_normal(screen_position)
		if absf(ray_direction.y) > 0.0001:
			var plane_distance := -ray_origin.y / ray_direction.y
			if plane_distance > 0.0:
				var ground_hit := ray_origin + ray_direction * plane_distance
				var nearest_lane := -1
				var nearest_z := INF
				for lane in range(3):
					if destroyed[current_player][lane] or progress[current_player][lane] + die_value > STEPS:
						continue
					var z_distance := absf(ground_hit.z - ROW_Z[lane])
					if z_distance < nearest_z:
						nearest_z = z_distance
						nearest_lane = lane
				if nearest_lane >= 0 and nearest_z < 2.25:
					best_lane = nearest_lane

	if best_lane >= 0 and (best_distance <= tap_radius or best_distance == INF or best_lane >= 0):
		_try_choose_weapon(current_player, best_lane)
		get_viewport().set_input_as_handled()

func _on_weapon_area_input(_camera: Node, event: InputEvent, _position: Vector3, _normal: Vector3, _shape_idx: int, side: int, lane: int) -> void:
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		_try_choose_weapon(side, lane)
	elif event is InputEventScreenTouch and event.pressed:
		_try_choose_weapon(side, lane)

func _try_choose_weapon(side: int, lane: int) -> void:
	if side != current_player or not waiting_for_weapon or game_over:
		return
	if destroyed[side][lane] or progress[side][lane] + die_value > STEPS:
		return
	_on_weapon_pressed(lane)

func _animate_weapon_action(side: int, lane: int) -> void:
	for item in weapon_roots:
		if int(item["side"]) == side and int(item["lane"]) == lane:
			var root: Node3D = item["root"]
			var start_position := root.position
			var direction := 0.42 if side == 0 else -0.42
			var tween := create_tween()
			tween.set_trans(Tween.TRANS_BACK)
			tween.set_ease(Tween.EASE_OUT)
			tween.tween_property(root, "position:x", start_position.x + direction, 0.18)
			tween.tween_property(root, "position:x", start_position.x, 0.22)
			tween.parallel().tween_property(root, "scale", Vector3(1.16, 1.16, 1.16), 0.16)
			tween.tween_property(root, "scale", Vector3.ONE, 0.20)
			break

func _on_weapon_pressed(lane: int) -> void:
	_animate_weapon_action(current_player, lane)
	progress[current_player][lane] += die_value
	if progress[current_player][lane] == STEPS:
		destroyed[1 - current_player][lane] = true
	waiting_for_weapon = false
	var lost_count := 0
	for dead in destroyed[1 - current_player]:
		if dead:
			lost_count += 1
	if lost_count >= 2:
		game_over = true
		ui_label.text = "انتهت المباراة! اللاعب %d فاز" % [current_player + 1]
	else:
		var previous_player := current_player
		current_player = 1 - current_player
		ui_label.text = "اللاعب %d أنهى حركته — دور اللاعب %d" % [previous_player + 1, current_player + 1]
	roll_button.disabled = game_over
	_refresh_board()
	if game_mode == 1 and current_player == 1 and not game_over:
		_ai_turn()

func _ai_turn() -> void:
	if game_over or game_mode != 1:
		return
	roll_button.disabled = true
	ui_label.text = "جنيرال يفكر..."
	await get_tree().create_timer(0.75).timeout
	if game_over or current_player != 1:
		return
	die_value = randi_range(1, 6)
	_update_die_face(die_value)
	var candidates: Array = []
	for lane in range(3):
		if not destroyed[1][lane] and progress[1][lane] + die_value <= STEPS:
			candidates.append(lane)
	if candidates.is_empty():
		ui_label.text = "جنيرال لم يجد حركة — دورك"
		current_player = 0
		roll_button.disabled = false
		_refresh_board()
		return
	var chosen: int = candidates[0]
	for lane in candidates:
		if progress[1][lane] > progress[1][chosen]:
			chosen = lane
	ui_label.text = "جنيرال رمى %d واختار %s" % [die_value, WEAPON_NAMES[chosen]]
	await get_tree().create_timer(0.65).timeout
	_on_weapon_pressed(chosen)

func _refresh_board() -> void:
	if die_root:
		die_root.position = Vector3(-8.35 if current_player == 0 else 8.35, 0.72, 5.0)
	if ui_label and game_mode >= 0 and not game_over and not waiting_for_weapon:
		ui_label.text = ("اللاعب الأزرق" if current_player == 0 else ("جنيرال الأحمر" if game_mode == 1 else "اللاعب الأحمر"))
	if weapon_panel:
		weapon_panel.visible = waiting_for_weapon and not game_over
	for lane in range(3):
		if lane < weapon_buttons.size():
			var can_use: bool = waiting_for_weapon and not destroyed[current_player][lane] and progress[current_player][lane] + die_value <= STEPS
			weapon_buttons[lane].visible = true
			weapon_buttons[lane].disabled = not can_use
	if game_panel and game_mode >= 0:
		if current_player == 0:
			game_panel.set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
			game_panel.position = Vector2(18, -390)
		else:
			game_panel.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
			game_panel.position = Vector2(-320, -390)
	for item in bullet_nodes:
		var side: int = item["side"]
		var lane: int = item["lane"]
		var step: int = item["step"]
		var mesh_node: Node3D = item["node"]
		var filled: bool = step < progress[side][lane]
		var target_color := (Color("#087cf0") if side == 0 else Color("#f02f3d")) if filled else Color("#aeb7b5")
		for child in mesh_node.get_children():
			if child is MeshInstance3D:
				child.material_override = (bullet_blue_material if side == 0 else bullet_red_material) if filled else bullet_material
	for item in weapon_roots:
		var side: int = item["side"]
		var lane: int = item["lane"]
		var root: Node3D = item["root"]
		var is_dead: bool = destroyed[side][lane]
		var available: bool = side == current_player and waiting_for_weapon and not is_dead and progress[side][lane] + die_value <= STEPS
		var fire := root.get_node("DestroyedFire") as Node3D
		fire.visible = is_dead
		for child in root.get_children():
			if child is MeshInstance3D and child.has_meta("original_material"):
				child.material_override = child.get_meta("original_material") if not is_dead and (not waiting_for_weapon or available) else gray_material
		var area := root.get_node_or_null("Area3D") as Area3D
		if area:
			area.input_ray_pickable = true
		if available:
			root.scale = Vector3(1.12, 1.12, 1.12)
		else:
			root.scale = Vector3.ONE

