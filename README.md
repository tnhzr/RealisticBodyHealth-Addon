# RealisticBodyHealth

`RealisticBodyHealth` is a `BodyHealth` addon for `Paper 1.21.8` / `Java 21` that makes body-part damage the source of truth for survival.

## What It Does

- Lets `BodyHealth` process hits first, then cancels vanilla HP loss so hearts do not decide death.
- Leaves `HEAD` / `TORSO` death to `BodyHealth` itself.
- Starts bleeding when arms, legs, or feet are broken.
- Converts bleeding into periodic `TORSO` damage until the player dies from blood loss.
- Blocks vanilla healing, clears absorption hearts, and keeps vanilla HP synced to max for covered players.

## Compatibility

`RealisticBodyHealth` automatically forces `plugins/BodyHealth/config.yml` -> `heal-on-full-health: false` and reloads `BodyHealth`, because the default value breaks strict body-part-only gameplay.

## Build

```bash
mvn package
```

The built jar will be created in `target/` as `RealisticBodyHealth-<version>.jar`.

## Install

1. Build the jar with Maven.
2. Drop the jar into `plugins/BodyHealth/addons`.
3. Restart the server or reload `BodyHealth`.

## Permissions

- `realisticbodyhealth.bypass`
- Respects `bodyhealth.bypass.*`
- Respects `bodyhealth.bypass.damage.*`
- Respects `bodyhealth.bypass.regen.*`
- Respects per-part `bodyhealth.bypass.damage.<part>` and `bodyhealth.bypass.regen.<part>`

## Lethal Rules

- `HEAD` / `TORSO` lethal handling is expected to be configured in `BodyHealth`.
- Broken limbs do not kill immediately.
- Broken limbs apply bleed stacks.
- Bleed stacks deal periodic `TORSO` damage.
- Bleeding speed and whether it can kill are configurable in this addon's config.

## Heart HUD

Server-side Bukkit/Paper APIs can control health values and scale, but they do not provide a reliable way to fully hide vanilla hearts for all clients.

For complete visual removal, use a client resource pack and show health through `BetterHud` / `BodyHealth`.

There is a documented template in [docs/resource-pack-template/README.md](docs/resource-pack-template/README.md).
