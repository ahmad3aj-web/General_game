extends Node2D

@export var kind: int = 0
@export var team_color: Color = Color("#66865a")

const INK := Color("#292a27")
const PAPER := Color("#f7f0dc")
const METAL := Color("#c9c9bd")
const HIGHLIGHT := Color("#fff9e9")
const SHADE := Color("#8b8c7d")

func _draw() -> void:
	if kind == 0:
		_draw_tank()
	elif kind == 1:
		_draw_aircraft()
	elif kind == 2:
		_draw_cannon()
	elif kind == 3:
		_draw_shell()
	elif kind == 4:
		_draw_explosion()

func _poly(points: PackedVector2Array, fill: Color, width: float = 4.0) -> void:
	draw_colored_polygon(points, fill)
	var outline := points.duplicate()
	outline.append(points[0])
	draw_polyline(outline, INK, width, true)

func _line(a: Vector2, b: Vector2, color: Color = INK, width: float = 3.0) -> void:
	draw_line(a, b, color, width, true)

func _draw_tank() -> void:
	# Hand-inked top-view tank with individual track links and a rounded turret.
	draw_rect(Rect2(24, 25, 208, 110), INK)
	draw_rect(Rect2(29, 30, 198, 100), Color("#4b5145"))
	for y in [39.0, 119.0]:
		for x in range(42, 220, 22):
			draw_circle(Vector2(x, y), 7.0, Color("#c6bfa8"))
			draw_arc(Vector2(x, y), 7.0, 0.0, TAU, 24, INK, 2.5, true)
		for x in range(38, 220, 12):
			_line(Vector2(x, y - 8), Vector2(x + 5, y - 8), Color("#ddd4bd"), 2)
	_poly(PackedVector2Array([Vector2(47,45),Vector2(195,45),Vector2(211,60),Vector2(211,100),Vector2(194,115),Vector2(47,115),Vector2(34,99),Vector2(34,61)]), team_color, 5)
	_line(Vector2(58,55),Vector2(185,55),Color("#e5d9b6"),3)
	_line(Vector2(58,105),Vector2(185,105),Color("#596c4d"),3)
	# turret and cannon
	draw_rect(Rect2(117, 73, 122, 14), INK)
	draw_rect(Rect2(120, 76, 116, 8), METAL)
	draw_circle(Vector2(112,80),34,INK)
	draw_circle(Vector2(112,77),29,team_color.lightened(0.13))
	draw_arc(Vector2(112,77),25,PI,TAU,36,HIGHLIGHT,3,true)
	draw_circle(Vector2(101,77),5,Color("#45483f"))
	draw_circle(Vector2(101,77),2,Color("#e5d8b6"))
	_line(Vector2(89,56),Vector2(76,48),Color("#f1e6c9"),2)
	# hand-drawn panel seams
	_line(Vector2(57,66),Vector2(76,58),INK,2)
	_line(Vector2(57,94),Vector2(76,102),INK,2)
	draw_circle(Vector2(191,62),5,Color("#d9d0ad"))
	draw_circle(Vector2(191,98),5,Color("#d9d0ad"))

func _draw_aircraft() -> void:
	# Fighter jet viewed from above, with swept wings and a glass cockpit.
	_poly(PackedVector2Array([Vector2(224,80),Vector2(158,62),Vector2(126,15),Vector2(106,18),Vector2(119,68),Vector2(52,47),Vector2(34,58),Vector2(91,80),Vector2(34,102),Vector2(52,113),Vector2(119,92),Vector2(106,142),Vector2(126,145),Vector2(158,98)]), team_color,5)
	_poly(PackedVector2Array([Vector2(222,80),Vector2(151,73),Vector2(112,73),Vector2(80,80),Vector2(112,87),Vector2(151,87)]), Color("#e6e0ce"),3)
	draw_ellipse(Vector2(153,80),Vector2(23,9),Color("#a8d9df"))
	draw_arc(Vector2(153,80),23,PI,TAU,24,INK,2.5,true)
	_line(Vector2(59,56),Vector2(109,72),Color("#f5e9cb"),3)
	_line(Vector2(59,104),Vector2(109,88),Color("#f5e9cb"),3)

func _draw_cannon() -> void:
	# Field gun with a shield, carriage and two detailed wheels.
	for y in [43.0,117.0]:
		draw_circle(Vector2(91,y),27,INK)
		draw_circle(Vector2(91,y),21,Color("#a9a89b"))
		draw_circle(Vector2(91,y),11,Color("#e4ddc9"))
		draw_circle(Vector2(91,y),4,INK)
		for a in range(8):
			var angle := TAU * float(a) / 8.0
			_line(Vector2(91,y)+Vector2(cos(angle),sin(angle))*13,Vector2(91,y)+Vector2(cos(angle),sin(angle))*19,INK,2)
	_poly(PackedVector2Array([Vector2(46,52),Vector2(130,52),Vector2(146,65),Vector2(146,95),Vector2(130,108),Vector2(46,108),Vector2(35,96),Vector2(35,64)]),team_color,5)
	_poly(PackedVector2Array([Vector2(92,43),Vector2(145,50),Vector2(157,80),Vector2(145,110),Vector2(92,117),Vector2(105,80)]),Color("#d7d0ba"),4)
	draw_rect(Rect2(139,73,95,14),INK)
	draw_rect(Rect2(143,76,91,8),METAL)
	draw_circle(Vector2(137,80),15,INK)
	draw_circle(Vector2(137,80),10,Color("#c5bda7"))

func _draw_shell() -> void:
	_poly(PackedVector2Array([Vector2(18,55),Vector2(166,55),Vector2(220,80),Vector2(166,105),Vector2(18,105)]),Color("#ded9c9"),5)
	draw_rect(Rect2(18,52,18,56),Color("#9b9b91"))
	draw_rect(Rect2(21,57,7,46),Color("#ece5d2"))
	_line(Vector2(164,57),Vector2(164,103),INK,3)
	_line(Vector2(42,64),Vector2(155,64),HIGHLIGHT,2)
	_line(Vector2(42,96),Vector2(155,96),Color("#aaa99d"),2)

func _draw_explosion() -> void:
	var pts := PackedVector2Array()
	for i in range(20):
		var angle := TAU * float(i) / 20.0
		var radius := 62.0 if i % 2 == 0 else 37.0
		pts.append(Vector2(80,80)+Vector2(cos(angle),sin(angle))*radius)
	_poly(pts,Color("#f4c45f"),5)
	draw_circle(Vector2(80,80),22,Color("#ed8750"))
	for p in [Vector2(18,22),Vector2(139,25),Vector2(150,130),Vector2(20,139)]:
		draw_line(p-Vector2(7,0),p+Vector2(7,0),INK,3)
		draw_line(p-Vector2(0,7),p+Vector2(0,7),INK,3)

func draw_ellipse(center: Vector2, radii: Vector2, color: Color) -> void:
	var pts := PackedVector2Array()
	for i in range(33):
		var a := TAU * float(i) / 32.0
		pts.append(center + Vector2(cos(a)*radii.x,sin(a)*radii.y))
	draw_colored_polygon(pts,color)
	draw_polyline(pts,INK,3,true)
