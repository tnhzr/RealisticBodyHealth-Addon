package realisticbodyhealth.listener;

import bodyhealth.api.events.BodyPartStateChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import realisticbodyhealth.service.CombatStateService;

public final class CombatStateListener implements Listener {

    private final CombatStateService combatStateService;

    public CombatStateListener(CombatStateService combatStateService) {
        this.combatStateService = combatStateService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageShield(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            combatStateService.prepareVanillaDamageShield(player, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageSync(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!combatStateService.shouldControl(player)
                || combatStateService.shouldAllowVanillaKill(event)
                || event.getFinalDamage() <= 0.0D) {
            return;
        }

        combatStateService.schedulePostDamageSync(player);
    }

    @EventHandler
    public void onBodyPartStateChange(BodyPartStateChangeEvent event) {
        combatStateService.handleBodyPartStateChange(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        combatStateService.scheduleSync(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        combatStateService.handlePlayerRespawn(event.getPlayer());
        combatStateService.scheduleSync(event.getPlayer(), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        combatStateService.scheduleSync(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        combatStateService.handlePlayerDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        combatStateService.forgetPlayer(event.getPlayer());
    }
}
