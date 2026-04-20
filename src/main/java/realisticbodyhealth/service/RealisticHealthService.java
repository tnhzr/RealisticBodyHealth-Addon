package realisticbodyhealth.service;

import bodyhealth.api.BodyHealthAPI;
import bodyhealth.api.events.BodyPartHealthChangeEvent;
import bodyhealth.api.events.BodyPartStateChangeEvent;
import bodyhealth.core.BodyPart;
import bodyhealth.core.BodyPartState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import realisticbodyhealth.RealisticBodyHealthAddon;
import realisticbodyhealth.config.RealisticBodyHealthConfig;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RealisticHealthService {

    private static final String ADDON_BYPASS_PERMISSION = "realisticbodyhealth.bypass";
    private static final Set<BodyPart> LETHAL_PARTS = EnumSet.of(BodyPart.HEAD, BodyPart.TORSO);
    private static final Set<BodyPart> BLEEDING_PARTS = EnumSet.of(
            BodyPart.ARM_LEFT,
            BodyPart.ARM_RIGHT,
            BodyPart.LEG_LEFT,
            BodyPart.LEG_RIGHT,
            BodyPart.FOOT_LEFT,
            BodyPart.FOOT_RIGHT
    );

    private final RealisticBodyHealthAddon addon;
    private final BodyHealthAPI bodyHealthApi;
    private final LegacyComponentSerializer legacySerializer;
    private final Map<UUID, EnumSet<BodyPart>> brokenLimbs;
    private final Set<UUID> addonOwnedDeaths;

    private RealisticBodyHealthConfig config;
    private BukkitTask syncTask;
    private BukkitTask bleedingTask;

    public RealisticHealthService(RealisticBodyHealthAddon addon) {
        this.addon = addon;
        this.bodyHealthApi = BodyHealthAPI.getInstance();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.brokenLimbs = new ConcurrentHashMap<>();
        this.addonOwnedDeaths = ConcurrentHashMap.newKeySet();
    }

    public void reload(RealisticBodyHealthConfig config) {
        this.config = config;
        ensureBodyHealthCompatibility();
        restartSyncTask();
        restartBleedingTask();
    }

    public void shutdown() {
        cancelTask(syncTask);
        cancelTask(bleedingTask);
        brokenLimbs.clear();
        addonOwnedDeaths.clear();
    }

    public boolean shouldControl(Player player) {
        return player != null
                && config != null
                && config.strictMode()
                && bodyHealthApi.isSystemEnabled(player)
                && !hasBypass(player);
    }

    public boolean shouldProtectVanillaHealth(Player player) {
        return shouldControl(player) && !isAddonDeathInProgress(player);
    }

    public boolean shouldAllowVanillaKill(EntityDamageEvent event) {
        return event.getCause() == EntityDamageEvent.DamageCause.KILL;
    }

    public void syncOnlinePlayers() {
        if (config == null) {
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
            forgetPlayer(player);
            return;
        }

        refreshBrokenLimbState(player);

        if (!player.isOnline() || player.isDead()) {
            return;
        }

        if (shouldProtectVanillaHealth(player)) {
            lockVanillaHealth(player);
        }

        enforceLethalRule(player);
    }

    public void handleBodyPartStateChange(BodyPartStateChangeEvent event) {
        Player player = event.getPlayer();
        if (!shouldControl(player)) {
            forgetPlayer(player);
            return;
        }

        BodyPart part = event.getBodyPart();
        BodyPartState newState = event.getNewState();

        if (LETHAL_PARTS.contains(part) && newState == BodyPartState.BROKEN) {
            killPlayer(player, part.name() + " reached BROKEN");
            return;
        }

        if (!BLEEDING_PARTS.contains(part)) {
            return;
        }

        updateBrokenLimb(player.getUniqueId(), part, newState == BodyPartState.BROKEN);
    }

    public void handleBodyPartHealthChange(BodyPartHealthChangeEvent event) {
        Player player = event.getPlayer();
        if (!shouldControl(player)) {
            forgetPlayer(player);
            return;
        }

        if (LETHAL_PARTS.contains(event.getBodyPart()) && event.getNewHealth() <= 0.0D) {
            killPlayer(player, event.getBodyPart().name() + " health reached 0%");
        }
    }

    public void handlePlayerDeath(Player player) {
        brokenLimbs.remove(player.getUniqueId());
        addonOwnedDeaths.remove(player.getUniqueId());
    }

    public void handlePlayerRespawn(Player player) {
        brokenLimbs.remove(player.getUniqueId());
        addonOwnedDeaths.remove(player.getUniqueId());
    }

    public void forgetPlayer(Player player) {
        brokenLimbs.remove(player.getUniqueId());
        addonOwnedDeaths.remove(player.getUniqueId());
    }

    public void lockVanillaHealth(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }

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

    private void ensureBodyHealthCompatibility() {
        if (config == null || !config.autoDisableBodyHealthHealOnFullHealth()) {
            return;
        }

        JavaPlugin bodyHealthPlugin = bodyHealthApi.getBodyHealthPlugin();
        if (!bodyHealthPlugin.getConfig().getBoolean("heal-on-full-health", true)) {
            return;
        }

        bodyHealthPlugin.getConfig().set("heal-on-full-health", false);
        bodyHealthPlugin.saveConfig();
        addon.getAddonDebug().logRaw("Set BodyHealth config 'heal-on-full-health' to false for RealisticBodyHealth compatibility.");
        bodyHealthApi.reloadBodyHealthPlugin();
    }

    private void restartSyncTask() {
        cancelTask(syncTask);
        if (config == null || !config.strictMode()) {
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

    private void restartBleedingTask() {
        cancelTask(bleedingTask);
        if (config == null || !config.strictMode() || !config.bleeding().enabled()) {
            return;
        }

        long interval = config.bleeding().intervalTicks();
        bleedingTask = addon.getBodyHealthPlugin().getServer().getScheduler().runTaskTimer(
                addon.getBodyHealthPlugin(),
                this::runBleedingTick,
                interval,
                interval
        );
    }

    private void runBleedingTick() {
        if (config == null || !config.bleeding().enabled()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!shouldProtectVanillaHealth(player)) {
                if (!shouldControl(player)) {
                    forgetPlayer(player);
                }
                continue;
            }

            EnumSet<BodyPart> limbs = brokenLimbs.get(player.getUniqueId());
            if (limbs == null || limbs.isEmpty()) {
                continue;
            }

            if (player.isDead()) {
                continue;
            }

            double torsoDamagePercent = config.bleeding().baseTorsoDamagePercent()
                    + Math.max(0, limbs.size() - 1) * config.bleeding().extraDamagePerStackPercent();
            double maxTorsoHealth = bodyHealthApi.getMaxPartHealth(player, BodyPart.TORSO);
            double torsoDamageAmount = maxTorsoHealth * (torsoDamagePercent / 100.0D);

            showBleedingEffects(player, limbs.size());
            bodyHealthApi.damagePlayerDirectly(player, torsoDamageAmount, BodyPart.TORSO, false, null);
            lockVanillaHealth(player);
            enforceLethalRule(player);
        }
    }

    private void showBleedingEffects(Player player, int stackCount) {
        int particles = config.bleeding().particleCountBase() + Math.max(0, stackCount - 1) * config.bleeding().particleCountPerStack();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.25F);

        player.getWorld().spawnParticle(
                Particle.DUST,
                player.getLocation().add(0.0D, 1.0D, 0.0D),
                particles,
                0.35D,
                0.6D,
                0.35D,
                0.02D,
                dust
        );

        float pitch = Math.min(2.0F, config.bleeding().soundPitchBase() + ((stackCount - 1) * 0.05F));
        player.playSound(player.getLocation(), config.bleeding().sound(), config.bleeding().soundVolume(), pitch);

        Component actionbar = legacySerializer.deserialize(config.bleeding().actionbar());
        player.sendActionBar(actionbar);
    }

    private void enforceLethalRule(Player player) {
        if (!shouldControl(player) || isAddonDeathInProgress(player) || player.isDead()) {
            return;
        }

        if (isBroken(player, BodyPart.HEAD) || bodyHealthApi.getHealth(player, BodyPart.HEAD) <= 0.0D) {
            killPlayer(player, "HEAD reached lethal state");
            return;
        }

        if (isBroken(player, BodyPart.TORSO) || bodyHealthApi.getHealth(player, BodyPart.TORSO) <= 0.0D) {
            killPlayer(player, "TORSO reached lethal state");
        }
    }

    private boolean isBroken(Player player, BodyPart part) {
        return bodyHealthApi.getBodyHealthState(player, part) == BodyPartState.BROKEN;
    }

    private void refreshBrokenLimbState(Player player) {
        EnumSet<BodyPart> activeBrokenLimbs = EnumSet.noneOf(BodyPart.class);
        for (BodyPart part : BLEEDING_PARTS) {
            if (isBroken(player, part)) {
                activeBrokenLimbs.add(part);
            }
        }

        if (activeBrokenLimbs.isEmpty()) {
            brokenLimbs.remove(player.getUniqueId());
            return;
        }

        brokenLimbs.put(player.getUniqueId(), activeBrokenLimbs);
    }

    private void updateBrokenLimb(UUID playerId, BodyPart part, boolean broken) {
        EnumSet<BodyPart> limbs = brokenLimbs.computeIfAbsent(playerId, ignored -> EnumSet.noneOf(BodyPart.class));
        if (broken) {
            limbs.add(part);
        } else {
            limbs.remove(part);
        }

        if (limbs.isEmpty()) {
            brokenLimbs.remove(playerId);
        }
    }

    private void killPlayer(Player player, String reason) {
        if (isAddonDeathInProgress(player) || player.isDead()) {
            return;
        }

        addonOwnedDeaths.add(player.getUniqueId());
        addon.getAddonDebug().logRaw("Killing " + player.getName() + " because " + reason + ".");

        if (player.getHealth() > 0.0D) {
            player.setHealth(0.0D);
        } else {
            player.damage(1_000_000.0D);
        }
    }

    private boolean isAddonDeathInProgress(Player player) {
        return addonOwnedDeaths.contains(player.getUniqueId());
    }

    private boolean hasBypass(Player player) {
        if (player.hasPermission(ADDON_BYPASS_PERMISSION)) {
            return true;
        }

        if (config == null || !config.respectBodyHealthBypass()) {
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

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
