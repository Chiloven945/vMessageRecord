package chiloven.vmrecord;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import off.szymon.vMessage.VMessagePlugin;
import off.szymon.vMessage.compatibility.mute.MutePluginCompatibilityProvider;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RecordListener {

    private static final DateTimeFormatter TS_LINE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final VMessageRecord record;

    public RecordListener(VMessageRecord record) {
        this.record = record;
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent e) {
        Player player = e.getPlayer();
        MutePluginCompatibilityProvider mute = VMessagePlugin.get().getMutePluginCompatibilityProvider();

        mute.isMuted(player).thenAcceptAsync(isMuted -> {
            if (isMuted) return; // 被禁言不记录

            String time = LocalDateTime.now().format(TS_LINE);
            String serverName = player.getCurrentServer()
                    .map(s -> s.getServerInfo().getName())
                    .orElse("Unknown");
            String name = player.getUsername();
            String uuid = player.getUniqueId().toString();

            // LuckPerms 前后缀（如果没有 LP 或拿不到则留空）
            String prefix = "";
            String suffix = "";
            try {
                LuckPerms lp = LuckPermsProvider.get();
                CachedMetaData meta = lp.getPlayerAdapter(Player.class).getMetaData(player);
                if (meta != null) {
                    if (meta.getPrefix() != null) prefix = meta.getPrefix();
                    if (meta.getSuffix() != null) suffix = meta.getSuffix();
                }
            } catch (Throwable ignored) {
                // LuckPerms 未安装或 API 不可用：保持空字符串
            }

            record.appendRow(time, serverName, name, uuid, prefix, suffix, e.getMessage());
        });
    }
}
