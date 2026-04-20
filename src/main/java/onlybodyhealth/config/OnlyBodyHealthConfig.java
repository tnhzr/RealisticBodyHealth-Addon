package onlybodyhealth.config;

import org.bukkit.configuration.file.FileConfiguration;

public record OnlyBodyHealthConfig(
        boolean strictMode,
        int healthSyncIntervalTicks,
        boolean clearAbsorption,
        boolean respectBodyHealthBypass,
        boolean allowVanillaKillCommands,
        boolean requireHealOnFullHealthDisabled
) {

    public static OnlyBodyHealthConfig from(FileConfiguration config) {
        return new OnlyBodyHealthConfig(
                config.getBoolean("strict-mode", true),
                Math.max(1, config.getInt("health-sync-interval-ticks", 1)),
                config.getBoolean("clear-absorption", true),
                config.getBoolean("respect-bodyhealth-bypass", true),
                config.getBoolean("allow-vanilla-kill-commands", true),
                config.getBoolean("require-heal-on-full-health-disabled", true)
        );
    }
}
