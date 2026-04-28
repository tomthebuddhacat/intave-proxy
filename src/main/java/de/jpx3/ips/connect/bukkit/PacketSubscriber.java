package de.jpx3.ips.connect.bukkit;

import com.velocitypowered.api.proxy.Player;

public interface PacketSubscriber<P extends AbstractPacket> {
  void handle(Player sender, P packet);
}
