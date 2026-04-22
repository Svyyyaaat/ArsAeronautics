# Ars Aeronautics

> Most of this codebase was generated with Claude Opus 4.7.

A Minecraft mod that bridges [Ars Nouveau](https://modrinth.com/mod/ars-nouveau) and [Create: Aeronautics](https://modrinth.com/mod/create-aeronautics) ([Sable](https://modrinth.com/mod/sable) physics engine), allowing spells to interact with physics-enabled contraptions (SubLevels).

**Minecraft 1.21.1 | NeoForge | MIT License**

## Features

### Spell Effects on Contraptions

Existing Ars Nouveau spells are extended via mixins to affect Sable SubLevels:

- **Knockback / Launch / Pull** — Apply linear impulse to contraptions, pushing or pulling them through the air. Augments (Amplify, Dampen, Sensitive) all work.
- **Gravity** — Functions as downwards Launch, but when augmented with Extend Time applies continuous downward force on for a duration.
- **Rotate** — Apply torque impulse to spin contraptions. Requires the Extract augment to target SubLevels (otherwise uses vanilla behavior).
- **Snare** — Freezes a contraption in place.
- **Slowfall** — Reduces a contraption's downward velocity, letting it drift gently, similar to what levitite does.
- **Blink** — Teleport a contraption (and all riders/nearby entities) to a Warp Scroll destination. Supports same-dimension and cross-dimension teleportation, delayed teleport with flashy particles and sounds via Extend Time, and turret casting from spell turrets with adjacent inventory warp scrolls. 

### New Spell: Telekinesis

A custom Tier 3 glyph that grabs a contraption and moves it with your gaze, similar to the Simulated's iron handle but with 10x the power. Poor man's gravity gun.

- **Cast on a contraption** — Grabs it with a physics constraint that follows your look direction. Left-click to release.
- **Cast on yourself** (via Self) — Grants creative flight for the spell's duration.
- **Cast on an entity** — Drags animals/players toward your look direction.
- **Augments**: Amplify (stronger grip), Dampen (weaker grip, min 10%), Extend Time (longer duration).
- **Recipe**: 3x Levitite Blend Bucket + End Crystal + Netherite Ingot.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1+
- [Ars Nouveau](https://modrinth.com/mod/ars-nouveau) 5.3+
- [Sable](https://modrinth.com/mod/sable) 1.0+
- [Create: Aeronautics](https://modrinth.com/mod/create-aeronautics) 1.0+

## Building

```bash
./gradlew build
```

The built jar will be at `build/libs/ars_aeronautics-1.0.0+1.21.1-neoforge.jar`.
