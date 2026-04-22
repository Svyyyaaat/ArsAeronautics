# Ars Aeronautics

> Most of this codebase was generated with Claude Opus 4.7.

A Minecraft mod that bridges [Ars Nouveau](https://github.com/baileyholl/Ars-Nouveau) and [Simulated/Aeronautics](https://www.curseforge.com/minecraft/mc-mods/create-aeronautics) (Sable physics engine), allowing spells to interact with physics-enabled contraptions (SubLevels).

**Minecraft 1.21.1 | NeoForge | MIT License**

## Features

### Spell Effects on Contraptions

Existing Ars Nouveau spells are extended via mixins to affect Sable SubLevels:

- **Knockback / Launch / Pull** — Apply linear impulse to contraptions, pushing or pulling them through the air. Augments (Amplify, Dampen, Sensitive) all work.
- **Rotate** — Apply torque impulse to spin contraptions. Requires the Extract augment to target SubLevels (otherwise uses vanilla behavior).
- **Gravity** — Continuous downward force on a contraption for a duration. Stacks with Amplify.
- **Snare** — Freezes a contraption in place by zeroing its velocity each tick.
- **Slowfall** — Reduces a contraption's downward velocity, letting it drift gently.
- **Levitate** — Continuous upward force (inverse of Gravity).
- **Blink** — Teleport a contraption (and all riders/nearby entities) to a Warp Scroll destination. Supports same-dimension and cross-dimension teleportation, delayed teleport via Extend Time, and turret casting from spell turrets with adjacent inventory warp scrolls.

### New Spell: Telekinesis

A custom Tier 3 glyph that grabs a contraption and moves it with your gaze, similar to the Sable iron Handle but with 10x the power and 3x the range.

- **Cast on a contraption** — Grabs it with a physics constraint that follows your look direction. Left-click to release.
- **Cast on yourself** (via Self) — Grants creative flight for the spell's duration.
- **Cast on an entity** — Drags animals/players toward your look direction.
- **Augments**: Amplify (stronger grip), Dampen (weaker grip, min 10%), Extend Time (longer duration).
- **Recipe**: 3x Levitite Blend Bucket + End Crystal + Netherite Ingot.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1+
- [Ars Nouveau](https://github.com/baileyholl/Ars-Nouveau) 5.3+
- [Simulated](https://www.curseforge.com/minecraft/mc-mods/create-aeronautics) 1.0+ (provides the Sable physics engine)

## Building

```bash
./gradlew build
```

The built jar will be at `build/libs/ars_aeronautics-1.0.0.jar`.
