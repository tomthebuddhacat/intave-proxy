package de.jpx3.velocity.connect.bukkit;

import com.velocitypowered.api.proxy.Player;
import de.jpx3.velocity.connect.AbstractPacket;

public interface PacketSubscriber<P extends AbstractPacket> {

  void handle(Player sender, P packet);
}
