package top.chiloven.vmrecord.storage;

import org.slf4j.Logger;
import top.chiloven.vmrecord.config.PluginConfig;
import top.chiloven.vmrecord.storage.csv.CsvRecordStorage;
import top.chiloven.vmrecord.storage.jdbc.JdbcRecordStorage;

import java.nio.file.Path;
import java.util.Locale;

public final class StorageFactory {

    private StorageFactory() {
    }

    public static RecordStorage create(Logger logger, Path dataDirectory, PluginConfig config) {
        String type = config.storage.type == null ? "csv" : config.storage.type.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "csv" -> new CsvRecordStorage(logger, dataDirectory, config);
            case "sqlite", "mysql", "h2", "postgresql" -> new JdbcRecordStorage(logger, dataDirectory, config, type);
            default -> throw new IllegalArgumentException("Unsupported storage type: " + type);
        };
    }

}
