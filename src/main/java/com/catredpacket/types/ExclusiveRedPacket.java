package com.catredpacket.types;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ExclusiveRedPacket implements RedPacketType {
    private final double amount;
    private final UUID target;
    private boolean claimed = false;

    public ExclusiveRedPacket(double amount, UUID target) {
        this.amount = amount;
        this.target = target;
    }

    @Override
    public boolean canGrab(Player player, String input) {
        return !claimed && player.getUniqueId().equals(target);
    }

    @Override
    public double nextAmount() {
        claimed = true;
        return amount;
    }

    @Override
    public boolean isEmpty() {
        return claimed;
    }

    @Override
    public double getRemainingAmount() {
        return claimed ? 0 : amount;
    }

    @Override
    public Component display(Player sender, UUID id) {
        return MiniMessage.miniMessage().deserialize(
            "<green>[专属红包]</green> " +
            "<yellow>" + sender.getName() + "</yellow> 发了一个专属红包！ " +
            "<click:run_command:'/redpacket grab " + id + "'>" +
              "<hover:show_text:'<gray>仅指定玩家可领取</gray>'>" +
              "<blue><bold>[点我抢]</bold></blue>" +
            "</hover></click>"
        );
    }
}
