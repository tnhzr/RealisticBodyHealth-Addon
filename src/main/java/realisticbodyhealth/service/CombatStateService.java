package realisticbodyhealth.service;

import bodyhealth.api.BodyHealthAPI;
import bodyhealth.api.events.BodyPartStateChangeEvent;
import bodyhealth.core.BodyPart;
import bodyhealth.core.BodyPartState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
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

public final class CombatStateService {

    private static final String ADDON_BYPASS_PERMISSION = "realisticbodyhealth.bypass";
    private static final Set<BodyPart> BLEEDING_PARTS = EnumSet.of(
            BodyPart.ARM_LEFT,
            BodyPart.ARM_RIGHT,
            BodyPart.LEG_LEFT,
            BodyPart.LEG_RIGHT,
            BodyPart.FOOT_LEFT,
            BodyPart.FOOT_RIGHT
    );
    private static final double DAMAGE_SHIELD_EPSILON = 0.001D;
    private static final double MICRO_ABSORPTION_THRESHOLD = 0.05D;

    private final RealisticBodyHealthAddon addon;
    private final BodyHealthAPI bodyHealthApi;
    private final LegacyComponentSerializer legacySerializer;
    private final Map<UUID, EnumSet<BodyPart>> brokenLimbs;

    private RealisticBodyHealthConfig config;
    private BukkitTask syncTask;
    private BukkitTask bleedingTask;

    public CombatStateService(RealisticBodyHealthAddon addon) {
        this.addon = addon;
        this.bodyHealthApi = BodyHealthAPI.getInstance();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.brokenLimbs = new ConcurrentHashMap<>();
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
    }

    public boolean shouldControl(Player player) {
        return player != null
                && config != null
                && config.strictMode()
                && bodyHealthApi.isSystemEnabled(player)
                && !hasBypass(player);
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

    public void schedulePostDamageSync(Player player) {
        scheduleSync(player, 1L);
    }

    public void syncPlayer(Player player) {
        if (!shouldControl(player)) {
            forgetPlayer(player);
            return;
        }

        refreshBrokenLimbState(player);
        if (!player.isOnline() || player.isDead() || player.getHealth() <= 0.0D) {
            return;
        }

        lockVanillaHealth(player);
    }

    public void prepareVanillaDamageShield(Player player, EntityDamageEvent event) {
        if (!shouldControl(player)
                || player.isDead()
                || event.isCancelled()
                || shouldAllowVanillaKill(event)
                || event.getFinalDamage() <= 0.0D) {
            return;
        }

        double requiredAbsorption = event.getFinalDamage() + DAMAGE_SHIELD_EPSILON;
        if (player.getAbsorptionAmount() < requiredAbsorption) {
            player.setAbsorptionAmount(requiredAbsorption);
        }
    }

    public void preBaselineHeartLock(Player player) {
        if (!shouldControl(player) || player.isDead() || player.getHealth() <= 0.0D) {
            return;
        }

        lockVanillaHealth(player);
    }

    public void handleBodyPartStateChange(BodyPartStateChangeEvent event) {
        Player player = event.getPlayer();
        if (!shouldControl(player)) {
            forgetPlayer(player);
            return;
        }

        BodyPart part = event.getBodyPart();
        if (!BLEEDING_PARTS.contains(part)) {
            return;
        }

        updateBrokenLimb(player.getUniqueId(), part, event.getNewState() == BodyPartState.BROKEN);
    }

    public void handlePlayerDeath(Player player) {
        brokenLimbs.remove(player.getUniqueId());
    }

    public void handlePlayerRespawn(Player player) {
        brokenLimbs.remove(player.getUniqueId());
    }

    public void forgetPlayer(Player player) {
        if (player != null) {
            brokenLimbs.remove(player.getUniqueId());
        }
    }

    public void lockVanillaHealth(Player player) {
        if (!player.isOnline() || player.isDead() || player.getHealth() <= 0.0D) {
            return;
        }

        double maxHealth = player.getMaxHealth();
        if (Math.abs(player.getHealth() - maxHealth) > 0.0001D) {
            player.setHealth(maxHealth);
        }

        double absorption = player.getAbsorptionAmount();
        if ((absorption > 0.0D && absorption < MICRO_ABSORPTION_THRESHOLD)
                || (config.clearAbsorption() && absorption != 0.0D)) {
            player.setAbsorptionAmount(0.0D);
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
            if (!shouldControl(player)) {
                forgetPlayer(player);
                continue;
            }

            EnumSet<BodyPart> limbs = brokenLimbs.get(player.getUniqueId());
            if (limbs == null || limbs.isEmpty() || player.isDead() || player.getHealth() <= 0.0D) {
                continue;
            }

            double maxTorsoHealth = bodyHealthApi.getMaxPartHealth(player, BodyPart.TORSO);
            double torsoHealthPercent = bodyHealthApi.getHealth(player, BodyPart.TORSO);
            double currentTorsoHealth = maxTorsoHealth * (torsoHealthPercent / 100.0D);
            if (currentTorsoHealth <= 0.0D) {
                continue;
            }

            double secondsPerTick = config.bleeding().intervalTicks() / 20.0D;
            double fatalTimeSeconds = Math.max(0.1D, config.bleeding().fatalTimeSecondsSingleStack());
            double baseDamagePerTick = maxTorsoHealth * (secondsPerTick / fatalTimeSeconds);
            double speedMultiplier = 1.0D + Math.max(0, limbs.size() - 1) * config.bleeding().extraSpeedPerStack();
            double torsoDamageAmount = baseDamagePerTick * speedMultiplier;

            if (!config.bleeding().canKill()) {
                double minTorsoHealth = maxTorsoHealth * (config.bleeding().nonLethalMinTorsoHealthPercent() / 100.0D);
                double maxAllowedDamage = Math.max(0.0D, currentTorsoHealth - minTorsoHealth);
                torsoDamageAmount = Math.min(torsoDamageAmount, maxAllowedDamage);
            }

            showBleedingEffects(player, limbs.size());
            if (torsoDamageAmount > 0.0D) {
                bodyHealthApi.damagePlayerDirectly(player, torsoDamageAmount, BodyPart.TORSO, false, null);
            }
            lockVanillaHealth(player);
        }
    }

    private void showBleedingEffects(Player player, int stackCount) {
        int particles = config.bleeding().particleCountBase() + Math.max(0, stackCount - 1) * config.bleeding().particleCountPerStack();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.25F);
        Location origin = player.getLocation().add(0.0D, 1.0D, 0.0D);
        double groundY = findGroundY(origin);
        int trails = Math.max(1, Math.min(6, stackCount + 1));

        for (int i = 0; i < trails; i++) {
            double xOffset = (Math.random() - 0.5D) * 0.45D;
            double zOffset = (Math.random() - 0.5D) * 0.45D;
            double startY = origin.getY() - (Math.random() * 0.35D);
            double endY = Math.max(groundY + 0.05D, startY - (0.9D + Math.random() * 0.7D));
            double step = 0.18D;

            for (double y = startY; y >= endY; y -= step) {
                player.getWorld().spawnParticle(
                        Particle.DUST,
                        origin.getX() + xOffset,
                        y,
                        origin.getZ() + zOffset,
                        1,
                        0.01D,
                        0.01D,
                        0.01D,
                        0.0D,
                        dust
                );
            }

            player.getWorld().spawnParticle(
                    Particle.DUST,
                    origin.getX() + xOffset,
                    endY,
                    origin.getZ() + zOffset,
                    Math.max(2, particles / 6),
                    0.06D,
                    0.01D,
                    0.06D,
                    0.0D,
                    dust
            );
        }

        float pitch = Math.min(2.0F, config.bleeding().soundPitchBase() + ((stackCount - 1) * 0.05F));
        player.playSound(player.getLocation(), config.bleeding().sound(), config.bleeding().soundVolume(), pitch);

        Component actionbar = legacySerializer.deserialize(config.bleeding().actionbar());
        player.sendActionBar(actionbar);
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

    private boolean hasBypass(Player player) {
        if (config != null && config.applyToOperators() && player.isOp()) {
            return false;
        }

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

    private boolean isBroken(Player player, BodyPart part) {
        return bodyHealthApi.getBodyHealthState(player, part) == BodyPartState.BROKEN;
    }

    private double findGroundY(Location origin) {
        Block block = origin.getBlock();
        int minY = origin.getWorld().getMinHeight();

        for (int y = block.getY(); y >= minY; y--) {
            Block current = origin.getWorld().getBlockAt(block.getX(), y, block.getZ());
            if (current.getType().isSolid() && current.getType() != Material.AIR) {
                return y + 1.02D;
            }
        }

        return Math.max(minY, origin.getY() - 1.5D);
    }
}
