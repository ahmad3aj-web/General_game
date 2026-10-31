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
var bullet_nodes: Array = []
var ui_label: Label
var roll_button: Button
var weapon_buttons: Array = []
var reset_button: Button

var blue_material: StandardMaterial3D
var red_material: StandardMaterial3D
var bullet_material: StandardMaterial3D

func _ready() -> void:
	_setup_materials()
	_build_world()
	_build_tracks()
	_build_weapons()
	_build_ui()
	_refresh_board()

func _setup_materials() -> void:
	blue_material = _material(Color("#238be6"), 0.25, 0.35)
	red_material = _material(Color("#e34e43"), 0.25, 0.35)
	bullet_material = _material(Color("#b9c0c1"), 0.72, 0.18)

func _material(color: Color, metallic: float, roughness: float) -> StandardMaterial3D:
	var mat := StandardMaterial3D.new()
	mat.albedo_color = color
	mat.metallic = metallic
	mat.roughness = roughness
	return mat

func _build_world() -> void:
	var env := WorldEnvironment.new()
	var environment := Environment.new()
	environment.background_mode = Environment.BG_SKY
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color("#b8c9dc")
	environment.ambient_light_energy = 0.65
	env.environment = environment
	add_child(env)

	var ground := MeshInstance3D.new()
	var ground_mesh := PlaneMesh.new()
	ground_mesh.size = Vector2(23.0, 17.0)
	ground.mesh = ground_mesh
	ground.position.y = -0.16
	ground.material_override = _material(Color("#729d50"), 0.0, 1.0)
	add_child(ground)

	var arena := MeshInstance3D.new()
	var arena_mesh := BoxMesh.new()
	arena_mesh.size = Vector3(15.2, 0.18, 11.2)
	arena.mesh = arena_mesh
	arena.position.y = -0.04
	arena.material_override = _material(Color("#9baf72"), 0.0, 0.95)
	add_child(arena)

	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-52, -28, 0)
	sun.light_energy = 1.35
	sun.shadow_enabled = true
	add_child(sun)

	var camera := Camera3D.new()
	camera.position = Vector3(0, 14.5, 18.5)
	camera.rotation_degrees = Vector3(-37, 0, 0)
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 22.5
	camera.current = true
	add_child(camera)

func _build_tracks() -> void:
	for side in range(2):
		for lane in range(3):
			var z := ROW_Z[lane] + (0.34 if side == 0 else -0.34)
			for step in range(STEPS):
				var bullet := _make_bullet()
				var x := -TRACK_X + (step * (TRACK_X * 2.0 / float(STEPS - 1)))
				if side == 1:
					x = TRACK_X - (step * (TRACK_X * 2.0 / float(STEPS - 1)))
				bullet.position = Vector3(x, 0.18, z)
				add_child(bullet)
				bullet_nodes.append({"node": bullet, "side": side, "lane": lane, "step": step})

func _make_bullet() -> Node3D:
	var root := Node3D.new()
	var body := MeshInstance3D.new()
	var capsule := CapsuleMesh.new()
	capsule.radius = 0.105
	capsule.height = 0.42
	body.mesh = capsule
	body.rotation_degrees.z = 90
	body.material_override = bullet_material
	root.add_child(body)

	var tip := MeshInstance3D.new()
	var tip_mesh := SphereMesh.new()
	tip_mesh.radius = 0.105
	tip_mesh.height = 0.21
	tip.mesh = tip_mesh
	tip.position.x = 0.19
	tip.material_override = bullet_material
	root.add_child(tip)
	return root

func _build_weapons() -> void:
	for lane in range(3):
		_make_weapon(0, lane, -7.25)
		_make_weapon(1, lane, 7.25)

func _make_weapon(side: int, lane: int, x: float) -> void:
	var root := Node3D.new()
	root.position = Vector3(x, 0.18, ROW_Z[lane])
	root.rotation_degrees.y = 0 if side == 0 else 180
	add_child(root)
	var team_mat := blue_material if side == 0 else red_material

	if lane == 0:
		_add_box(root, Vector3(0, 0.27, 0), Vector3(1.35, 0.38, 0.76), team_mat)
		_add_box(root, Vector3(0.0, 0.54, 0), Vector3(0.68, 0.22, 0.52), _material(Color("#52634a"), 0.35, 0.5))
		_add_box(root, Vector3(0.53, 0.57, 0), Vector3(0.85, 0.11, 0.12), team_mat)
		for wheel_x in [-0.48, -0.16, 0.16, 0.48]:
			_add_cylinder(root, Vector3(wheel_x, 0.16, 0.42), 0.14, 0.12, Color("#303b32"))
			_add_cylinder(root, Vector3(wheel_x, 0.16, -0.42), 0.14, 0.12, Color("#303b32"))
	elif lane == 1:
		_add_box(root, Vector3(0, 0.42, 0), Vector3(1.45, 0.14, 0.22), team_mat)
		_add_box(root, Vector3(0, 0.42, 0), Vector3(0.38, 0.13, 1.45), team_mat)
		_add_box(root, Vector3(-0.18, 0.53, 0), Vector3(0.34, 0.20, 0.24), _material(Color("#a9d7e8"), 0.2, 0.2))
		_add_box(root, Vector3(-0.67, 0.38, 0), Vector3(0.22, 0.10, 0.38), _material(Color("#d6d8d4"), 0.25, 0.35))
	else:
		_add_box(root, Vector3(0, 0.28, 0), Vector3(0.95, 0.30, 0.65), team_mat)
		_add_cylinder(root, Vector3(0, 0.43, 0), 0.27, 0.18, Color("#58634b"))
		_add_box(root, Vector3(0.45, 0.55, 0), Vector3(1.0, 0.10, 0.10), team_mat)
		for wheel_z in [-0.42, 0.42]:
			_add_cylinder(root, Vector3(-0.25, 0.17, wheel_z), 0.20, 0.12, Color("#303b32"))
			_add_cylinder(root, Vector3(0.25, 0.17, wheel_z), 0.20, 0.12, Color("#303b32"))

func _add_box(parent: Node3D, pos: Vector3, size: Vector3, mat: Material) -> void:
	var mesh_instance := MeshInstance3D.new()
	var mesh := BoxMesh.new()
	mesh.size = size
	mesh_instance.mesh = mesh
	mesh_instance.position = pos
	mesh_instance.material_override = mat
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
	parent.add_child(mesh_instance)

func _build_ui() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)
	var panel := PanelContainer.new()
	panel.position = Vector2(16, 14)
	panel.size = Vector2(350, 190)
	layer.add_child(panel)
	var column := VBoxContainer.new()
	panel.add_child(column)
	ui_label = Label.new()
	ui_label.text = "GENERAL — ساحة ثلاثية الأبعاد"
	ui_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	column.add_child(ui_label)
	roll_button = Button.new()
	roll_button.text = "ارمِ النرد"
	roll_button.pressed.connect(_on_roll_pressed)
	column.add_child(roll_button)
	for lane in range(3):
		var button := Button.new()
		button.text = WEAPON_NAMES[lane]
		button.disabled = true
		button.pressed.connect(_on_weapon_pressed.bind(lane))
		column.add_child(button)
		weapon_buttons.append(button)
	reset_button = Button.new()
	reset_button.text = "إعادة المباراة"
	reset_button.pressed.connect(_reset_match)
	column.add_child(reset_button)

func _on_roll_pressed() -> void:
	if game_over or waiting_for_weapon:
		return
	die_value = randi_range(1, 6)
	waiting_for_weapon = true
	roll_button.disabled = true
	ui_label.text = "اللاعب %d — نتيجة النرد: %d — اختر سلاحاً" % [current_player + 1, die_value]
	var possible := false
	for lane in range(3):
		var can_use := not destroyed[current_player][lane] and progress[current_player][lane] + die_value <= STEPS
		weapon_buttons[lane].disabled = not can_use
		possible = possible or can_use
	if not possible:
		ui_label.text = "لا توجد حركة ممكنة — خسر اللاعب دوره"
		waiting_for_weapon = false
		current_player = 1 - current_player
		roll_button.disabled = false

func _on_weapon_pressed(lane: int) -> void:
	if not waiting_for_weapon or game_over:
		return
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
		ui_label.text = "اللاعب %d أنهى حركته — دور اللاعب %d" % [current_player + 1, 2 - current_player]
		current_player = 1 - current_player
	roll_button.disabled = game_over
	for button in weapon_buttons:
		button.disabled = true
	_refresh_board()

func _reset_match() -> void:
	progress = [[0, 0, 0], [0, 0, 0]]
	destroyed = [[false, false, false], [false, false, false]]
	current_player = 0
	die_value = 0
	waiting_for_weapon = false
	game_over = false
	ui_label.text = "بدأت معركة جديدة — اللاعب 1 يرمي النرد"
	roll_button.disabled = false
	for button in weapon_buttons:
		button.disabled = true
	_refresh_board()

func _refresh_board() -> void:
	for item in bullet_nodes:
		var side: int = item["side"]
		var lane: int = item["lane"]
		var step: int = item["step"]
		var mesh_node: Node3D = item["node"]
		var filled := step < progress[side][lane]
		var target_color := (Color("#238be6") if side == 0 else Color("#e34e43")) if filled else Color("#b9c0c1")
		for child in mesh_node.get_children():
			if child is MeshInstance3D:
				var animated_material := StandardMaterial3D.new()
				animated_material.albedo_color = Color("#b9c0c1")
				animated_material.metallic = 0.72
				animated_material.roughness = 0.18
				child.material_override = animated_material
				var tween := create_tween()
				tween.tween_property(animated_material, "albedo_color", target_color, 0.22).set_delay(float(step) * 0.018 if filled else 0.0)
