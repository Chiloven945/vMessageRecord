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
import top.chiloven.vmrecord.bootstrap.VMessageRecordBootstrap;

import java.nio.file.Path;

@Plugin(
        id = "vmessagerecord",
        name = Const.NAME,
        version = Const.VERSION,
        description = "A addon for vMessage that records chat history to database or files.",
        authors = {"Chiloven945"},
        dependencies = {
                @Dependency(id = "vmessage"),
                @Dependency(id = "luckperms", optional = true)
        }
)
public final class VMessageRecord {

    private final VMessageRecordBootstrap bootstrap;

    @Inject
    public VMessageRecord(Logger logger, ProxyServer server, @DataDirectory Path dataDirectory) {
        this.bootstrap = new VMessageRecordBootstrap(logger, server, dataDirectory);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        bootstrap.initialize(this);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        bootstrap.shutdown();
    }

}
