package realisticbodyhealth;

import bodyhealth.api.addons.AddonInfo;
import bodyhealth.api.addons.BodyHealthAddon;
import org.bukkit.configuration.file.FileConfiguration;
import realisticbodyhealth.config.RealisticBodyHealthConfig;
import realisticbodyhealth.listener.CombatStateListener;
import realisticbodyhealth.listener.HealingListener;
import realisticbodyhealth.service.CombatStateService;
import realisticbodyhealth.service.HealingService;

import java.io.File;

@AddonInfo(
        name = "RealisticBodyHealth",
        description = "BodyHealth addon with strict heart shielding, bleeding, and controlled body-part healing.",
        version = "1.5.2",
        author = "Islam"
)
public final class RealisticBodyHealthAddon extends BodyHealthAddon {

    private CombatStateService combatStateService;
    private HealingService healingService;

    @Override
    public void onAddonEnable() {
        combatStateService = new CombatStateService(this);
        healingService = new HealingService(this, combatStateService);

        registerListener(new CombatStateListener(combatStateService));
        registerListener(new HealingListener(combatStateService, healingService));

        RealisticBodyHealthConfig config = loadAddonConfig();
        combatStateService.reload(config);
        healingService.reload(config);
        combatStateService.syncOnlinePlayers();
    }

    @Override
    public void onAddonReload() {
        if (combatStateService == null || healingService == null) {
            return;
        }

        RealisticBodyHealthConfig config = loadAddonConfig();
        combatStateService.reload(config);
        healingService.reload(config);
        combatStateService.syncOnlinePlayers();
    }

    @Override
    public void onAddonDisable() {
        if (healingService != null) {
            healingService.shutdown();
        }

        if (combatStateService != null) {
            combatStateService.shutdown();
        }

        unregisterListeners();
    }

    private RealisticBodyHealthConfig loadAddonConfig() {
        getAddonFileManager().saveResource("config.yml", false);

        File configFile = getAddonFileManager().getFile("config.yml");
        getAddonFileManager().updateYamlFile("config.yml", configFile);

        FileConfiguration configuration = getAddonFileManager().getYamlConfiguration("config.yml");
        return RealisticBodyHealthConfig.from(configuration);
    }
}
