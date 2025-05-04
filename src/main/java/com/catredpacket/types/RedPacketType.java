package com.catredpacket.types;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface RedPacketType {
    boolean canGrab(Player player, String input);

    double nextAmount();

    Component display(Player sender, UUID id);

    boolean isEmpty();

    double getRemainingAmount();
}
