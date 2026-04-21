package realisticbodyhealth.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import realisticbodyhealth.service.CombatStateService;
import realisticbodyhealth.service.HealingService;

public final class HealingListener implements Listener {

    private final CombatStateService combatStateService;
    private final HealingService healingService;

    public HealingListener(CombatStateService combatStateService, HealingService healingService) {
        this.combatStateService = combatStateService;
        this.healingService = healingService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreConsume(PlayerItemConsumeEvent event) {
        combatStateService.preBaselineHeartLock(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        healingService.handleConsumedItem(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreInteract(PlayerInteractEvent event) {
        combatStateService.preBaselineHeartLock(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreCommand(PlayerCommandPreprocessEvent event) {
        combatStateService.preBaselineHeartLock(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            healingService.handleVanillaHealing(player, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        healingService.handlePotionSplash(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        healingService.handleAreaEffectCloudApply(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        healingService.forgetPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        healingService.forgetPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        healingService.forgetPlayer(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        healingService.forgetPlayer(event.getPlayer());
    }
}
