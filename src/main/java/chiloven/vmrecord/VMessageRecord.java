package chiloven.vmrecord;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Plugin(
        id = "vmessagerecord",
        name = "vMessageRecord",
        version = "0.0.1",
        description = "A plugin for vMessage that records chat history to CSV.",
        authors = {"Chiloven945"},
        // 确保在 vMessage 之后加载，这样我们能复用它的禁言判断逻辑
        dependencies = { @Dependency(id = "vmessage") }
)
public class VMessageRecord {

    private static final String LATEST_FILE = "latest.csv";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    @Inject private Logger logger;
    @Inject private ProxyServer server;
    @Inject @DataDirectory private Path dataDir;

    private Path latestPath;
    private BufferedWriter writer;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            // 数据目录
            Files.createDirectories(dataDir);
            latestPath = dataDir.resolve(LATEST_FILE);

            // 每次启动都清空 latest.csv，并写入表头
            writer = Files.newBufferedWriter(
                    latestPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            writer.write("server,player,message");
            writer.newLine();
            writer.flush();

            // 注册监听器
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

    void appendRow(String server, String player, String message) {
        if (writer == null) return;
        synchronized (this) {
            try {
                writer.write(csv(server));
                writer.write(',');
                writer.write(csv(player));
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
        // 简单 CSV 转义：包一层引号并把内部引号双写
        String quoted = s.replace("\"", "\"\"");
        return "\"" + quoted + "\"";
    }

    private void rotateOnShutdown() {
        synchronized (this) {
            try {
                if (writer != null) {
                    writer.flush();
                    writer.close();
                    writer = null;
                }
            } catch (IOException ignored) {}

            if (latestPath == null || !Files.exists(latestPath)) return;

            String ts = LocalDateTime.now().format(TS);
            Path rotated = dataDir.resolve(ts + ".csv");
            try {
                // 尝试原子移动；不支持则退回普通移动
                try {
                    Files.move(latestPath, rotated, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(latestPath, rotated, StandardCopyOption.REPLACE_EXISTING);
                }
                logger.info("[vMessageRecord] Rotated {} -> {}", LATEST_FILE, rotated.getFileName());
            } catch (IOException e) {
                logger.error("[vMessageRecord] Failed to rotate file: {}", e.getMessage(), e);
            }
        }
    }
}
