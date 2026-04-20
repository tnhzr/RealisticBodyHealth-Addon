package onlybodyhealth;

import bodyhealth.api.addons.AddonInfo;
import bodyhealth.api.addons.BodyHealthAddon;
import onlybodyhealth.config.OnlyBodyHealthConfig;
import onlybodyhealth.listener.StrictHealthListener;
import onlybodyhealth.service.StrictHealthService;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

@AddonInfo(
        name = "OnlyBodyHealth",
        description = "Disables vanilla HP gameplay and routes survivability to BodyHealth body parts only.",
        version = "1.0.0",
        author = "Islam"
)
public final class OnlyBodyHealthAddon extends BodyHealthAddon {

    private StrictHealthService strictHealthService;

    @Override
    public void onAddonEnable() {
        OnlyBodyHealthConfig config = loadAddonConfig();
        strictHealthService = new StrictHealthService(this);
        strictHealthService.reload(config);
        registerListener(new StrictHealthListener(strictHealthService));
        strictHealthService.syncOnlinePlayers();
    }

    @Override
    public void onAddonReload() {
        if (strictHealthService == null) {
            return;
        }

        strictHealthService.reload(loadAddonConfig());
        strictHealthService.syncOnlinePlayers();
    }

    @Override
    public void onAddonDisable() {
        if (strictHealthService != null) {
            strictHealthService.shutdown();
        }

        unregisterListeners();
    }

    private OnlyBodyHealthConfig loadAddonConfig() {
        getAddonFileManager().saveResource("config.yml", false);

        File configFile = getAddonFileManager().getFile("config.yml");
        getAddonFileManager().updateYamlFile("config.yml", configFile);

        FileConfiguration configuration = getAddonFileManager().getYamlConfiguration("config.yml");
        return OnlyBodyHealthConfig.from(configuration);
    }
}
