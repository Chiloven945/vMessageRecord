package top.chiloven.vmrecord.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {

    private final Path configPath;
    private final ObjectMapper mapper;

    public ConfigManager(Path dataDirectory) {
        this.configPath = dataDirectory.resolve("config.yml");
        this.mapper = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public PluginConfig load() throws IOException {
        ensureDefaultConfig();
        return mapper.readValue(configPath.toFile(), PluginConfig.class);
    }

    private void ensureDefaultConfig() throws IOException {
        if (Files.exists(configPath)) {
            return;
        }
        Files.createDirectories(configPath.getParent());
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (inputStream == null) {
                throw new IOException("Missing bundled config.yml");
            }
            Files.copy(inputStream, configPath);
        }
    }

}
