package de.jpx3.bungee;

import de.jpx3.bungee.connect.MessengerService;
import de.jpx3.common.config.ConfigurationService;
import de.jpx3.common.connect.DatabaseService;
import de.jpx3.common.punish.PunishmentService;
import net.md_5.bungee.api.plugin.Plugin;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class IntaveBungeeProxySupportPlugin extends Plugin {

  private static IntaveBungeeProxySupportPlugin singletonInstance;

  private final Executor executor = Executors.newSingleThreadExecutor();
  private DatabaseService databaseService;
  private MessengerService messengerService;
  private PunishmentService punishmentService;

  @Override
  public void onEnable() {
    singletonInstance = this;

    loadServices();
    enableServices();
  }

  @Override
  public void onDisable() {
    disableServices();
  }

  private void loadServices() {
    ConfigurationService configurationService = ConfigurationService.createFrom(getDataFolder(), "config.yml");

    ConfigurationNode configuration = configurationService.configuration();

    ConfigurationNode connectionSection = configuration.node("connection");

    databaseService = DatabaseService.createFrom(connectionSection.node("sql"), executor);

    messengerService = MessengerService.createFrom(this, connectionSection.node("bukkit"));

    punishmentService = PunishmentService.createFrom(configuration.node("punishment"));
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

  public static IntaveBungeeProxySupportPlugin singletonInstance() {
    return singletonInstance;
  }
}