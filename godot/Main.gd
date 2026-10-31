extends Node3D

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
var gray_material: StandardMaterial3D
var flame_material: StandardMaterial3D

func _ready() -> void:
	randomize()
	get_viewport().physics_object_picking = true
	_setup_materials()
	_build_world()
	_build_tracks()
	_build_weapons()
	_build_die()
	_build_ui()
	_refresh_board()

func _setup_materials() -> void:
	blue_material = _material(Color("#2589e8"), 0.28, 0.3)
	red_material = _material(Color("#e34d3f"), 0.24, 0.32)
	bullet_material = _material(Color("#b9c5c8"), 0.72, 0.2)
	gray_material = _material(Color("#777d7c"), 0.12, 0.82)
	flame_material = _material(Color("#ff7b16"), 0.0, 0.3)
	flame_material.emission_enabled = true
	flame_material.emission = Color("#ff4b08")
	flame_material.emission_energy_multiplier = 2.4

func _material(color: Color, metallic: float, roughness: float) -> StandardMaterial3D:
	var mat := StandardMaterial3D.new()
	mat.albedo_color = color
	mat.metallic = metallic
	mat.roughness = roughness
	return mat

func _build_world() -> void:
	var env := WorldEnvironment.new()
	var environment := Environment.new()
	environment.background_mode = Environment.BG_COLOR
	environment.background_color = Color("#a9c5d2")
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color("#c5d8e7")
	environment.ambient_light_energy = 0.85
	environment.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.environment = environment
	add_child(env)

	var ground := MeshInstance3D.new()
	var ground_mesh := PlaneMesh.new()
	ground_mesh.size = Vector2(27.0, 20.0)
	ground.mesh = ground_mesh
	ground.position.y = -0.22
	ground.material_override = _material(Color("#557d42"), 0.0, 1.0)
	add_child(ground)

	var arena := MeshInstance3D.new()
	var arena_mesh := BoxMesh.new()
	arena_mesh.size = Vector3(17.0, 0.2, 12.5)
	arena.mesh = arena_mesh
	arena.position.y = -0.08
	arena.material_override = _material(Color("#b1b98b"), 0.0, 0.92)
	add_child(arena)

	# Decorative runway edges and lane markings.
	for side_x in [-8.25, 8.25]:
		var edge := MeshInstance3D.new()
		var edge_mesh := BoxMesh.new()
		edge_mesh.size = Vector3(0.12, 0.035, 11.7)
		edge.mesh = edge_mesh
		edge.position = Vector3(side_x, 0.035, 0)
		edge.material_override = _material(Color("#e1d6a8"), 0.0, 0.8)
		add_child(edge)
	for z in ROW_Z:
		var divider := MeshInstance3D.new()
		var divider_mesh := BoxMesh.new()
		divider_mesh.size = Vector3(12.0, 0.025, 0.035)
		divider.mesh = divider_mesh
		divider.position = Vector3(0, 0.045, z)
		divider.material_override = _material(Color("#d8c99a"), 0.0, 0.9)
		add_child(divider)

	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-48, -32, 0)
	sun.light_energy = 1.55
	sun.shadow_enabled = true
	add_child(sun)

	var fill := OmniLight3D.new()
	fill.position = Vector3(0, 7, 2)
	fill.light_color = Color("#d8eaff")
	fill.light_energy = 0.45
	fill.omni_range = 24
	add_child(fill)

	var camera := Camera3D.new()
	camera.position = Vector3(0, 15.5, 18.5)
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 18.0
	camera.near = 0.1
	camera.far = 100.0
	camera.look_at(Vector3(0.0, 0.0, 0.0), Vector3.UP)
	camera.make_current()
	add_child(camera)
	game_camera = camera

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
	var casing := MeshInstance3D.new()
	var cylinder := CylinderMesh.new()
	cylinder.top_radius = 0.075
	cylinder.bottom_radius = 0.095
	cylinder.height = 0.29
	casing.mesh = cylinder
	casing.rotation_degrees.z = 90
	casing.material_override = bullet_material
	root.add_child(casing)
	var nose := MeshInstance3D.new()
	var cone := CylinderMesh.new()
	cone.top_radius = 0.0
	cone.bottom_radius = 0.075
	cone.height = 0.16
	nose.mesh = cone
	nose.rotation_degrees.z = -90
	nose.position.x = 0.21
	nose.material_override = _material(Color("#d8dedb"), 0.82, 0.16)
	root.add_child(nose)
	var base := MeshInstance3D.new()
	var base_mesh := CylinderMesh.new()
	base_mesh.top_radius = 0.10
	base_mesh.bottom_radius = 0.10
	base_mesh.height = 0.045
	base.mesh = base_mesh
	base.rotation_degrees.z = 90
	base.position.x = -0.16
	base.material_override = _material(Color("#7e8988"), 0.75, 0.24)
	root.add_child(base)
	return root

func _build_weapons() -> void:
	for lane in range(3):
		_make_weapon(0, lane, -7.35)
		_make_weapon(1, lane, 7.35)

func _make_weapon(side: int, lane: int, x: float) -> void:
	var root := Node3D.new()
	root.position = Vector3(x, 0.16, ROW_Z[lane])
	root.rotation_degrees.y = 0 if side == 0 else 180
	add_child(root)
	var team_mat := blue_material if side == 0 else red_material

	if lane == 0:
		# Tank: hull, tracks, road wheels, turret and barrel.
		_add_box(root, Vector3(0, 0.28, 0), Vector3(1.5, 0.38, 0.82), team_mat)
		_add_box(root, Vector3(0, 0.51, 0), Vector3(0.83, 0.25, 0.59), _material(Color("#53684a"), 0.32, 0.55))
		_add_box(root, Vector3(0.52, 0.57, 0), Vector3(0.92, 0.105, 0.13), team_mat)
		for wheel_x in [-0.55, -0.19, 0.19, 0.55]:
			_add_cylinder(root, Vector3(wheel_x, 0.16, 0.44), 0.145, 0.12, Color("#26342d"))
			_add_cylinder(root, Vector3(wheel_x, 0.16, -0.44), 0.145, 0.12, Color("#26342d"))
		_add_box(root, Vector3(-0.1, 0.73, 0), Vector3(0.28, 0.12, 0.35), _material(Color("#d4b56a"), 0.35, 0.45))
	elif lane == 1:
		# Fighter aircraft: fuselage, swept wings, tail and cockpit.
		_add_box(root, Vector3(0, 0.48, 0), Vector3(1.72, 0.16, 0.23), team_mat)
		_add_box(root, Vector3(0.0, 0.43, 0), Vector3(0.66, 0.12, 1.5), team_mat)
		_add_box(root, Vector3(-0.62, 0.43, 0), Vector3(0.34, 0.12, 0.66), team_mat)
		_add_box(root, Vector3(-0.22, 0.61, 0), Vector3(0.43, 0.20, 0.20), _material(Color("#8fd0e5"), 0.4, 0.18))
		_add_box(root, Vector3(0.78, 0.45, 0), Vector3(0.18, 0.10, 0.42), _material(Color("#d6d9d6"), 0.45, 0.28))
	else:
		# Field cannon: carriage, shield, barrel and wheels.
		_add_box(root, Vector3(-0.08, 0.29, 0), Vector3(0.92, 0.28, 0.66), team_mat)
		_add_cylinder(root, Vector3(0, 0.48, 0), 0.28, 0.18, Color("#56634b"))
		_add_box(root, Vector3(0.46, 0.61, 0), Vector3(1.08, 0.12, 0.13), team_mat)
		_add_box(root, Vector3(-0.32, 0.48, 0), Vector3(0.12, 0.58, 0.72), _material(Color("#43563e"), 0.28, 0.58))
		for wheel_z in [-0.46, 0.46]:
			_add_cylinder(root, Vector3(-0.25, 0.17, wheel_z), 0.22, 0.13, Color("#26342d"))
			_add_cylinder(root, Vector3(0.25, 0.17, wheel_z), 0.22, 0.13, Color("#26342d"))

	var label := Label3D.new()
	label.text = WEAPON_NAMES[lane]
	label.position = Vector3(0, 1.12, 0)
	label.font_size = 34
	label.outline_size = 8
	label.modulate = Color("#f5f5e8")
	root.add_child(label)

	var fire := Node3D.new()
	fire.name = "DestroyedFire"
	fire.visible = false
	fire.position = Vector3(0, 0.95, 0)
	root.add_child(fire)
	for i in range(4):
		var flame := MeshInstance3D.new()
		var flame_mesh := CylinderMesh.new()
		flame_mesh.top_radius = 0.0
		flame_mesh.bottom_radius = 0.13 + float(i % 2) * 0.06
		flame_mesh.height = 0.58 + float(i % 2) * 0.2
		flame.mesh = flame_mesh
		flame.position = Vector3(-0.28 + float(i) * 0.18, 0.18, 0.0 if i % 2 == 0 else 0.16)
		flame.material_override = flame_material
		fire.add_child(flame)
	var smoke := MeshInstance3D.new()
	var smoke_mesh := SphereMesh.new()
	smoke_mesh.radius = 0.25
	smoke_mesh.height = 0.5
	smoke.mesh = smoke_mesh
	smoke.position = Vector3(0, 0.65, -0.12)
	smoke.material_override = _material(Color("#454746"), 0.0, 1.0)
	fire.add_child(smoke)

	weapon_roots.append({"root": root, "side": side, "lane": lane})
	var area := Area3D.new()
	area.position = Vector3(0, 0.55, 0)
	area.input_ray_pickable = true
	area.collision_layer = 1
	area.collision_mask = 0
	area.set_meta("weapon_side", side)
	area.set_meta("weapon_lane", lane)
	var collision := CollisionShape3D.new()
	var shape := SphereShape3D.new()
	shape.radius = 1.25
	collision.shape = shape
	area.add_child(collision)
	root.add_child(area)
	area.input_event.connect(_on_weapon_area_input.bind(side, lane))
	weapon_areas.append(area)

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

func _build_die() -> void:
	die_root = Node3D.new()
	die_root.position = Vector3(0, 0.72, 0)
	add_child(die_root)
	var cube := MeshInstance3D.new()
	var cube_mesh := BoxMesh.new()
	cube_mesh.size = Vector3(1.05, 0.72, 1.05)
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
		bullet_mesh.height = 0.19
		bullet.mesh = bullet_mesh
		bullet.position = Vector3(p.x, 0.38, p.y)
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
	game_panel.position = Vector2(18, 16)
	game_panel.size = Vector2(370, 220)
	screen.add_child(game_panel)
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 8)
	game_panel.add_child(column)
	ui_label = Label.new()
	ui_label.text = "GENERAL — ساحة ثلاثية الأبعاد"
	ui_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	ui_label.add_theme_font_size_override("font_size", 22)
	column.add_child(ui_label)
	roll_button = Button.new()
	roll_button.text = "ارمِ حجر النرد"
	roll_button.custom_minimum_size = Vector2(0, 58)
	roll_button.pressed.connect(_on_roll_pressed)
	column.add_child(roll_button)
	reset_button = Button.new()
	reset_button.text = "إعادة المباراة"
	reset_button.pressed.connect(_show_mode_menu)
	column.add_child(reset_button)
	game_panel.visible = false

func _start_match(mode: int) -> void:
	game_mode = mode
	mode_panel.visible = false
	game_panel.visible = true
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

func _reset_state() -> void:
	progress = [[0, 0, 0], [0, 0, 0]]
	destroyed = [[false, false, false], [false, false, false]]
	current_player = 0
	die_value = 0
	waiting_for_weapon = false
	game_over = false
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

func _unhandled_input(event: InputEvent) -> void:
	var screen_position := Vector2.ZERO
	var pressed := false
	if event is InputEventScreenTouch and event.pressed:
		screen_position = event.position
		pressed = true
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		screen_position = event.position
		pressed = true
	if pressed:
		_pick_weapon_at_screen_position(screen_position)

func _pick_weapon_at_screen_position(screen_position: Vector2) -> void:
	if game_camera == null:
		return
	var ray_origin := game_camera.project_ray_origin(screen_position)
	var ray_direction := game_camera.project_ray_normal(screen_position)
	var query := PhysicsRayQueryParameters3D.create(ray_origin, ray_origin + ray_direction * 100.0)
	query.collision_mask = 1
	var hit := get_world_3d().direct_space_state.intersect_ray(query)
	if hit.is_empty():
		return
	var collider := hit.get("collider") as Node
	if collider is Area3D and collider.has_meta("weapon_side") and collider.has_meta("weapon_lane"):
		_try_choose_weapon(int(collider.get_meta("weapon_side")), int(collider.get_meta("weapon_lane")))
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

func _on_weapon_pressed(lane: int) -> void:
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
	for item in bullet_nodes:
		var side: int = item["side"]
		var lane: int = item["lane"]
		var step: int = item["step"]
		var mesh_node: Node3D = item["node"]
		var filled: bool = step < progress[side][lane]
		var target_color := (Color("#2589e8") if side == 0 else Color("#e34d3f")) if filled else Color("#aeb7b5")
		for child in mesh_node.get_children():
			if child is MeshInstance3D:
				var mat := child.material_override as StandardMaterial3D
				if mat:
					mat.albedo_color = target_color if filled else Color("#aeb7b5")
					mat.emission_enabled = filled
					mat.emission = target_color * 0.16 if filled else Color.BLACK
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

