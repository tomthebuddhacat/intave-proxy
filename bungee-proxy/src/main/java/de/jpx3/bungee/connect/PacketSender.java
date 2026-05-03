package de.jpx3.bungee.connect;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.jpx3.bungee.IntaveBungeeProxySupportPlugin;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import static de.jpx3.bungee.connect.MessengerService.*;

public final class PacketSender {

  private final IntaveBungeeProxySupportPlugin plugin;
  private final MessengerService messengerService;

  private PacketSender(IntaveBungeeProxySupportPlugin plugin, MessengerService messengerService) {
    this.plugin = plugin;
    this.messengerService = messengerService;
  }

  public void setup() {
    plugin.getProxy().registerChannel(OUTGOING_CHANNEL);
  }

  public void reset() {
    plugin.getProxy().unregisterChannel(OUTGOING_CHANNEL);
  }

  public void sendPacket(ProxiedPlayer player, AbstractPacket packet) {
    Preconditions.checkNotNull(player);
    Preconditions.checkNotNull(packet);

    messengerService.packetSubscriptionService()
            .broadcastPacketToSubscribers(player, packet);

    player.sendData(OUTGOING_CHANNEL, prepareDataToSend(packet));
  }

  private byte[] prepareDataToSend(AbstractPacket packet) {
    ByteArrayDataOutput byteArrayWrapper = newByteArrayDataOutput();

    pushProtocolHeader(byteArrayWrapper);

    pushProtocolVersion(byteArrayWrapper);

    pushPacketHeader(byteArrayWrapper, packet.getClass());

    pushPacketData(byteArrayWrapper, packet);

    pushProtocolFooter(byteArrayWrapper);

    return byteArrayWrapper.toByteArray();
  }

  private void pushProtocolHeader(ByteArrayDataOutput byteArrayWrapper) {
    byteArrayWrapper.writeUTF(PROTOCOL_HEADER);
  }

  private void pushProtocolVersion(ByteArrayDataOutput byteArrayWrapper) {
    byteArrayWrapper.writeInt(PROTOCOL_VERSION);
  }

  private void pushPacketHeader(ByteArrayDataOutput byteArrayWrapper, Class<? extends AbstractPacket> packetClass) {
    byteArrayWrapper.writeInt(PacketRegister.identifierOf(packetClass));
  }

  private void pushPacketData(ByteArrayDataOutput byteArrayWrapper, AbstractPacket packetToSend) {
    byte[] bytes = serialize(packetToSend);
    byteArrayWrapper.write(bytes);
  }

  private byte[] serialize(AbstractPacket packet) {
    ByteArrayDataOutput dataOutput = newByteArrayDataOutput();
    packet.applyTo(dataOutput);
    return dataOutput.toByteArray();
  }

  private void pushProtocolFooter(ByteArrayDataOutput byteArrayWrapper) {
    byteArrayWrapper.writeUTF(PROTOCOL_FOOTER);
  }

  private ByteArrayDataOutput newByteArrayDataOutput() {
    return ByteStreams.newDataOutput();
  }

  public static PacketSender createFrom(IntaveBungeeProxySupportPlugin plugin, MessengerService messengerService) {
    return new PacketSender(plugin, messengerService);
  }
}