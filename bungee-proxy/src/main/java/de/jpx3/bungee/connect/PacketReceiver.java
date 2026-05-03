package de.jpx3.bungee.connect;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import de.jpx3.bungee.IntaveBungeeProxySupportPlugin;
import de.jpx3.bungee.connect.bukkit.exceptions.InvalidPacketException;
import de.jpx3.bungee.connect.bukkit.exceptions.ProtocolVersionMismatchException;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import static de.jpx3.bungee.connect.MessengerService.*;


public final class PacketReceiver implements Listener {

  private final IntaveBungeeProxySupportPlugin plugin;
  private final MessengerService messengerService;

  private PacketReceiver(IntaveBungeeProxySupportPlugin plugin, MessengerService messengerService) {
    this.plugin = plugin;
    this.messengerService = messengerService;
  }

  public void setup() {
    plugin.getProxy().getPluginManager().registerListener(plugin, this);
  }

  public void unset() {
    plugin.getProxy().getPluginManager().unregisterListener(this);
  }

  @EventHandler
  public void onPluginMessageReceive(PluginMessageEvent event) {
    if (!event.getTag().equalsIgnoreCase(OUTGOING_CHANNEL)) {
      return;
    }

    if (!(event.getSender() instanceof ProxiedPlayer player)) {
      return;
    }

    boolean isWatchCatPacket = receivePayloadPacket(player, event.getData());

    if (isWatchCatPacket) {
      event.setCancelled(true);
    }
  }

  public boolean receivePayloadPacket(ProxiedPlayer player, byte[] data) {
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

  private AbstractPacket constructPacketFrom(ByteArrayDataInput input, int packetId)
          throws IllegalAccessException, InstantiationException {
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

  public static PacketReceiver createFrom(IntaveBungeeProxySupportPlugin plugin, MessengerService messengerService) {
    return new PacketReceiver(plugin, messengerService);
  }
}