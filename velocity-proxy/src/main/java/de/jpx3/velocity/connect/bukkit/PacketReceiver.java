package de.jpx3.velocity.connect.bukkit;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import de.jpx3.velocity.IntaveVelocityProxySupportPlugin;
import de.jpx3.velocity.connect.AbstractPacket;
import de.jpx3.velocity.connect.bukkit.exceptions.InvalidPacketException;
import de.jpx3.velocity.connect.bukkit.exceptions.ProtocolVersionMismatchException;

import static de.jpx3.velocity.connect.bukkit.MessengerService.*;

public final class PacketReceiver {

  private final IntaveVelocityProxySupportPlugin plugin;
  private final MessengerService messengerService;

  private final MinecraftChannelIdentifier channelIdentifier = MinecraftChannelIdentifier.from(OUTGOING_CHANNEL);

  private PacketReceiver(IntaveVelocityProxySupportPlugin plugin, MessengerService messengerService) {
    this.plugin = plugin;
    this.messengerService = messengerService;
  }

  public void setup() {
    plugin.server().getEventManager().register(plugin, this);
  }

  public void unset() {
    plugin.server().getEventManager().unregisterListener(plugin, this);
  }

  @Subscribe
  public void onPluginMessageReceive(PluginMessageEvent event) {
    if (!event.getIdentifier().equals(channelIdentifier)) {
      return;
    }

    if (!(event.getSource() instanceof Player player)) {
      return;
    }

    boolean isWatchCatPacket = receivePayloadPacket(player, event.getData());

    if (isWatchCatPacket) {
      event.setResult(PluginMessageEvent.ForwardResult.handled());
    }
  }

  public boolean receivePayloadPacket(Player player, byte[] data) {
    ByteArrayDataInput inputData = newByteArrayDataInputFrom(data);

    try {
      String channelName = readChannelName(inputData);
      if (!channelName.equalsIgnoreCase(PROTOCOL_HEADER)) {
        return false;
      }

      int protocolVersion = readProtocolVersion(inputData);
      if (protocolVersion != PROTOCOL_VERSION) {
        throw new ProtocolVersionMismatchException(
                String.format("Invalid protocol version (Ours: %s Packet: %s)", PROTOCOL_VERSION, protocolVersion)
        );
      }

      AbstractPacket constructedPacket = constructPacketFrom(inputData);

      String footer = readFooter(inputData);
      if (!footer.equalsIgnoreCase(PROTOCOL_FOOTER)) {
        throw new InvalidPacketException("Invalid end of packet");
      }

      messengerService.packetSubscriptionService().broadcastPacketToSubscribers(player, constructedPacket);

      return true;

    } catch (IllegalStateException exception) {
      return false;
    } catch (IllegalAccessException | InstantiationException exception) {
      throw new IllegalStateException("Could not handle incoming packet", exception);
    }
  }

  private String readChannelName(ByteArrayDataInput input) {
    return input.readUTF();
  }

  private int readProtocolVersion(ByteArrayDataInput input) {
    return input.readInt();
  }

  private int readPacketIdentifier(ByteArrayDataInput input) {
    return input.readInt();
  }

  private AbstractPacket constructPacketFrom(ByteArrayDataInput input)
          throws InstantiationException, IllegalAccessException {
    int packetId = readPacketIdentifier(input);
    return constructPacketFrom(input, packetId);
  }

  private AbstractPacket constructPacketFrom(ByteArrayDataInput input, int packetId) throws IllegalAccessException, InstantiationException {
    AbstractPacket packet = PacketRegister
            .classOf(packetId)
            .orElseThrow(IllegalStateException::new)
            .newInstance();

    packet.applyFrom(input);
    return packet;
  }

  private String readFooter(ByteArrayDataInput input) {
    return input.readUTF();
  }

  private ByteArrayDataInput newByteArrayDataInputFrom(byte[] byteArray) {
    return ByteStreams.newDataInput(byteArray);
  }

  public static PacketReceiver createFrom(IntaveVelocityProxySupportPlugin plugin, MessengerService messengerService) {
    return new PacketReceiver(plugin, messengerService);
  }
}