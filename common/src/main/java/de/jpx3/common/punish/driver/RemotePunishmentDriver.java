package de.jpx3.common.punish.driver;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.jpx3.common.connect.DatabaseService;
import de.jpx3.common.punish.BanEntry;
import de.jpx3.common.punish.PunishmentDriver;
import de.jpx3.common.punish.PunishmentService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("UnstableApiUsage")
public final class RemotePunishmentDriver implements PunishmentDriver {

  private static final String TABLE_NAME = "ips_ban_entries";
  private static final String TABLE_SETUP_QUERY = "CREATE TABLE IF NOT EXISTS `%s`.`"+TABLE_NAME+"` ( `EntryId` INT NOT NULL AUTO_INCREMENT , `UniquePlayerId` VARCHAR(36) NOT NULL , `BanExpireTimestamp` BIGINT NOT NULL , `BanReason` VARCHAR(128) NOT NULL , PRIMARY KEY (`EntryId`)) ENGINE = InnoDB;";
  private static final String SELECTION_QUERY = "select * from `"+TABLE_NAME+"` where `"+TABLE_NAME+"`.`UniquePlayerId` = \"%s\"";
  private static final String INSERTION_QUERY = "insert into `"+TABLE_NAME+"` (`EntryId`, `UniquePlayerId`, `BanExpireTimestamp`, `BanReason`) values (NULL, \"%s\", \"%s\", \"%s\")";

  private Cache<UUID, BanEntry> playerBanCache;

  private final DatabaseService service;
  private final PunishmentService punishmentService;
  private final boolean useCaches;

  private RemotePunishmentDriver(DatabaseService service, PunishmentService punishmentService, boolean useCaches) {
    this.service = service;
    this.punishmentService = punishmentService;
    this.useCaches = useCaches;
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

  @Override
  public void kickPlayer(UUID id, String kickMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(kickMessage);
  }

  @Override
  public void banPlayerTemporarily(UUID id, long endOfBanTimestamp, String banMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(banMessage);

    BanEntry banEntry = BanEntry.builder()
            .withId(id)
            .withEnd(endOfBanTimestamp)
            .withReason(banMessage)
            .build();

    activateBan(banEntry);
  }

  @Override
  public void banPlayer(UUID id, String banMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(banMessage);

    BanEntry banEntry = BanEntry.builder()
            .withId(id)
            .withReason(banMessage)
            .withAnInfiniteDuration()
            .build();

    activateBan(banEntry);
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

  private String formatMessageBy(String configurationKey, BanEntry banEntry) {
    return punishmentService.resolveMessageBy(configurationKey, banEntry);
  }

  public static RemotePunishmentDriver createWithCachingEnabled(DatabaseService service, PunishmentService punishmentService) {
    RemotePunishmentDriver driver = new RemotePunishmentDriver(service, punishmentService, true);
    driver.setupTableData();
    driver.initializeCache();
    return driver;
  }

  public static RemotePunishmentDriver createWithCachingDisabled(DatabaseService service, PunishmentService punishmentService) {
    RemotePunishmentDriver driver = new RemotePunishmentDriver(service, punishmentService, false);
    driver.setupTableData();
    driver.initializeCache();
    return driver;
  }
}