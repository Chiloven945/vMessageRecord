package top.chiloven.vmrecord;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;

@Plugin(
        id = "vmessagerecord",
        name = Const.name,
        version = Const.version,
        description = "A plugin for vMessage that records chat history to CSV.",
        authors = {"Chiloven945"},
        dependencies = {
                @Dependency(id = "vmessage"),
                @Dependency(id = "luckperms", optional = true)
        }
)
public class VMessageRecord {

    private static final String LATEST_FILE = "latest.csv";
    private static final DateTimeFormatter TS_FILE = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    @Inject
    private Logger logger;
    @Inject
    private ProxyServer server;
    @Inject
    @DataDirectory
    private Path dataDir;

    private Path latestPath;
    private BufferedWriter writer;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("[vMessageRecord] Initializing {} {}\n{}", Const.name, Const.version,
                """
                                           ##\\      ##\\                                                             #######\\                                                ##\\\s
                                           ###\\    ### |                                                            ##  __##\\                                               ## |
                                ##\\    ##\\ ####\\  #### | ######\\   #######\\  #######\\  ######\\   ######\\   ######\\  ## |  ## | ######\\   #######\\  ######\\   ######\\   ####### |
                                \\##\\  ##  |##\\##\\## ## |##  __##\\ ##  _____|##  _____| \\____##\\ ##  __##\\ ##  __##\\ #######  |##  __##\\ ##  _____|##  __##\\ ##  __##\\ ##  __## |
                                 \\##\\##  / ## \\###  ## |######## |\\######\\  \\######\\   ####### |## /  ## |######## |##  __##< ######## |## /      ## /  ## |## |  \\__|## /  ## |
                                  \\###  /  ## |\\#  /## |##   ____| \\____##\\  \\____##\\ ##  __## |## |  ## |##   ____|## |  ## |##   ____|## |      ## |  ## |## |      ## |  ## |
                                   \\#  /   ## | \\_/ ## |\\#######\\ #######  |#######  |\\####### |\\####### |\\#######\\ ## |  ## |\\#######\\ \\#######\\ \\######  |## |      \\####### |
                                    \\_/    \\__|     \\__| \\_______|\\_______/ \\_______/  \\_______| \\____## | \\_______|\\__|  \\__| \\_______| \\_______| \\______/ \\__|       \\_______|
                                                                                                ##\\   ## |                                                                     \s
                                                                                                \\######  |                                                                     \s
                                                                                                 \\______/                                                                      \s
                        """
        );
        try {
            Files.createDirectories(dataDir);
            latestPath = dataDir.resolve(LATEST_FILE);

            writer = Files.newBufferedWriter(
                    latestPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            writer.write("time,server,player,uuid,prefix,suffix,message");
            writer.newLine();
            writer.flush();

            server.getEventManager().register(this, new RecordListener(this));
            logger.info("[vMessageRecord] Initialized. Logging to {}", latestPath.toAbsolutePath());
        } catch (IOException e) {
            logger.error("[vMessageRecord] Failed to initialize writer: {}", e.getMessage(), e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        rotateOnShutdown();
    }

    private void rotateOnShutdown() {
        synchronized (this) {
            try {
                if (writer != null) {
                    writer.flush();
                    writer.close();
                    writer = null;
                }
            } catch (IOException ignored) {
            }

            if (latestPath == null || !Files.exists(latestPath)) return;

            String ts = java.time.LocalDateTime.now().format(TS_FILE);
            Path rotated = dataDir.resolve(ts + ".csv");
            try {
                try {
                    Files.move(latestPath, rotated, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(latestPath, rotated, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                logger.error("[vMessageRecord] Failed to rotate file: {}", e.getMessage(), e);
            }
        }
    }

    void appendRow(String time, String server, String player, String uuid, String prefix, String suffix, String message) {
        if (writer == null) return;
        synchronized (this) {
            try {
                writer.write(csv(time));
                writer.write(',');
                writer.write(csv(server));
                writer.write(',');
                writer.write(csv(player));
                writer.write(',');
                writer.write(csv(uuid));
                writer.write(',');
                writer.write(csv(prefix));
                writer.write(',');
                writer.write(csv(suffix));
                writer.write(',');
                writer.write(csv(message));
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                logger.error("[vMessageRecord] Failed to write row: {}", e.getMessage(), e);
            }
        }
    }

    private String csv(String s) {
        if (s == null) s = "";
        String quoted = s.replace("\"", "\"\"");
        return "\"" + quoted + "\"";
    }
}
