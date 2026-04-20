package realisticbodyhealth;

import bodyhealth.api.addons.AddonInfo;
import bodyhealth.api.addons.BodyHealthAddon;
import org.bukkit.configuration.file.FileConfiguration;
import realisticbodyhealth.config.RealisticBodyHealthConfig;
import realisticbodyhealth.listener.RealisticHealthListener;
import realisticbodyhealth.service.RealisticHealthService;

import java.io.File;

@AddonInfo(
        name = "RealisticBodyHealth",
        description = "Makes BodyHealth lethal through head and torso damage plus bleeding from broken limbs.",
        version = "1.1.0",
        author = "Islam"
)
public final class RealisticBodyHealthAddon extends BodyHealthAddon {

    private RealisticHealthService healthService;

    @Override
    public void onAddonEnable() {
        healthService = new RealisticHealthService(this);
        registerListener(new RealisticHealthListener(healthService));
        healthService.reload(loadAddonConfig());
        healthService.syncOnlinePlayers();
    }

    @Override
    public void onAddonReload() {
        if (healthService == null) {
            return;
        }

        healthService.reload(loadAddonConfig());
        healthService.syncOnlinePlayers();
    }

    @Override
    public void onAddonDisable() {
        if (healthService != null) {
            healthService.shutdown();
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
