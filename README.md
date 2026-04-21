# RealisticBodyHealth

`RealisticBodyHealth` is a `BodyHealth` addon for `Paper 1.21.x` on `Java 21` to `Java 26`.

It keeps vanilla hearts out of survival logic and lets `BodyHealth` stay in full control of where damage is applied.

## Features

- Keeps melee, projectile, fall, fire, lava, drowning, freezing, starvation, suffocation, and other damage causes routed by `BodyHealth`.
- Prevents vanilla hearts from becoming the real survival system.
- Does not duplicate `HEAD` or `TORSO` death handling.
- Adds bleeding from broken limbs, with configurable speed, visuals, sound, and lethality.
- Converts allowed healing into body-part healing:
  - `Instant Health`
  - active `Regeneration`, including golden apples and beacons
- Supports `OP` players when `apply-to-operators: true`. ( When testing always disable op or perm for better results, it may conflict )

## Important Behavior

- Damage distribution is not hardcoded in this addon.
- If you want hunger, suffocation, fire, drowning, or any other cause to hit specific body parts, configure that in `BodyHealth` itself.
- `plugins/BodyHealth/config.yml -> heal-on-full-health` is forced to `false` automatically because it conflicts with this addon.

## Compatibility

- `Paper 1.21.x`
- `Java 21` to `Java 26`
- `BodyHealth 4.1.0`

## Build

```bash
mvn package
```

The built jar is created in `target/` as `RealisticBodyHealth-<version>.jar`.

## Install

1. Build the jar or download a release.
2. Do not place the jar in `/plugins`.
3. Place it in `/plugins/BodyHealth/addons/`.
4. Restart the server or reload `BodyHealth`.

## Resource Pack

The repository includes a ready-to-use [resourcepack](resourcepack/) folder with hidden vanilla heart textures.

If you want to remove the vanilla heart HUD completely:

1. Zip the contents of `resourcepack/`.
2. Use that zip as your server resource pack.
3. Show health through `BetterHud` or `BodyHealth`.

## Config

Top-level options:

- `strict-mode`
- `health-sync-interval-ticks`
- `clear-absorption`
- `respect-bodyhealth-bypass`
- `apply-to-operators`
- `auto-disable-bodyhealth-heal-on-full-health`

Bleeding options:

- `bleeding.enabled`
- `bleeding.interval-ticks`
- `bleeding.can-kill`
- `bleeding.fatal-time-seconds-single-stack`
- `bleeding.extra-speed-per-stack`
- `bleeding.non-lethal-min-torso-health-percent`
- `bleeding.particle-count-base`
- `bleeding.particle-count-per-stack`
- `bleeding.sound`
- `bleeding.sound-volume`
- `bleeding.sound-pitch-base`
- `bleeding.actionbar`

## Permissions

- `realisticbodyhealth.bypass`
- `bodyhealth.bypass.*`
- `bodyhealth.bypass.damage.*`
- `bodyhealth.bypass.regen.*`
- `bodyhealth.bypass.damage.<part>`
- `bodyhealth.bypass.regen.<part>`
