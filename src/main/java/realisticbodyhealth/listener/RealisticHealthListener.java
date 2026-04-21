package realisticbodyhealth.listener;

import bodyhealth.api.events.BodyPartStateChangeEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.inventory.meta.PotionMeta;
import realisticbodyhealth.service.RealisticHealthService;

public final class RealisticHealthListener implements Listener {

    private final RealisticHealthService healthService;

    public RealisticHealthListener(RealisticHealthService healthService) {
        this.healthService = healthService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInternalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        healthService.handleInternalTorsoDamage(player, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        healthService.suppressExternalVanillaDamage(player, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        healthService.convertVanillaHealing(player, event);
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player player) {
                healthService.handlePotionEffects(player, event.getPotion().getEffects(), event.getIntensity(player), event);
            }
        }
    }

    @EventHandler
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player player) {
                healthService.handlePotionEffects(player, event.getEntity().getCustomEffects(), 1.0D, event);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (!(event.getItem().getItemMeta() instanceof PotionMeta meta)) {
            return;
        }

        healthService.handlePotionEffects(event.getPlayer(), healthService.readPotionEffects(meta), 1.0D, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNightSkip(TimeSkipEvent event) {
        healthService.handleNightSkip(event);
    }

    @EventHandler
    public void onBodyPartStateChange(BodyPartStateChangeEvent event) {
        healthService.handleBodyPartStateChange(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        healthService.scheduleSync(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        healthService.handlePlayerRespawn(event.getPlayer());
        healthService.scheduleSync(event.getPlayer(), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        healthService.scheduleSync(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        healthService.handlePlayerDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        healthService.forgetPlayer(event.getPlayer());
    }
}
