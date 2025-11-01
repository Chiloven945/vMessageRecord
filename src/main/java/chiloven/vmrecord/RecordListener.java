package chiloven.vmrecord;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import off.szymon.vMessage.VMessagePlugin;
import off.szymon.vMessage.compatibility.mute.MutePluginCompatibilityProvider;

public class RecordListener {

    private final VMessageRecord record;

    public RecordListener(VMessageRecord record) {
        this.record = record;
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent e) {
        // vMessage 会在自己的监听器里 deny 并转而广播。
        // 这里为了“不记录被禁言消息”，复用 vMessage 的禁言判断。
        Player player = e.getPlayer();
        MutePluginCompatibilityProvider mute = VMessagePlugin.get().getMutePluginCompatibilityProvider();

        mute.isMuted(player).thenAcceptAsync(isMuted -> {
            if (isMuted) return; // 被禁言：不落盘
            String serverName = player.getCurrentServer()
                    .map(s -> s.getServerInfo().getName())
                    .orElse("Unknown");
            record.appendRow(serverName, player.getUsername(), e.getMessage());
        });
    }
}
