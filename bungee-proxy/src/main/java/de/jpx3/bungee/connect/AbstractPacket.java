package de.jpx3.bungee.connect;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public abstract class AbstractPacket {

  public abstract void applyFrom(ByteArrayDataInput input) throws IllegalStateException;

  public abstract void applyTo(ByteArrayDataOutput output);
}
