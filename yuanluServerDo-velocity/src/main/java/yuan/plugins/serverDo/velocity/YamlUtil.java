package yuan.plugins.serverDo.velocity;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Path;

public class YamlUtil {

    public static ConfigurationNode load(Path path) throws IOException {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(path)
                .build();
        return loader.load();
    }

    public static void save(ConfigurationNode node, Path path) throws IOException {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(path)
                .build();
        loader.save(node);
    }

    public static ConfigurationNode createEmptyNode() {
        return YamlConfigurationLoader.builder().build().createNode();
    }
}