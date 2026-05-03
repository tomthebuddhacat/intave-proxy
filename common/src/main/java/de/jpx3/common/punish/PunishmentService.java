package de.jpx3.common.punish;

import com.google.common.base.Preconditions;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PunishmentService {

  private final ConfigurationNode configuration;

  public final static String BAN_LAYOUT_CONFIGURATION_KEY = "message-layout.ban-layout";
  public final static String KICK_LAYOUT_CONFIGURATION_KEY = "message-layout.kick-layout";

  private PunishmentDriver punishmentDriver;

  private Consumer<String> commandExecutor;
  private BiConsumer<UUID, Object> punishmentPacketHandler;

  private PunishmentService(ConfigurationNode configuration) {
    this.configuration = configuration;
  }

  public void setup() {
    setupPunishmentDriver();
  }

  private void setupPunishmentDriver() {
    String desiredDriverName = desiredPunishmentDriverName();
    this.punishmentDriver = loadDriverFrom(desiredDriverName);
  }

  private final static String DRIVER_NAME_RUNTIME     = "runtime";
  private final static String DRIVER_NAME_SQL_CACHED  = "sql";
  private final static String DRIVER_NAME_SQL_NOCACHE = "sql-nc";

  private PunishmentDriver loadDriverFrom(String driverName) {
    Preconditions.checkNotNull(driverName);

    PunishmentDriver punishmentDriver;

    switch (driverName.toLowerCase()) {
      case DRIVER_NAME_RUNTIME:
        throw new IllegalStateException("Runtime driver must be injected externally");
      case DRIVER_NAME_SQL_CACHED:
        throw new IllegalStateException("SQL cached driver must be injected externally");
      case DRIVER_NAME_SQL_NOCACHE:
        throw new IllegalStateException("SQL no-cache driver must be injected externally");
      default:
        throw new IllegalStateException("Could not find driver " + driverName);
    }
  }

  public void processPunishment(UUID id, String message, PunishmentType type, long endTimestamp) {
    if (message.length() > 64) {
      message = message.substring(0, 64);
    }

    switch (type) {
      case BAN:
        punishmentDriver.banPlayer(id, message);
        break;
      case KICK:
        punishmentDriver.kickPlayer(id, message);
        break;
      case TEMP_BAN:
        punishmentDriver.banPlayerTemporarily(id, endTimestamp, message);
        break;
    }
  }

  public void processCommand(String command) {
    if (commandExecutor != null) {
      commandExecutor.accept(command);
    }
  }

  public String resolveMessageBy(String configurationKey, BanEntry banEntry) {
    String layout = configuration.node(configurationKey.split("\\.")).getString();
    return MessageFormatter.formatMessage(layout, banEntry);
  }

  public PunishmentDriver punishmentDriver() {
    return punishmentDriver;
  }

  public String desiredPunishmentDriverName() {
    return configuration.node("driver").getString("runtime");
  }

  public void setPunishmentDriver(PunishmentDriver punishmentDriver) {
    this.punishmentDriver = punishmentDriver;
  }

  public void setCommandExecutor(Consumer<String> commandExecutor) {
    this.commandExecutor = commandExecutor;
  }

  public static PunishmentService createFrom(ConfigurationNode configuration) {
    return new PunishmentService(configuration);
  }

  public enum PunishmentType {
    BAN,
    KICK,
    TEMP_BAN
  }
}