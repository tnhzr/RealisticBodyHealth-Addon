# Heart-Hide Resource Pack Template

This folder is intentionally documentation-first.

`RealisticBodyHealth` handles the gameplay side of disabling vanilla HP. To hide the vanilla heart HUD completely, provide a client resource pack with transparent replacements for the vanilla heart textures used by your target Minecraft client version.

## Recommended Workflow

1. Start from an empty resource pack that matches your server's target client version.
2. Replace the vanilla heart HUD textures with transparent PNGs.
3. Keep hunger, armor, and other HUD sprites untouched unless you also want to hide them.
4. Distribute that pack alongside your `BetterHud` / `BodyHealth` setup so the body-part HUD becomes the only visible health display.

## Why This Is Not Bundled Automatically

- Heart HUD sprite paths can change between Minecraft versions.
- The server cannot reliably force a universal client-side heart hide through Bukkit/Paper APIs alone.
- A transparent resource pack keeps the addon version-agnostic and easy to adapt.

## Suggested Files To Replace

On modern clients, the relevant assets are usually under the HUD GUI sprite set. Use transparent PNGs for the heart variants that exist in your target version, such as:

- full heart
- half heart
- empty heart
- hardcore variants
- absorption variants
- blinking / hurt overlay variants

Verify the exact asset paths against the client version you distribute to players.
