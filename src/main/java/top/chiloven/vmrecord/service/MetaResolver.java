package top.chiloven.vmrecord.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.slf4j.Logger;
import top.chiloven.vmrecord.model.PlayerMetaSnapshot;

public final class MetaResolver {

    private final ProxyServer server;
    private final Logger logger;
    private volatile boolean luckPermsUnavailableLogged;

    public MetaResolver(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public PlayerMetaSnapshot resolve(Player player) {
        if (player == null || !server.getPluginManager().isLoaded("luckperms")) {
            return PlayerMetaSnapshot.empty();
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            CachedMetaData metaData = luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
            return new PlayerMetaSnapshot(
                    metaData.getPrefix() == null ? "" : metaData.getPrefix(),
                    metaData.getSuffix() == null ? "" : metaData.getSuffix()
            );
        } catch (Throwable throwable) {
            if (!luckPermsUnavailableLogged) {
                luckPermsUnavailableLogged = true;
                logger.warn("Failed to resolve LuckPerms metadata, prefix/suffix fields will be empty", throwable);
            }
            return PlayerMetaSnapshot.empty();
        }
    }

}
