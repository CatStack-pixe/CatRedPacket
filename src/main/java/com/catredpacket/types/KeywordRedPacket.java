package com.catredpacket.types;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.*;

public class KeywordRedPacket implements RedPacketType {
    private final List<Double> amounts;
    private final String keyword;

    public KeywordRedPacket(double total, int count, String keyword) {
        int totalCents = (int) Math.round(total * 100);
        amounts = new ArrayList<>(count);
        this.keyword = keyword;
        Random rand = new Random();
        for (int i = 0; i < count - 1; i++) {
            int remainCount = count - i;
            int max = totalCents - (remainCount - 1);
            int take = rand.nextInt(max) + 1;
            amounts.add(take / 100.0);
            totalCents -= take;
        }
        amounts.add(totalCents / 100.0);
    }

    @Override
    public boolean canGrab(Player player, String input) {
        return input != null && input.equals(keyword) && !amounts.isEmpty();
    }

    @Override
    public double nextAmount() {
        return amounts.remove(0);
    }

    @Override
    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    @Override
    public double getRemainingAmount() {
        return amounts.stream().mapToDouble(Double::doubleValue).sum();
    }

    @Override
    public Component display(Player sender, UUID id) {
        return MiniMessage.miniMessage().deserialize(
            "<green>[口令红包]</green> " +
            "<yellow>" + sender.getName() + "</yellow> 发了一个口令红包！ " +
            "<click:run_command:'/redpacket grab " + id + " " + keyword + "'>" +
              "<hover:show_text:'<gray>输入口令领取</gray>'>" +
              "<blue><bold>[点我抢]</bold></blue>" +
            "</hover></click>"
        );
    }
}
