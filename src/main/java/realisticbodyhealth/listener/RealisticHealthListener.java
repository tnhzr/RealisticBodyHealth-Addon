package realisticbodyhealth.listener;

import bodyhealth.api.events.BodyPartHealthChangeEvent;
import bodyhealth.api.events.BodyPartStateChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import realisticbodyhealth.service.RealisticHealthService;

public final class RealisticHealthListener implements Listener {

    private final RealisticHealthService healthService;

    public RealisticHealthListener(RealisticHealthService healthService) {
        this.healthService = healthService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!healthService.shouldProtectVanillaHealth(player)) {
            return;
        }

        if (event.isCancelled() || healthService.shouldAllowVanillaKill(event) || event.getFinalDamage() <= 0.0D) {
            return;
        }

        int previousNoDamageTicks = player.getNoDamageTicks();
        event.setCancelled(true);
        player.setLastDamageCause(event);

        healthService.lockVanillaHealth(player);
        healthService.restoreInvulnerabilityFrames(player, previousNoDamageTicks);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!healthService.shouldProtectVanillaHealth(player)) {
            return;
        }

        event.setAmount(0.0D);
        event.setCancelled(true);
        healthService.lockVanillaHealth(player);
    }

    @EventHandler
    public void onBodyPartStateChange(BodyPartStateChangeEvent event) {
        healthService.handleBodyPartStateChange(event);
    }

    @EventHandler
    public void onBodyPartHealthChange(BodyPartHealthChangeEvent event) {
        healthService.handleBodyPartHealthChange(event);
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
