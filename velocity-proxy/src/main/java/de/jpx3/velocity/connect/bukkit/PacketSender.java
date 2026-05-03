package de.jpx3.velocity.connect.bukkit;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import de.jpx3.velocity.IntaveVelocityProxySupportPlugin;
import de.jpx3.velocity.connect.AbstractPacket;

import static de.jpx3.velocity.connect.bukkit.MessengerService.*;

public final class PacketSender {

  private final IntaveVelocityProxySupportPlugin plugin;
  private final MessengerService messengerService;

  private final MinecraftChannelIdentifier channelIdentifier = MinecraftChannelIdentifier.from(OUTGOING_CHANNEL);

  private PacketSender(IntaveVelocityProxySupportPlugin plugin, MessengerService messengerService) {
    this.plugin = plugin;
    this.messengerService = messengerService;
  }

  public void setup() {
    plugin.server().getChannelRegistrar().register(channelIdentifier);
  }

  public void reset() {
    plugin.server().getChannelRegistrar().unregister(channelIdentifier);
  }

  public void sendPacket(Player player, AbstractPacket packet) {
    Preconditions.checkNotNull(player);
    Preconditions.checkNotNull(packet);

    messengerService.packetSubscriptionService()
            .broadcastPacketToSubscribers(player, packet);

    player.sendPluginMessage(channelIdentifier, prepareDataToSend(packet));
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

  public static PacketSender createFrom(IntaveVelocityProxySupportPlugin plugin, MessengerService messengerService) {
    return new PacketSender(plugin, messengerService);
  }
}