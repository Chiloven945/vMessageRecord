package top.chiloven.vmrecord.bootstrap;

import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import top.chiloven.vmrecord.Const;
import top.chiloven.vmrecord.config.ConfigManager;
import top.chiloven.vmrecord.config.PluginConfig;
import top.chiloven.vmrecord.listener.RecordListener;
import top.chiloven.vmrecord.service.MetaResolver;
import top.chiloven.vmrecord.service.RecordService;
import top.chiloven.vmrecord.storage.RecordStorage;
import top.chiloven.vmrecord.storage.StorageFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VMessageRecordBootstrap {

    private final Logger logger;
    private final ProxyServer server;
    private final Path dataDirectory;
    private RecordService recordService;

    public VMessageRecordBootstrap(Logger logger, ProxyServer server, Path dataDirectory) {
        this.logger = logger;
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    public void initialize(Object plugin) {
        logger.info("Initializing {} {}\n{}", Const.NAME, Const.VERSION, """
                        __  __                                _____                        _\s
                       |  \\/  |                              |  __ \\                      | |
                 __   _| \\  / | ___  ___ ___  __ _  __ _  ___| |__) |___  ___ ___  _ __ __| |
                 \\ \\ / / |\\/| |/ _ \\/ __/ __|/ _` |/ _` |/ _ \\  _  // _ \\/ __/ _ \\| '__/ _` |
                  \\ V /| |  | |  __/\\__ \\__ \\ (_| | (_| |  __/ | \\ \\  __/ (_| (_) | | | (_| |
                   \\_/ |_|  |_|\\___||___/___/\\__,_|\\__, |\\___|_|  \\_\\___|\\___\\___/|_|  \\__,_|
                                                    __/ |                                   \s
                                                   |___/                                    \s
                """
        );

        try {
            Files.createDirectories(dataDirectory);

            ConfigManager configManager = new ConfigManager(dataDirectory);
            PluginConfig config = configManager.load();

            RecordStorage storage = StorageFactory.create(logger, dataDirectory, config);
            MetaResolver metaResolver = new MetaResolver(server, logger);
            recordService = new RecordService(logger, config, storage);
            recordService.initialize();

            server.getEventManager().register(plugin, new RecordListener(server, logger, config, recordService, metaResolver));
            logger.info("Initialized with storage type {}", config.storage.type);
        } catch (Exception exception) {
            logger.error("Failed to initialize plugin", exception);
        }
    }

    public void shutdown() {
        if (recordService == null) return;
        try {
            recordService.close();
        } catch (IOException exception) {
            logger.error("Failed to close record service cleanly", exception);
        }
    }

}
