# RealisticBodyHealth

`RealisticBodyHealth` is a `BodyHealth` addon for `Paper 1.21.x` running on `Java 21` to `Java 26`.

The addon keeps vanilla hearts out of survival logic and lets `BodyHealth` remain the main source of truth for body-part damage.

## What The Addon Does

- Leaves external damage from mobs, players, arrows, splash potions, and blocks to `BodyHealth`, while preventing vanilla hearts from becoming the real survival system.
- Redirects internal and non-locational damage directly to `TORSO`.
  - This includes starvation, drowning, fire, lava, and other damage causes without a specific hit location.
- Does not duplicate `HEAD` / `TORSO` death handling.
  - Death at zero head or torso health should be configured in `BodyHealth` itself.
- Keeps bleeding from broken limbs and continues converting that bleeding into gradual `TORSO` damage.
- Supports `OP` players.
  - By default, the addon also applies to operators even if they implicitly have bypass-style permissions.
- Restores body-part healing from:
  - instant health potions
  - regeneration potions
  - other `EntityRegainHealthEvent` sources, which are now converted into body-part healing instead of vanilla heart healing
- Heals body parts after sleeping only when the night was actually skipped.

## Visual Effects

- Bleeding still has blood effects, sound, and actionbar feedback.
- Low `HEAD` or `TORSO` health adds a red vignette effect through a personal `WorldBorder` plus extra red particles.

## Compatibility

- `Paper 1.21.x`
- `Java 21` - `Java 26`
- `BodyHealth 4.1.0`

`RealisticBodyHealth` automatically forces `plugins/BodyHealth/config.yml -> heal-on-full-health` to `false`, because that setting breaks the addon's damage and healing model.

## Build

```bash
mvn package
```

The built jar is created in `target/` as `RealisticBodyHealth-<version>.jar`.

## Install

1. Build the jar with Maven or use a release build.
2. Do **not** place the addon jar in `/plugins`.
3. The correct install path is `/plugins/BodyHealth/addons/`
4. Restart the server or reload `BodyHealth`.

## Heart-Hiding Resource Pack

The repository already includes a ready-to-use [resourcepack](resourcepack/) folder with hidden vanilla heart textures.

If you want to fully remove the vanilla heart HUD:

1. Zip the contents of the `resourcepack/` folder.
2. Use that zip as a normal server resource pack.
3. Display health through `BetterHud` / `BodyHealth`.

There is also a documented template in [docs/resource-pack-template/README.md](docs/resource-pack-template/README.md).

## Config

Main options:

- `apply-to-operators`
  - enables addon mechanics for `OP` players
- `bleeding.*`
  - controls bleeding speed and lethality
- `critical-effects.*`
  - controls thresholds and visual intensity for `HEAD` and `TORSO`
- `sleep-healing.heal-percent-per-part`
  - controls how much health each body part restores after one actually skipped night

## Permissions

- `realisticbodyhealth.bypass`
- Respects `bodyhealth.bypass.*`
- Respects `bodyhealth.bypass.damage.*`
- Respects `bodyhealth.bypass.regen.*`
- Respects `bodyhealth.bypass.damage.<part>` and `bodyhealth.bypass.regen.<part>`

If `apply-to-operators: true`, operators will not automatically bypass the addon only because they are `OP`.
