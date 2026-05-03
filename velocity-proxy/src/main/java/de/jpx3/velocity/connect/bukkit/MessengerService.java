package de.jpx3.velocity.connect.bukkit;

import com.google.common.base.Preconditions;
import de.jpx3.velocity.IntaveVelocityProxySupportPlugin;
import org.spongepowered.configurate.ConfigurationNode;

public final class MessengerService {

  public final static int PROTOCOL_VERSION = 5;
  public final static String OUTGOING_CHANNEL = "intave:proxy";
  public final static String PROTOCOL_HEADER = "IPC_BEGIN";
  public final static String PROTOCOL_FOOTER = "IPC_END";

  private final IntaveVelocityProxySupportPlugin plugin;
  private final boolean enabled;

  private PacketSender packetSender;
  private PacketReceiver packetReceiver;
  private PacketSubscriptionService packetSubscriptionService;
  private boolean channelOpen = false;

  private MessengerService(IntaveVelocityProxySupportPlugin plugin, ConfigurationNode configuration) {
    this.plugin = plugin;
    this.enabled = configuration.node("enabled").getBoolean();
  }

  public void setup() {
    packetSender = PacketSender.createFrom(plugin, this);
    packetReceiver = PacketReceiver.createFrom(plugin, this);
    packetSubscriptionService = PacketSubscriptionService.createFrom(plugin);

    if (enabled()) {
      openChannel();
    }
  }

  public void openChannel() {
    if (channelOpen() || !enabled()) {
      throw new IllegalStateException();
    }

    packetSender.setup();
    packetReceiver.setup();
    packetSubscriptionService.setup();
    channelOpen = true;
  }

  public void closeChannel() {
    if (!channelOpen()) {
      throw new IllegalStateException();
    }

    packetSender.reset();
    packetReceiver.unset();
    packetSubscriptionService.reset();
    channelOpen = false;
  }

  public boolean channelOpen() {
    return channelOpen;
  }

  public boolean enabled() {
    return enabled;
  }

  public PacketSender packetSender() {
    return packetSender;
  }

  public PacketReceiver packetReceiver() {
    return packetReceiver;
  }

  public PacketSubscriptionService packetSubscriptionService() {
    return packetSubscriptionService;
  }

  public static MessengerService createFrom(IntaveVelocityProxySupportPlugin proxySupportPlugin, ConfigurationNode configuration) {
    Preconditions.checkNotNull(proxySupportPlugin);
    Preconditions.checkNotNull(configuration);
    return new MessengerService(proxySupportPlugin, configuration);
  }
}