package com.catredpacket;

import com.catredpacket.types.RedPacketType;
import org.bukkit.entity.Player;

import java.util.*;

public class PacketHolder {
    private final UUID id = UUID.randomUUID();
    private final RedPacketType type;
    private final UUID sender;
    private final Set<UUID> claimed = new HashSet<>();

    public PacketHolder(RedPacketType type, UUID sender) {
        this.type = type;
        this.sender = sender;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSender() {
        return sender;
    }

    /**
     * 尝试抢红包
     * @return 金额 或 null 表示无法领取
     */
    public synchronized Double grab(Player player, String input) {
        if (claimed.contains(player.getUniqueId()) || !type.canGrab(player, input))
            return null;
        double amt = type.nextAmount();
        claimed.add(player.getUniqueId());
        return amt;
    }

    /** 是否已完全领取 */
    public boolean isEmpty() {
        return type.isEmpty();
    }

    /** 剩余总额 */
    public double getRemainingAmount() {
        return type.getRemainingAmount();
    }

    public RedPacketType getType() {
        return type;
    }
}
