package de.jpx3.ips.punish.driver;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import de.jpx3.ips.IntaveProxySupportPlugin;
import de.jpx3.ips.connect.database.DatabaseService;
import de.jpx3.ips.punish.BanEntry;
import de.jpx3.ips.punish.PunishmentDriver;
import de.jpx3.ips.punish.PunishmentService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public final class RemotePunishmentDriver implements PunishmentDriver {

  private static final String TABLE_NAME = "ips_ban_entries";
  private static final String TABLE_SETUP_QUERY = "CREATE TABLE IF NOT EXISTS `%s`.`"+TABLE_NAME+"` ( `EntryId` INT NOT NULL AUTO_INCREMENT , `UniquePlayerId` VARCHAR(36) NOT NULL , `BanExpireTimestamp` BIGINT NOT NULL , `BanReason` VARCHAR(128) NOT NULL , PRIMARY KEY (`EntryId`)) ENGINE = InnoDB;";
  private static final String SELECTION_QUERY = "select * from `"+TABLE_NAME+"` where `"+TABLE_NAME+"`.`UniquePlayerId` = \"%s\"";
  private static final String INSERTION_QUERY = "insert into `"+TABLE_NAME+"` (`EntryId`, `UniquePlayerId`, `BanExpireTimestamp`, `BanReason`) values (NULL, \"%s\", \"%s\", \"%s\")";

  private final IntaveProxySupportPlugin plugin;
  private Cache<UUID, BanEntry> playerBanCache;

  private final DatabaseService service;
  private final boolean useCaches;

  private RemotePunishmentDriver(IntaveProxySupportPlugin plugin, boolean useCaches) {
    this.plugin = plugin;
    this.useCaches = useCaches;
    this.service = plugin.databaseService();
  }

  public void registerEvents() {
    plugin.server().getEventManager().register(plugin, this);
  }

  public void initializeCache() {
    playerBanCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS)
            .build();
  }

  public void setupTableData() {
    if (service.shouldCreateTables()) {
      String tableCreationQuery = String.format(TABLE_SETUP_QUERY, service.database());
      updateQuery(tableCreationQuery);
    }
  }

  @Subscribe
  public void onPlayerLogin(LoginEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();

    BanEntry banEntry = resolveNullableBanInfoBlocking(uuid);

    if (banEntry != null && !banEntry.expired()) {
      String formattedMessage = formatMessageBy(PunishmentService.BAN_LAYOUT_CONFIGURATION_KEY, banEntry);

      event.setResult(LoginEvent.ComponentResult.denied(LegacyComponentSerializer.legacyAmpersand().deserialize(formattedMessage)));
    }
  }

  @Override
  public void kickPlayer(UUID id, String kickMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(kickMessage);

    Player player = getPlayerFrom(id);
    if (player == null) return;

    String formattedMessage = formatMessageBy(PunishmentService.KICK_LAYOUT_CONFIGURATION_KEY, null);

    player.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(formattedMessage));
  }

  @Override
  public void banPlayerTemporarily(UUID id, long endOfBanTimestamp, String banMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(banMessage);

    Player player = getPlayerFrom(id);
    if (player == null) return;

    BanEntry banEntry = BanEntry.builder()
            .withId(id)
            .withEnd(endOfBanTimestamp)
            .withReason(banMessage)
            .build();

    activateBan(banEntry);

    String formattedMessage = formatMessageBy(PunishmentService.BAN_LAYOUT_CONFIGURATION_KEY, banEntry);

    player.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(formattedMessage));
  }

  @Override
  public void banPlayer(UUID id, String banMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(banMessage);

    Player player = getPlayerFrom(id);
    if (player == null) return;

    BanEntry banEntry = BanEntry.builder()
            .withId(id)
            .withReason(banMessage)
            .withAnInfiniteDuration()
            .build();

    activateBan(banEntry);

    String formattedMessage = formatMessageBy(PunishmentService.BAN_LAYOUT_CONFIGURATION_KEY, banEntry);

    player.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(formattedMessage));
  }

  private void activateBan(BanEntry banEntry) {
    UUID id = banEntry.id();

    if (useCaches) {
      setInCache(id, banEntry);
    }

    String formattedInsertionQuery = String.format(INSERTION_QUERY, id.toString(), banEntry.ending(), banEntry.reason());

    updateQuery(formattedInsertionQuery);
  }

  private BanEntry resolveNullableBanInfoBlocking(UUID id) {
    if (useCaches() && isInCache(id)) {
      return getFromCache(id);
    }

    String queryString = String.format(SELECTION_QUERY, id.toString());
    List<Map<String, Object>> mappedResult = findBlockingByQuery(queryString);

    Optional<BanEntry> banSearch = searchActiveBan(id, mappedResult);

    if (!banSearch.isPresent()) return null;

    BanEntry banEntry = banSearch.get();

    if (banEntry.expired()) return null;

    if (useCaches()) setInCache(id, banEntry);

    return banEntry;
  }

  private static final String COLUMN_NAME_EXPIRATION = "BanExpireTimestamp";
  private static final String COLUMN_NAME_REASON = "BanReason";

  private Optional<BanEntry> searchActiveBan(UUID id, List<Map<String, Object>> tableData) {
    for (Map<String, Object> columnData : tableData) {
      long endingOn = (long) columnData.get(COLUMN_NAME_EXPIRATION);

      if (!entryHasExpired(endingOn)) {
        String reason = (String) columnData.get(COLUMN_NAME_REASON);

        return Optional.of(
                BanEntry.builder()
                        .withId(id)
                        .withEnd(endingOn)
                        .withReason(reason)
                        .build()
        );
      }
    }
    return Optional.empty();
  }

  private void updateQuery(String queryCommand) {
    service.getQueryExecutor().update(queryCommand);
  }

  private List<Map<String, Object>> findBlockingByQuery(String searchCommand) {
    return service.getQueryExecutor().findBlocking(searchCommand);
  }

  private boolean entryHasExpired(long entryEnd) {
    return System.currentTimeMillis() > entryEnd;
  }

  private boolean isInCache(UUID id) {
    return getFromCache(id) != null;
  }

  private BanEntry getFromCache(UUID id) {
    return playerBanCache.getIfPresent(id);
  }

  private void setInCache(UUID id, BanEntry banEntry) {
    playerBanCache.put(id, banEntry);
  }

  private boolean useCaches() {
    return useCaches;
  }

  private Player getPlayerFrom(UUID uuid) {
    return plugin.server().getPlayer(uuid).orElse(null);
  }

  private String formatMessageBy(String configurationKey, BanEntry banEntry) {
    return plugin.punishmentService().resolveMessageBy(configurationKey, banEntry);
  }

  public static RemotePunishmentDriver createWithCachingEnabled(IntaveProxySupportPlugin plugin) {
    RemotePunishmentDriver driver = new RemotePunishmentDriver(plugin, true);
    driver.setupTableData();
    driver.registerEvents();
    driver.initializeCache();
    return driver;
  }

  public static RemotePunishmentDriver createWithCachingDisabled(IntaveProxySupportPlugin plugin) {
    RemotePunishmentDriver driver = new RemotePunishmentDriver(plugin, false);
    driver.setupTableData();
    driver.registerEvents();
    driver.initializeCache();
    return driver;
  }
}