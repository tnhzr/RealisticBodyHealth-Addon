package realisticbodyhealth.service;

import bodyhealth.api.BodyHealthAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.Event;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;
import realisticbodyhealth.RealisticBodyHealthAddon;
import realisticbodyhealth.config.RealisticBodyHealthConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HealingService {

    private static final long MAGIC_SUPPRESSION_WINDOW_MS = 1500L;

    private final BodyHealthAPI bodyHealthApi;
    private final CombatStateService combatStateService;
    private final Map<UUID, Integer> regenAccumulators;
    private final Map<UUID, Long> suppressedMagicHealUntil;

    private RealisticBodyHealthConfig config;
    private BukkitTask regenerationTask;

    public HealingService(RealisticBodyHealthAddon addon, CombatStateService combatStateService) {
        this.bodyHealthApi = BodyHealthAPI.getInstance();
        this.combatStateService = combatStateService;
        this.regenAccumulators = new ConcurrentHashMap<>();
        this.suppressedMagicHealUntil = new ConcurrentHashMap<>();
    }

    public void reload(RealisticBodyHealthConfig config) {
        this.config = config;
        restartRegenerationTask();
    }

    public void shutdown() {
        if (regenerationTask != null) {
            regenerationTask.cancel();
        }
        regenAccumulators.clear();
        suppressedMagicHealUntil.clear();
    }

    public void handleVanillaHealing(Player player, EntityRegainHealthEvent event) {
        if (!combatStateService.shouldControl(player) || player.isDead() || event.getAmount() <= 0.0D) {
            return;
        }

        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
        if (reason == EntityRegainHealthEvent.RegainReason.MAGIC && !isMagicSuppressed(player)) {
            applyBodyPartHealing(player, event.getAmount(), event);
            event.setAmount(0.0D);
            event.setCancelled(true);
            return;
        }

        blockVanillaHealing(player, event);
    }

    public void handleConsumedItem(Player player, PlayerItemConsumeEvent event) {
        if (!combatStateService.shouldControl(player) || player.isDead()) {
            return;
        }

        double instantHealth = extractInstantHealthFromItem(event.getItem());
        if (instantHealth <= 0.0D) {
            return;
        }

        applyDedicatedInstantHealth(player, instantHealth, event);
    }

    public void handlePotionSplash(org.bukkit.event.entity.PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        double baseInstantHealth = extractInstantHealthAmount(potion.getEffects());
        if (baseInstantHealth <= 0.0D) {
            return;
        }

        for (LivingEntity entity : event.getAffectedEntities()) {
            if (!(entity instanceof Player player) || !combatStateService.shouldControl(player) || player.isDead()) {
                continue;
            }

            double intensity = event.getIntensity(player);
            if (intensity <= 0.0D) {
                continue;
            }

            applyDedicatedInstantHealth(player, baseInstantHealth * intensity, event);
        }
    }

    public void handleAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        double baseInstantHealth = extractInstantHealthFromCloud(cloud);
        if (baseInstantHealth <= 0.0D) {
            return;
        }

        for (LivingEntity entity : event.getAffectedEntities()) {
            if (!(entity instanceof Player player) || !combatStateService.shouldControl(player) || player.isDead()) {
                continue;
            }

            applyDedicatedInstantHealth(player, baseInstantHealth, event);
        }
    }

    public void forgetPlayer(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        regenAccumulators.remove(playerId);
        suppressedMagicHealUntil.remove(playerId);
    }

    private void restartRegenerationTask() {
        if (regenerationTask != null) {
            regenerationTask.cancel();
        }

        if (config == null || !config.strictMode()) {
            return;
        }

        regenerationTask = bodyHealthApi.getBodyHealthPlugin().getServer().getScheduler().runTaskTimer(
                bodyHealthApi.getBodyHealthPlugin(),
                this::runRegenerationTick,
                1L,
                1L
        );
    }

    private void runRegenerationTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!combatStateService.shouldControl(player) || player.isDead() || player.getHealth() <= 0.0D) {
                forgetRegeneration(player);
                continue;
            }

            PotionEffect regeneration = player.getPotionEffect(PotionEffectType.REGENERATION);
            if (regeneration == null) {
                forgetRegeneration(player);
                continue;
            }

            int periodTicks = Math.max(1, 50 / (Math.max(0, regeneration.getAmplifier()) + 1));
            int accumulatedTicks = regenAccumulators.merge(player.getUniqueId(), 1, Integer::sum);
            if (accumulatedTicks < periodTicks) {
                continue;
            }

            regenAccumulators.put(player.getUniqueId(), accumulatedTicks - periodTicks);
            applyBodyPartHealing(player, 1.0D, null);
        }
    }

    private void forgetRegeneration(Player player) {
        if (player != null) {
            regenAccumulators.remove(player.getUniqueId());
        }
    }

    private void applyDedicatedInstantHealth(Player player, double amount, Event sourceEvent) {
        if (amount <= 0.0D) {
            return;
        }

        suppressNextMagicHeal(player);
        applyBodyPartHealing(player, amount, sourceEvent);
    }

    private void applyBodyPartHealing(Player player, double amount, Event sourceEvent) {
        if (amount <= 0.0D) {
            return;
        }

        bodyHealthApi.getBodyHealth(player).regenerateHealth(amount, false, sourceEvent);
        combatStateService.lockVanillaHealth(player);
    }

    private void blockVanillaHealing(Player player, EntityRegainHealthEvent event) {
        event.setAmount(0.0D);
        event.setCancelled(true);
        combatStateService.lockVanillaHealth(player);
    }

    private void suppressNextMagicHeal(Player player) {
        suppressedMagicHealUntil.put(player.getUniqueId(), System.currentTimeMillis() + MAGIC_SUPPRESSION_WINDOW_MS);
    }

    private boolean isMagicSuppressed(Player player) {
        Long suppressedUntil = suppressedMagicHealUntil.get(player.getUniqueId());
        if (suppressedUntil == null) {
            return false;
        }

        if (suppressedUntil < System.currentTimeMillis()) {
            suppressedMagicHealUntil.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    private double extractInstantHealthFromItem(ItemStack item) {
        if (item == null) {
            return 0.0D;
        }

        ItemMeta itemMeta = item.getItemMeta();
        if (!(itemMeta instanceof PotionMeta potionMeta)) {
            return 0.0D;
        }

        List<PotionEffect> effects = new ArrayList<>();
        PotionType basePotionType = potionMeta.hasBasePotionType() ? potionMeta.getBasePotionType() : null;
        if (basePotionType != null) {
            effects.addAll(basePotionType.getPotionEffects());
        }
        effects.addAll(potionMeta.getCustomEffects());
        return extractInstantHealthAmount(effects);
    }

    private double extractInstantHealthFromCloud(AreaEffectCloud cloud) {
        List<PotionEffect> effects = new ArrayList<>();
        PotionType basePotionType = cloud.getBasePotionType();
        if (basePotionType != null) {
            effects.addAll(basePotionType.getPotionEffects());
        }
        effects.addAll(cloud.getCustomEffects());
        return extractInstantHealthAmount(effects);
    }

    private double extractInstantHealthAmount(Collection<PotionEffect> effects) {
        double total = 0.0D;
        for (PotionEffect effect : effects) {
            if (effect.getType() != PotionEffectType.INSTANT_HEALTH) {
                continue;
            }

            total += 4.0D * (1 << Math.max(0, effect.getAmplifier()));
        }
        return total;
    }
}
