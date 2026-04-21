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
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import realisticbodyhealth.RealisticBodyHealthAddon;
import realisticbodyhealth.config.RealisticBodyHealthConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RealisticHealthService {

    private static final String ADDON_BYPASS_PERMISSION = "realisticbodyhealth.bypass";
    private static final int REGEN_TASK_INTERVAL_TICKS = 5;
    private static final int CRITICAL_EFFECT_INTERVAL_TICKS = 5;
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
    private final Map<UUID, Integer> regenerationCounters;
    private final Map<UUID, WorldBorder> warningBorders;

    private RealisticBodyHealthConfig config;
    private BukkitTask syncTask;
    private BukkitTask bleedingTask;
    private BukkitTask regenerationTask;
    private BukkitTask criticalEffectsTask;

    public RealisticHealthService(RealisticBodyHealthAddon addon) {
        this.addon = addon;
        this.bodyHealthApi = BodyHealthAPI.getInstance();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.brokenLimbs = new ConcurrentHashMap<>();
        this.regenerationCounters = new ConcurrentHashMap<>();
        this.warningBorders = new ConcurrentHashMap<>();
    }

    public void reload(RealisticBodyHealthConfig config) {
        this.config = config;
        ensureBodyHealthCompatibility();
        restartSyncTask();
        restartBleedingTask();
        restartRegenerationTask();
        restartCriticalEffectsTask();
    }

    public void shutdown() {
        cancelTask(syncTask);
        cancelTask(bleedingTask);
        cancelTask(regenerationTask);
        cancelTask(criticalEffectsTask);
        brokenLimbs.clear();
        regenerationCounters.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            clearWarningBorder(player);
        }
        warningBorders.clear();
    }

    public boolean shouldControl(Player player) {
        return player != null
                && config != null
                && config.strictMode()
                && bodyHealthApi.isSystemEnabled(player)
                && !hasBypass(player);
    }

    public boolean shouldProtectVanillaHealth(Player player) {
        return shouldControl(player) && !hasBodyHealthOwnedLethalState(player);
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
            clearWarningBorder(player);
            return;
        }

        if (shouldProtectVanillaHealth(player)) {
            lockVanillaHealth(player);
        }

        updateCriticalEffects(player);
    }

    public void handleInternalTorsoDamage(Player player, EntityDamageEvent event) {
        if (!shouldControl(player) || event.isCancelled() || shouldAllowVanillaKill(event)) {
            return;
        }

        if (!shouldRouteDirectlyToTorso(event) || event.getFinalDamage() <= 0.0D) {
            return;
        }

        event.setCancelled(true);
        bodyHealthApi.damagePlayerDirectly(player, event.getFinalDamage(), BodyPart.TORSO, false, event);
        refreshBrokenLimbState(player);
        applyVanillaDamageWindow(player);
        scheduleSync(player, 1L);
    }

    public void suppressExternalVanillaDamage(Player player, EntityDamageEvent event) {
        if (!shouldControl(player) || event.isCancelled() || shouldAllowVanillaKill(event)) {
            return;
        }

        if (shouldRouteDirectlyToTorso(event) || event.getFinalDamage() <= 0.0D) {
            return;
        }

        event.setDamage(0.0D);
        applyVanillaDamageWindow(player);
        scheduleSync(player, 1L);
    }

    public void convertVanillaHealing(Player player, EntityRegainHealthEvent event) {
        if (!shouldControl(player) || event.getAmount() <= 0.0D) {
            return;
        }

        event.setCancelled(true);
        bodyHealthApi.healPlayer(player, (int) Math.max(1L, Math.round(event.getAmount())), false, event);
        scheduleSync(player, 1L);
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
        regenerationCounters.remove(player.getUniqueId());
        clearWarningBorder(player);
    }

    public void handlePlayerRespawn(Player player) {
        brokenLimbs.remove(player.getUniqueId());
        regenerationCounters.remove(player.getUniqueId());
        clearWarningBorder(player);
    }

    public void forgetPlayer(Player player) {
        if (player == null) {
            return;
        }

        brokenLimbs.remove(player.getUniqueId());
        regenerationCounters.remove(player.getUniqueId());
        clearWarningBorder(player);
    }

    public void lockVanillaHealth(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }

        double maxHealth = getVanillaMaxHealth(player);
        if (player.getHealth() != maxHealth) {
            player.setHealth(maxHealth);
        }

        if (config.clearAbsorption() && player.getAbsorptionAmount() != 0.0D) {
            player.setAbsorptionAmount(0.0D);
        }
    }

    public List<PotionEffect> readPotionEffects(PotionMeta potionMeta) {
        List<PotionEffect> effects = new ArrayList<>(potionMeta.getBasePotionType().getPotionEffects());
        effects.addAll(potionMeta.getCustomEffects());
        return effects;
    }

    public void handlePotionEffects(Player player, Collection<PotionEffect> potionEffects, double intensity, Event cause) {
        if (!shouldControl(player) || potionEffects.isEmpty()) {
            return;
        }

        if (player.getHealth() + 0.01D < getVanillaMaxHealth(player)) {
            return;
        }

        int totalInstantHealing = 0;
        for (PotionEffect effect : potionEffects) {
            if (effect.getType().equals(PotionEffectType.INSTANT_HEALTH)) {
                totalInstantHealing += calculateInstantHealthHealing(effect, intensity);
            }
        }

        if (totalInstantHealing <= 0) {
            return;
        }

        bodyHealthApi.healPlayer(player, totalInstantHealing, false, cause);
        scheduleSync(player, 1L);
    }

    public void handleNightSkip(TimeSkipEvent event) {
        if (config == null
                || !config.sleepHealing().enabled()
                || event.getSkipReason() != TimeSkipEvent.SkipReason.NIGHT_SKIP) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isSleeping() || !shouldControl(player)) {
                continue;
            }

            for (BodyPart part : BodyPart.values()) {
                double currentHealth = bodyHealthApi.getHealth(player, part);
                double healedHealth = Math.min(100.0D, currentHealth + config.sleepHealing().healPercentPerPart());
                if (healedHealth > currentHealth) {
                    bodyHealthApi.setHealth(player, part, healedHealth, false, event);
                }
            }

            scheduleSync(player, 1L);
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

    private void restartRegenerationTask() {
        cancelTask(regenerationTask);
        if (config == null || !config.strictMode()) {
            return;
        }

        regenerationTask = addon.getBodyHealthPlugin().getServer().getScheduler().runTaskTimer(
                addon.getBodyHealthPlugin(),
                this::runRegenerationTick,
                REGEN_TASK_INTERVAL_TICKS,
                REGEN_TASK_INTERVAL_TICKS
        );
    }

    private void restartCriticalEffectsTask() {
        cancelTask(criticalEffectsTask);
        if (config == null || !config.strictMode() || !config.criticalEffects().enabled()) {
            return;
        }

        criticalEffectsTask = addon.getBodyHealthPlugin().getServer().getScheduler().runTaskTimer(
                addon.getBodyHealthPlugin(),
                this::runCriticalEffectsTick,
                CRITICAL_EFFECT_INTERVAL_TICKS,
                CRITICAL_EFFECT_INTERVAL_TICKS
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
            if (limbs == null || limbs.isEmpty() || player.isDead()) {
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

    private void runRegenerationTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!shouldControl(player) || player.isDead()) {
                if (player != null) {
                    regenerationCounters.remove(player.getUniqueId());
                }
                continue;
            }

            PotionEffect regeneration = player.getPotionEffect(PotionEffectType.REGENERATION);
            if (regeneration == null) {
                regenerationCounters.remove(player.getUniqueId());
                continue;
            }

            if (player.getHealth() + 0.01D < getVanillaMaxHealth(player)) {
                regenerationCounters.remove(player.getUniqueId());
                continue;
            }

            int periodTicks = Math.max(REGEN_TASK_INTERVAL_TICKS, 50 / (regeneration.getAmplifier() + 1));
            int accumulatedTicks = regenerationCounters.merge(player.getUniqueId(), REGEN_TASK_INTERVAL_TICKS, Integer::sum);
            if (accumulatedTicks < periodTicks) {
                continue;
            }

            int healPulses = accumulatedTicks / periodTicks;
            regenerationCounters.put(player.getUniqueId(), accumulatedTicks % periodTicks);
            bodyHealthApi.healPlayer(player, healPulses, false, null);
            lockVanillaHealth(player);
        }
    }

    private void runCriticalEffectsTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateCriticalEffects(player);
        }
    }

    private void updateCriticalEffects(Player player) {
        if (!shouldControl(player) || player.isDead() || !config.criticalEffects().enabled()) {
            clearWarningBorder(player);
            return;
        }

        boolean bleeding = false;
        EnumSet<BodyPart> limbs = brokenLimbs.get(player.getUniqueId());
        if (limbs != null && !limbs.isEmpty()) {
            bleeding = true;
        }

        double headHealth = bodyHealthApi.getHealth(player, BodyPart.HEAD);
        double torsoHealth = bodyHealthApi.getHealth(player, BodyPart.TORSO);
        boolean criticalHead = headHealth <= config.criticalEffects().headThresholdPercent();
        boolean criticalTorso = torsoHealth <= config.criticalEffects().torsoThresholdPercent();

        if (!criticalHead && !criticalTorso && !bleeding) {
            clearWarningBorder(player);
            return;
        }

        applyWarningBorder(player);
        showCriticalParticles(player, criticalHead, criticalTorso, bleeding);
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

    private void showCriticalParticles(Player player, boolean criticalHead, boolean criticalTorso, boolean bleeding) {
        int particleCount = config.criticalEffects().particleCount();
        Particle.DustOptions headDust = new Particle.DustOptions(Color.fromRGB(180, 10, 10), 1.1F);
        Particle.DustOptions torsoDust = new Particle.DustOptions(Color.fromRGB(120, 0, 0), 1.3F);
        Location base = player.getLocation();

        if (criticalHead) {
            player.getWorld().spawnParticle(
                    Particle.DUST,
                    base.getX(),
                    base.getY() + 1.65D,
                    base.getZ(),
                    particleCount,
                    0.18D,
                    0.08D,
                    0.18D,
                    0.0D,
                    headDust
            );
        }

        if (criticalTorso) {
            player.getWorld().spawnParticle(
                    Particle.DUST,
                    base.getX(),
                    base.getY() + 1.05D,
                    base.getZ(),
                    particleCount + 2,
                    0.22D,
                    0.12D,
                    0.22D,
                    0.0D,
                    torsoDust
            );
        }

        if (bleeding) {
            player.getWorld().spawnParticle(
                    Particle.DAMAGE_INDICATOR,
                    base.getX(),
                    base.getY() + 1.0D,
                    base.getZ(),
                    Math.max(1, particleCount / 2),
                    0.18D,
                    0.18D,
                    0.18D,
                    0.0D
            );
        }
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

    private void applyVanillaDamageWindow(Player player) {
        int maximumNoDamageTicks = player.getMaximumNoDamageTicks();
        if (maximumNoDamageTicks > 0) {
            player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), maximumNoDamageTicks));
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

    private boolean hasBodyHealthOwnedLethalState(Player player) {
        return player != null && (isBroken(player, BodyPart.HEAD) || isBroken(player, BodyPart.TORSO));
    }

    private boolean shouldRouteDirectlyToTorso(EntityDamageEvent event) {
        return !(event instanceof EntityDamageByEntityEvent)
                && !(event instanceof EntityDamageByBlockEvent)
                && event.getCause() != EntityDamageEvent.DamageCause.KILL;
    }

    private boolean isBroken(Player player, BodyPart part) {
        return bodyHealthApi.getBodyHealthState(player, part) == BodyPartState.BROKEN;
    }

    private double getVanillaMaxHealth(Player player) {
        return player.getMaxHealth();
    }

    private int calculateInstantHealthHealing(PotionEffect effect, double intensity) {
        int baseHealing = 4 << Math.max(0, effect.getAmplifier());
        return (int) Math.max(1L, Math.round(baseHealing * Math.max(0.0D, intensity)));
    }

    private void applyWarningBorder(Player player) {
        WorldBorder border = warningBorders.computeIfAbsent(player.getUniqueId(), ignored -> {
            WorldBorder createdBorder = Bukkit.createWorldBorder();
            createdBorder.setDamageAmount(0.0D);
            createdBorder.setDamageBuffer(1_000_000.0D);
            return createdBorder;
        });

        Location location = player.getLocation();
        border.setCenter(location.getX(), location.getZ());
        border.setSize(config.criticalEffects().borderSize());
        border.setWarningDistance(config.criticalEffects().warningDistance());
        player.setWorldBorder(border);
    }

    private void clearWarningBorder(Player player) {
        if (player == null) {
            return;
        }

        if (warningBorders.remove(player.getUniqueId()) != null) {
            player.setWorldBorder(null);
        }
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
