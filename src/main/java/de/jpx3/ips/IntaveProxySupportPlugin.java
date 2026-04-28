package de.jpx3.ips;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;

import de.jpx3.ips.config.ConfigurationService;
import de.jpx3.ips.connect.bukkit.MessengerService;
import de.jpx3.ips.connect.database.DatabaseService;
import de.jpx3.ips.punish.PunishmentService;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Plugin(
        id = "intave",
        name = "IntaveProxySupport",
        version = "1.0.1",
        authors = {"jpx3"}
)
public final class IntaveProxySupportPlugin {

  private static IntaveProxySupportPlugin singletonInstance;

  private final ProxyServer server;
  private final Logger logger;

  private final Executor executor = Executors.newSingleThreadExecutor();
  private DatabaseService databaseService;
  private MessengerService messengerService;
  private PunishmentService punishmentService;

  @Inject
  public IntaveProxySupportPlugin(ProxyServer server, Logger logger) {
    this.server = server;
    this.logger = logger;
  }

  @Subscribe
  public void onEnable(ProxyInitializeEvent event) {
    singletonInstance = this;

    loadServices();
    enableServices();
  }

  @Subscribe
  public void onDisable(ProxyShutdownEvent event) {
    disableServices();
  }

  private void loadServices() {
    ConfigurationService configurationService = ConfigurationService.createFrom(this);
    ConfigurationNode configuration = configurationService.configuration();

    ConfigurationNode connectionSection = configuration.node("connection");

    databaseService = DatabaseService.createFrom(this, connectionSection.node("sql"), executor);

    messengerService = MessengerService.createFrom(this, connectionSection.node("bukkit"));

    punishmentService = PunishmentService.createFrom(this, configuration.node("punishment"));
  }

  private void enableServices() {
    databaseService.tryConnection();
    messengerService.setup();
    punishmentService.setup();
  }

  private void disableServices() {
    messengerService.closeChannel();
    databaseService.closeConnection();
  }

  public MessengerService messengerService() {
    return messengerService;
  }

  public PunishmentService punishmentService() {
    return punishmentService;
  }

  public DatabaseService databaseService() {
    return databaseService;
  }

  public static IntaveProxySupportPlugin singletonInstance() {
    return singletonInstance;
  }

  public ProxyServer server() {
    return server;
  }

  public Logger logger() {
    return logger;
  }
}