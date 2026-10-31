# GENERAL — 3D prototype

This branch adds a separate Godot 4 project under `godot/`. The existing Android Java prototype remains unchanged on `main`.

## Current 3D prototype foundation
- A real 3D battlefield with a perspective camera, lighting, ground and arena.
- Three 3D weapon models per side: tank, aircraft and artillery (built from 3D meshes as prototype models).
- Six independent tracks, each containing 15 3D bullet-shaped pieces rather than square cells.
- Uncolored metallic bullets gradually change to the moving player's team color after a die roll and weapon selection.
- A basic local two-player dice loop, exact-15 hit rule, two-weapons-destroyed win condition and match reset.

## Not implemented yet
This is the first 3D prototype foundation, not the finished game. It does not yet include polished imported 3D character/weapon assets, cinematic character animations, online accounts/friends, expert AI, sound design, or a signed Android APK export.

## Validation
The `Validate GENERAL 3D prototype` workflow imports the Godot project and checks its scripts.
