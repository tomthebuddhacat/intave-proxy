package de.jpx3.ips.punish.driver;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import de.jpx3.ips.IntaveProxySupportPlugin;
import de.jpx3.ips.punish.BanEntry;
import de.jpx3.ips.punish.PunishmentDriver;
import de.jpx3.ips.punish.PunishmentService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.UUID;

public final class RuntimePunishmentDriver implements PunishmentDriver {

  private final Map<UUID, BanEntry> bannedPlayers = Maps.newHashMap();
  private final IntaveProxySupportPlugin plugin;

  private RuntimePunishmentDriver(IntaveProxySupportPlugin plugin) {
    this.plugin = plugin;
  }

  public void registerEvents() {
    plugin.server().getEventManager().register(plugin, this);
  }

  @Subscribe
  public void onPlayerLogin(LoginEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();

    if (bannedPlayers.containsKey(playerId)) {
      BanEntry banEntry = bannedPlayers.get(playerId);

      if (!banEntry.expired()) {
        String formattedMessage = formatMessageBy(PunishmentService.KICK_LAYOUT_CONFIGURATION_KEY, null);

        event.setResult(LoginEvent.ComponentResult.denied(LegacyComponentSerializer.legacyAmpersand().deserialize(formattedMessage)));
      }
    }
  }

  @Override
  public void kickPlayer(UUID id, String kickMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(kickMessage);

    Player player = getPlayerFrom(id);
    if (player == null) return;

    String formattedMessage = formatMessageBy(
            PunishmentService.KICK_LAYOUT_CONFIGURATION_KEY,
            BanEntry.builder()
                    .withId(id)
                    .withReason(kickMessage)
                    .withAnInfiniteDuration()
                    .build()
    );

    player.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(formattedMessage));
  }

  @Override
  public void banPlayerTemporarily(UUID id, long endOfBanTimestamp, String banMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(banMessage);

    Player player = getPlayerFrom(id);
    if (player == null) return;

    BanEntry banEntry = BanEntry.builder()
            .withReason(banMessage)
            .withId(id)
            .withEnd(endOfBanTimestamp)
            .build();

    bannedPlayers.put(id, banEntry);

    String formattedMessage = formatMessageBy(
            PunishmentService.BAN_LAYOUT_CONFIGURATION_KEY,
            banEntry
    );

    player.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(formattedMessage));
  }

  @Override
  public void banPlayer(UUID id, String banMessage) {
    Preconditions.checkNotNull(id);
    Preconditions.checkNotNull(banMessage);

    Player player = getPlayerFrom(id);
    if (player == null) return;

    BanEntry banEntry = BanEntry.builder()
            .withReason(banMessage)
            .withId(id)
            .withAnInfiniteDuration()
            .build();

    bannedPlayers.put(id, banEntry);

    String formattedMessage = formatMessageBy(PunishmentService.BAN_LAYOUT_CONFIGURATION_KEY, banEntry);

    player.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(formattedMessage));
  }

  private String formatMessageBy(String configurationKey, BanEntry banEntry) {
    return plugin
            .punishmentService()
            .resolveMessageBy(configurationKey, banEntry);
  }

  private Player getPlayerFrom(UUID uuid) {
    return plugin.server().getPlayer(uuid).orElse(null);
  }

  public static RuntimePunishmentDriver createFrom(IntaveProxySupportPlugin plugin) {
    RuntimePunishmentDriver driver = new RuntimePunishmentDriver(plugin);
    driver.registerEvents();
    return driver;
  }
}