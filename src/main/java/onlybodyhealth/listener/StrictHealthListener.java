package onlybodyhealth.listener;

import onlybodyhealth.service.StrictHealthService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class StrictHealthListener implements Listener {

    private final StrictHealthService strictHealthService;

    public StrictHealthListener(StrictHealthService strictHealthService) {
        this.strictHealthService = strictHealthService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!strictHealthService.shouldControl(player)) {
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        if (strictHealthService.shouldAllowVanillaKill(event)) {
            return;
        }

        if (event.getFinalDamage() <= 0.0D) {
            return;
        }

        int previousNoDamageTicks = player.getNoDamageTicks();
        event.setCancelled(true);
        player.setLastDamageCause(event);

        strictHealthService.lockVanillaHealth(player);
        strictHealthService.restoreInvulnerabilityFrames(player, previousNoDamageTicks);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!strictHealthService.shouldControl(player)) {
            return;
        }

        event.setAmount(0.0D);
        event.setCancelled(true);
        strictHealthService.lockVanillaHealth(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        strictHealthService.scheduleSync(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        strictHealthService.scheduleSync(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        strictHealthService.scheduleSync(event.getPlayer(), 1L);
    }
}
