package de.jpx3.common.punish.driver;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import de.jpx3.common.punish.BanEntry;
import de.jpx3.common.punish.PunishmentDriver;
import de.jpx3.common.punish.PunishmentService;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public final class RuntimePunishmentDriver implements PunishmentDriver {

  private final Map<UUID, BanEntry> bannedPlayers = Maps.newHashMap();
  private final PunishmentService punishmentService;
  private final Function<UUID, Object> playerProvider;
  private final Consumer<Object> disconnectConsumer;

  private RuntimePunishmentDriver(
          PunishmentService punishmentService,
          Function<UUID, Object> playerProvider,
          Consumer<Object> disconnectConsumer
  ) {
    this.punishmentService = punishmentService;
    this.playerProvider = playerProvider;
    this.disconnectConsumer = disconnectConsumer;
  }

  public void onPlayerLogin(UUID playerId, Consumer<String> denyAction) {
    if (bannedPlayers.containsKey(playerId)) {
      BanEntry banEntry = bannedPlayers.get(playerId);

      if (!banEntry.expired()) {
        String formattedMessage = formatMessageBy(
                PunishmentService.KICK_LAYOUT_CONFIGURATION_KEY,
                null
        );
        denyAction.accept(formattedMessage);
      }
    }
  }

  @Override
  public void kickPlayer(UUID id, String kickMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(kickMessage);

    Object player = getPlayerFrom(id);
    if (player == null) return;

    String formattedMessage = formatMessageBy(
            PunishmentService.KICK_LAYOUT_CONFIGURATION_KEY,
            BanEntry.builder()
                    .withId(id)
                    .withReason(kickMessage)
                    .withAnInfiniteDuration()
                    .build()
    );

    disconnectConsumer.accept(player);
  }

  @Override
  public void banPlayerTemporarily(UUID id, long endOfBanTimestamp, String banMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(banMessage);

    Object player = getPlayerFrom(id);
    if (player == null) return;

    BanEntry banEntry = BanEntry.builder()
            .withReason(banMessage)
            .withId(id)
            .withEnd(endOfBanTimestamp)
            .build();

    bannedPlayers.put(id, banEntry);

    disconnectConsumer.accept(player);
  }

  @Override
  public void banPlayer(UUID id, String banMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(banMessage);

    Object player = getPlayerFrom(id);
    if (player == null) return;

    BanEntry banEntry = BanEntry.builder()
            .withReason(banMessage)
            .withId(id)
            .withAnInfiniteDuration()
            .build();

    bannedPlayers.put(id, banEntry);

    disconnectConsumer.accept(player);
  }

  private String formatMessageBy(String configurationKey, BanEntry banEntry) {
    return punishmentService.resolveMessageBy(configurationKey, banEntry);
  }

  private Object getPlayerFrom(UUID uuid) {
    return playerProvider.apply(uuid);
  }

  public static RuntimePunishmentDriver createFrom(PunishmentService punishmentService, Function<UUID, Object> playerProvider, Consumer<Object> disconnectConsumer) {
    Preconditions.checkNotNull(punishmentService);
    Preconditions.checkNotNull(playerProvider);
    Preconditions.checkNotNull(disconnectConsumer);
    return new RuntimePunishmentDriver(punishmentService, playerProvider, disconnectConsumer);
  }
}