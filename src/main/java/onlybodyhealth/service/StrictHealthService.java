package onlybodyhealth.service;

import bodyhealth.api.BodyHealthAPI;
import bodyhealth.core.BodyPart;
import onlybodyhealth.OnlyBodyHealthAddon;
import onlybodyhealth.config.OnlyBodyHealthConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

public final class StrictHealthService {

    private static final String ADDON_BYPASS_PERMISSION = "onlybodyhealth.bypass";

    private final OnlyBodyHealthAddon addon;
    private final BodyHealthAPI bodyHealthApi;

    private OnlyBodyHealthConfig config;
    private boolean compatibilityPassed;
    private BukkitTask syncTask;

    public StrictHealthService(OnlyBodyHealthAddon addon) {
        this.addon = addon;
        this.bodyHealthApi = BodyHealthAPI.getInstance();
    }

    public void reload(OnlyBodyHealthConfig config) {
        this.config = config;
        this.compatibilityPassed = validateCompatibility();
        restartSyncTask();
    }

    public void shutdown() {
        cancelSyncTask();
    }

    public void syncOnlinePlayers() {
        if (!isStrictModeEnabled()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPlayer(player);
        }
    }

    public void scheduleSync(Player player, long delayTicks) {
        addon.getBodyHealthPlugin().getServer().getScheduler().runTaskLater(
                addon.getBodyHealthPlugin(),
                () -> syncPlayer(player),
                Math.max(1L, delayTicks)
        );
    }

    public void syncPlayer(Player player) {
        if (!shouldControl(player)) {
            return;
        }

        if (!player.isOnline() || player.isDead() || player.getHealth() <= 0.0D) {
            return;
        }

        lockVanillaHealth(player);
    }

    public boolean shouldControl(Player player) {
        return player != null
                && config != null
                && isStrictModeEnabled()
                && bodyHealthApi.isSystemEnabled(player)
                && !hasBypass(player);
    }

    public boolean shouldAllowVanillaKill(EntityDamageEvent event) {
        return config != null
                && config.allowVanillaKillCommands()
                && event.getCause() == EntityDamageEvent.DamageCause.KILL;
    }

    public void lockVanillaHealth(Player player) {
        double maxHealth = player.getMaxHealth();
        if (player.getHealth() != maxHealth) {
            player.setHealth(maxHealth);
        }

        if (config.clearAbsorption() && player.getAbsorptionAmount() != 0.0D) {
            player.setAbsorptionAmount(0.0D);
        }
    }

    public void restoreInvulnerabilityFrames(Player player, int previousNoDamageTicks) {
        int targetNoDamageTicks = Math.max(previousNoDamageTicks, player.getMaximumNoDamageTicks());
        if (player.getNoDamageTicks() < targetNoDamageTicks) {
            player.setNoDamageTicks(targetNoDamageTicks);
        }
    }

    private boolean isStrictModeEnabled() {
        return config != null && config.strictMode() && compatibilityPassed;
    }

    private boolean validateCompatibility() {
        if (config == null || !config.strictMode()) {
            return true;
        }

        if (!config.requireHealOnFullHealthDisabled()) {
            return true;
        }

        boolean healOnFullHealth = addon.getBodyHealthPlugin().getConfig().getBoolean("heal-on-full-health", true);
        if (!healOnFullHealth) {
            return true;
        }

        addon.getAddonDebug().logRaw(
                "Strict mode disabled because BodyHealth still has 'heal-on-full-health: true'. " +
                        "Set plugins/BodyHealth/config.yml -> heal-on-full-health: false and reload BodyHealth."
        );
        return false;
    }

    private boolean hasBypass(Player player) {
        if (player.hasPermission(ADDON_BYPASS_PERMISSION)) {
            return true;
        }

        if (!config.respectBodyHealthBypass()) {
            return false;
        }

        if (player.hasPermission("bodyhealth.bypass.*")
                || player.hasPermission("bodyhealth.bypass.damage.*")
                || player.hasPermission("bodyhealth.bypass.regen.*")) {
            return true;
        }

        for (BodyPart part : BodyPart.values()) {
            String partName = part.name().toLowerCase(Locale.ROOT);
            if (player.hasPermission("bodyhealth.bypass.damage." + partName)
                    || player.hasPermission("bodyhealth.bypass.regen." + partName)) {
                return true;
            }
        }

        return false;
    }

    private void restartSyncTask() {
        cancelSyncTask();

        if (!isStrictModeEnabled()) {
            return;
        }

        long interval = config.healthSyncIntervalTicks();
        syncTask = addon.getBodyHealthPlugin().getServer().getScheduler().runTaskTimer(
                addon.getBodyHealthPlugin(),
                this::syncOnlinePlayers,
                interval,
                interval
        );
    }

    private void cancelSyncTask() {
        if (syncTask == null) {
            return;
        }

        syncTask.cancel();
        syncTask = null;
    }
}
