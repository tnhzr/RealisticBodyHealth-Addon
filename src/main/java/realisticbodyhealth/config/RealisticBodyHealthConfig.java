package realisticbodyhealth.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record RealisticBodyHealthConfig(
        boolean strictMode,
        int healthSyncIntervalTicks,
        boolean clearAbsorption,
        boolean respectBodyHealthBypass,
        boolean autoDisableBodyHealthHealOnFullHealth,
        BleedingConfig bleeding
) {

    public static RealisticBodyHealthConfig from(FileConfiguration config) {
        ConfigurationSection bleedingSection = config.getConfigurationSection("bleeding");

        return new RealisticBodyHealthConfig(
                config.getBoolean("strict-mode", true),
                Math.max(1, config.getInt("health-sync-interval-ticks", 1)),
                config.getBoolean("clear-absorption", true),
                config.getBoolean("respect-bodyhealth-bypass", true),
                config.getBoolean("auto-disable-bodyhealth-heal-on-full-health", true),
                BleedingConfig.from(bleedingSection)
        );
    }

    public record BleedingConfig(
            boolean enabled,
            int intervalTicks,
            double baseTorsoDamagePercent,
            double extraDamagePerStackPercent,
            int particleCountBase,
            int particleCountPerStack,
            String sound,
            float soundVolume,
            float soundPitchBase,
            String actionbar
    ) {

        public static BleedingConfig from(ConfigurationSection config) {
            if (config == null) {
                return new BleedingConfig(true, 40, 2.5D, 1.5D, 8, 4, "entity.player.hurt", 0.9F, 0.8F, "&cYou are bleeding out");
            }

            return new BleedingConfig(
                    config.getBoolean("enabled", true),
                    Math.max(1, config.getInt("interval-ticks", 40)),
                    Math.max(0.1D, config.getDouble("base-torso-damage-percent", 2.5D)),
                    Math.max(0.0D, config.getDouble("extra-damage-per-stack-percent", 1.5D)),
                    Math.max(0, config.getInt("particle-count-base", 8)),
                    Math.max(0, config.getInt("particle-count-per-stack", 4)),
                    config.getString("sound", "entity.player.hurt"),
                    (float) config.getDouble("sound-volume", 0.9D),
                    (float) config.getDouble("sound-pitch-base", 0.8D),
                    config.getString("actionbar", "&cYou are bleeding out")
            );
        }
    }
}
