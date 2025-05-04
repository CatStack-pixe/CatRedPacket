package com.catredpacket.types;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.*;

public class IdiomRedPacket implements RedPacketType {
    private final List<Double> amounts;
    private final String startIdiom;
    private String lastChar;
    private final Set<String> used = new HashSet<>();

    public IdiomRedPacket(double total, int count, String startIdiom) {
        int totalCents = (int) Math.round(total * 100);
        amounts = new ArrayList<>(count);
        this.startIdiom = startIdiom;
        this.lastChar = startIdiom.substring(startIdiom.length() - 1);
        used.add(startIdiom);
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
        if (input == null) return false;
        if (used.contains(input)) return false;
        if (!input.startsWith(lastChar)) return false;
        lastChar = input.substring(input.length() - 1);
        used.add(input);
        return !amounts.isEmpty();
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
            "<green>[成语接龙红包]</green> " +
            "<yellow>" + sender.getName() + "</yellow> 开始成语接龙！起始：<gold>" +
            startIdiom + "</gold> " +
            "<click:run_command:'/redpacket grab " + id + " <成语>'>" +
              "<hover:show_text:'<gray>接龙正确可领取</gray>'>" +
              "<blue><bold>[点我抢]</bold></blue>" +
            "</hover></click>"
        );
    }
}
