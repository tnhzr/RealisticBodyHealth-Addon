# OnlyBodyHealth

`OnlyBodyHealth` is a `BodyHealth` addon for `Paper 1.21.8` / `Java 21` that disables vanilla HP gameplay and keeps survivability on body parts only.

## What It Does

- Lets `BodyHealth` process hits first, then cancels the vanilla `EntityDamageEvent` so hearts are not consumed.
- Blocks vanilla `EntityRegainHealthEvent` before `BodyHealth` can turn that heal into body-part regeneration.
- Pins covered players' vanilla health to max and clears absorption hearts on a sync loop.
- Respects disabled worlds and bypass permissions so fallback vanilla HP still works where needed.
- Leaves explicit vanilla kill flows like `/kill` alone by default.

## Important BodyHealth Setting

Strict mode depends on this value in `plugins/BodyHealth/config.yml`:

```yml
heal-on-full-health: false
```

If `BodyHealth` still has `heal-on-full-health: true`, this addon disables strict mode for safety and logs a warning.

## Build

```bash
mvn package
```

The built jar will be created in `target/`.

## Install

1. Build the jar with Maven.
2. Drop the jar into `plugins/BodyHealth/addons`.
3. Restart the server or reload `BodyHealth`.
4. Confirm `plugins/BodyHealth/config.yml` has `heal-on-full-health: false`.

## Permissions

- `onlybodyhealth.bypass`
- Respects `bodyhealth.bypass.*`
- Respects `bodyhealth.bypass.damage.*`
- Respects `bodyhealth.bypass.regen.*`
- Respects per-part `bodyhealth.bypass.damage.<part>` and `bodyhealth.bypass.regen.<part>`

## Heart HUD

Server-side Bukkit/Paper APIs can control health values and scale, but they do not provide a reliable way to fully hide vanilla hearts for all clients.

For complete visual removal, use a client resource pack and show health through `BetterHud` / `BodyHealth`.

There is a documented template in [docs/resource-pack-template/README.md](docs/resource-pack-template/README.md).
