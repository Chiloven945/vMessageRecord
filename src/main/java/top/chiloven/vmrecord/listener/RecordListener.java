package top.chiloven.vmrecord.listener;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.PostCommandInvocationEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import off.szymon.vmessage.VMessagePlugin;
import off.szymon.vmessage.cmd.ReplyCommand;
import off.szymon.vmessage.compatibility.mute.MutePluginCompatibilityProvider;
import org.slf4j.Logger;
import top.chiloven.vmrecord.config.PluginConfig;
import top.chiloven.vmrecord.model.PlayerMetaSnapshot;
import top.chiloven.vmrecord.model.RecordEntry;
import top.chiloven.vmrecord.model.RecordType;
import top.chiloven.vmrecord.service.MetaResolver;
import top.chiloven.vmrecord.service.RecordService;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RecordListener {

    private static final String[] MESSAGE_ALIASES = {"message", "msg", "tell", "whisper", "w"};
    private static final String[] REPLY_ALIASES = {"reply", "r"};

    private final ProxyServer server;
    private final Logger logger;
    private final PluginConfig config;
    private final RecordService recordService;
    private final MetaResolver metaResolver;
    private final DateTimeFormatter formatter;
    private final AtomicBoolean missingVMessageWarningLogged = new AtomicBoolean(false);

    public RecordListener(ProxyServer server, Logger logger, PluginConfig config, RecordService recordService, MetaResolver metaResolver) {
        this.server = server;
        this.logger = logger;
        this.config = config;
        this.recordService = recordService;
        this.metaResolver = metaResolver;
        this.formatter = DateTimeFormatter.ofPattern(config.recording.timeFormat);
    }

    @Subscribe(priority = -100)
    public void onPlayerChat(PlayerChatEvent event) {
        if (!config.recording.recordChat) {
            return;
        }

        Player sender = event.getPlayer();
        MutePluginCompatibilityProvider muteProvider = resolveMuteProvider();
        if (muteProvider == null) {
            return;
        }

        muteProvider.isMuted(sender).thenAcceptAsync(isMuted -> {
            if (Boolean.TRUE.equals(isMuted)) {
                return;
            }

            PlayerMetaSnapshot meta = metaResolver.resolve(sender);
            RecordEntry entry = new RecordEntry();
            entry.type = RecordType.CHAT;
            entry.timestamp = now();
            entry.server = sender.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("Unknown");
            entry.senderName = sender.getUsername();
            entry.senderUuid = sender.getUniqueId().toString();
            entry.senderPrefix = meta.prefix();
            entry.senderSuffix = meta.suffix();
            entry.command = "chat";
            entry.message = event.getMessage();
            recordService.submit(entry);
        });
    }

    @Subscribe
    public void onPostCommandInvocation(PostCommandInvocationEvent event) {
        if (!config.recording.recordPrivateMessages) {
            return;
        }
        String rawCommand = event.getCommand();
        if (rawCommand.isBlank()) {
            return;
        }
        ParsedCommand parsed = ParsedCommand.parse(rawCommand);
        if (parsed == null) {
            return;
        }
        String alias = parsed.alias().toLowerCase(Locale.ROOT);
        if (isAlias(alias, MESSAGE_ALIASES)) {
            handleDirectMessage(event.getCommandSource(), alias, parsed.remainder());
        } else if (isAlias(alias, REPLY_ALIASES)) {
            handleReply(event.getCommandSource(), alias, parsed.remainder());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Map<UUID, UUID> replyMap = getVMessageReplyMap();
        replyMap.remove(uuid);
        replyMap.entrySet().removeIf(entry -> entry.getValue().equals(uuid));
    }

    private void handleDirectMessage(CommandSource source, String alias, String remainder) {
        if (remainder == null || remainder.isBlank()) {
            return;
        }

        String[] split = remainder.trim().split("\\s+", 2);
        if (split.length < 2) {
            return;
        }
        String targetName = split[0];
        String message = split[1];
        Player receiver = server.getPlayer(targetName).orElse(null);
        Player senderPlayer = source instanceof Player player ? player : null;
        if (receiver == null) {
            return;
        }

        RecordEntry entry = createPrivateMessageEntry(senderPlayer, receiver, alias, message, source);
        recordService.submit(entry);
    }

    private void handleReply(CommandSource source, String alias, String message) {
        if (!(source instanceof Player sender) || message == null || message.isBlank()) {
            return;
        }
        UUID receiverUuid = getVMessageReplyMap().get(sender.getUniqueId());
        if (receiverUuid == null) {
            return;
        }
        Player receiver = server.getPlayer(receiverUuid).orElse(null);
        if (receiver == null) {
            return;
        }
        RecordEntry entry = createPrivateMessageEntry(sender, receiver, alias, message, source);
        recordService.submit(entry);
    }

    private RecordEntry createPrivateMessageEntry(Player senderPlayer, Player receiver, String alias, String message, CommandSource source) {
        PlayerMetaSnapshot senderMeta = metaResolver.resolve(senderPlayer);
        PlayerMetaSnapshot receiverMeta = metaResolver.resolve(receiver);
        RecordEntry entry = new RecordEntry();
        entry.type = RecordType.PRIVATE_MESSAGE;
        entry.timestamp = now();
        entry.server = senderPlayer != null
                ? senderPlayer.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("Unknown")
                : "Console";
        entry.senderName = senderPlayer != null ? senderPlayer.getUsername() : source.getClass().getSimpleName();
        entry.senderUuid = senderPlayer != null ? senderPlayer.getUniqueId().toString() : "";
        entry.senderPrefix = senderMeta.prefix();
        entry.senderSuffix = senderMeta.suffix();
        entry.receiverName = receiver.getUsername();
        entry.receiverUuid = receiver.getUniqueId().toString();
        entry.receiverPrefix = receiverMeta.prefix();
        entry.receiverSuffix = receiverMeta.suffix();
        entry.command = alias;
        entry.message = message;
        return entry;
    }

    private MutePluginCompatibilityProvider resolveMuteProvider() {
        VMessagePlugin plugin = VMessagePlugin.get();
        if (plugin == null) {
            plugin = server.getPluginManager()
                    .getPlugin("vmessage")
                    .flatMap(container -> container.getInstance()
                            .filter(VMessagePlugin.class::isInstance)
                            .map(VMessagePlugin.class::cast))
                    .orElse(null);
        }

        if (plugin == null) {
            if (missingVMessageWarningLogged.compareAndSet(false, true)) {
                logger.warn("vMessage is not available yet, skipping chat/private-message recording until it finishes loading.");
            }
            return null;
        }

        return plugin.getMutePluginCompatibilityProvider();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, UUID> getVMessageReplyMap() {
        try {
            Field field = ReplyCommand.class.getDeclaredField("repliers");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) {
                return (Map<UUID, UUID>) map;
            }
        } catch (ReflectiveOperationException exception) {
            logger.debug("Failed to access vMessage reply map", exception);
        }
        return Collections.emptyMap();
    }

    private String now() {
        return LocalDateTime.now().format(formatter);
    }

    private boolean isAlias(String alias, String[] aliases) {
        return Arrays.asList(aliases).contains(alias);
    }

    private record ParsedCommand(String alias, String remainder) {

        private static ParsedCommand parse(String rawCommand) {
            String trimmed = rawCommand.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            int firstSpace = trimmed.indexOf(' ');
            if (firstSpace < 0) {
                return new ParsedCommand(trimmed, "");
            }
            return new ParsedCommand(trimmed.substring(0, firstSpace), trimmed.substring(firstSpace + 1).trim());
        }

    }

}
