package com.catredpacket;

import com.Zrips.CMI.CMI;
import com.catredpacket.types.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CatRedPacket extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private Economy economy;
    private final Map<UUID, PacketHolder> packets = new HashMap<>();
    private MiniMessage miniMessage;

    @Override
    public void onEnable() {
        miniMessage = MiniMessage.miniMessage();
        if (!setupEconomy()) {
            getLogger().severe("未找到 CMI 或其 Vault 经济支持，插件已禁用");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("已连接到 CMI 经济服务: " + economy.getName());

        PluginCommand cmd = Objects.requireNonNull(getCommand("redpacket"));
        cmd.setExecutor(this);
        cmd.setTabCompleter(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getConsoleSender().sendMessage(
                miniMessage.deserialize("<green>CatRedPacket 插件已启动！</green>")
        );
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("CMI") == null) return false;
        economy = CMI.getInstance()
                .getEconomyManager()
                .getVaultManager()
                .getVaultEconomy();
        return economy != null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (args.length >= 1 && args[0].equalsIgnoreCase("grab")) {
            if (args.length < 2) {
                sendHelp(player);
                return true;
            }
            handleGrab(player, args);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("send")) {
            if (args.length < 4) {
                sendHelp(player);
                return true;
            }
            handleSend(player, args);
            return true;
        }
        sendHelp(player);
        return true;
    }

    private void handleGrab(Player player, String[] args) {
        UUID id;
        try {
            id = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(miniMessage.deserialize("<red>无效的红包ID！</red>"));
            return;
        }
        PacketHolder ph = packets.get(id);
        if (ph == null) {
            player.sendMessage(miniMessage.deserialize("<red>红包不存在或已过期！</red>"));
            return;
        }
        String input = args.length >= 3 ? args[2] : null;
        Double got = ph.grab(player, input);
        if (got == null) {
            player.sendMessage(miniMessage.deserialize("<red>你无法领取此红包！</red>"));
            return;
        }
        economy.depositPlayer(player, got);
        player.sendMessage(miniMessage.deserialize(
                "<green>你抢到了 " + got + " 金币！</green>"
        ));
        if (ph.isEmpty()) {
            packets.remove(id);
            Bukkit.getServer().broadcast(
                    miniMessage.deserialize("<gold>[红包] 红包被抢完啦！</gold>")
            );
        }
    }

    private void handleSend(Player player, String[] args) {
        String type = args[1].toLowerCase(Locale.ROOT);
        PacketHolder holder;
        try {
            switch (type) {
                case "luck":
                    double tL = Double.parseDouble(args[2]);
                    int cL = Integer.parseInt(args[3]);
                    holder = new PacketHolder(new LuckRedPacket(tL, cL), player.getUniqueId());
                    break;
                case "exclusive":
                    double aE = Double.parseDouble(args[2]);
                    Player tgt = Bukkit.getPlayerExact(args[3]);
                    if (tgt == null) throw new IllegalArgumentException("找不到玩家 " + args[3]);
                    holder = new PacketHolder(new ExclusiveRedPacket(aE, tgt.getUniqueId()), player.getUniqueId());
                    break;
                case "keyword":
                    double tK = Double.parseDouble(args[2]);
                    int cK = Integer.parseInt(args[3]);
                    String kw = args[4];
                    holder = new PacketHolder(new KeywordRedPacket(tK, cK, kw), player.getUniqueId());
                    break;
                case "idiom":
                    double tI = Double.parseDouble(args[2]);
                    int cI = Integer.parseInt(args[3]);
                    String st = args[4];
                    holder = new PacketHolder(new IdiomRedPacket(tI, cI, st), player.getUniqueId());
                    break;
                default:
                    throw new IllegalArgumentException("未知类型: " + type);
            }
        } catch (Exception e) {
            player.sendMessage(miniMessage.deserialize("<red>参数错误: " + e.getMessage() + "</red>"));
            return;
        }

        double cost;
        try {
            cost = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(miniMessage.deserialize("<red>金额格式不正确！</red>"));
            return;
        }
        if (!economy.has(player, cost)) {
            player.sendMessage(miniMessage.deserialize("<red>余额不足！</red>"));
            return;
        }
        economy.withdrawPlayer(player, cost);

        UUID id = holder.getId();
        packets.put(id, holder);
        Component comp = holder.getType().display(player, id);
        Bukkit.getServer().broadcast(comp);

        long delay = 60 * 60 * 20L;
        new ExpireTask(id).runTaskLater(this, delay);
    }

    /**
     * 红包过期退回
     */
    private class ExpireTask extends BukkitRunnable {
        private final UUID packetId;

        public ExpireTask(UUID packetId) {
            this.packetId = packetId;
        }

        @Override
        public void run() {
            PacketHolder expired = packets.remove(packetId);
            if (expired == null) return;

            double leftover = expired.getRemainingAmount();
            if (leftover <= 0) return;

            // 将剩余金额退回给发送者
            UUID senderId = expired.getSender();
            OfflinePlayer offline = Bukkit.getOfflinePlayer(senderId);
            economy.depositPlayer(offline, leftover);

            // 在线时通知
            Player onlineSender = Bukkit.getPlayer(senderId);
            if (onlineSender != null) {
                onlineSender.sendMessage(miniMessage.deserialize(
                        "<gold>60 分钟内未领取的红包剩余 "
                                + leftover + " 已退回到你的账户。</gold>"
                ));
            }
        }
    }


    /**
     * 帮助信息，带点击示例
     */
    private void sendHelp(Player p) {
        p.sendMessage(miniMessage.deserialize("<gold>---- /redpacket 使用帮助 ----</gold>"));
        p.sendMessage(miniMessage.deserialize(
                "<aqua>• 发拼手气：</aqua>" +
                        "<click:run_command:'/redpacket send luck 100 5'>" +
                        "<hover:show_text:'示例: 100金 共5份'>" +
                        "<green>/redpacket send luck 100 5</green>" +
                        "</hover></click>"
        ));
        p.sendMessage(miniMessage.deserialize(
                "<aqua>• 发专属：</aqua>" +
                        "<click:run_command:'/redpacket send exclusive 50 Steve'>" +
                        "<hover:show_text:'示例: 50金 专给Steve'>" +
                        "<green>/redpacket send exclusive 50 Steve</green>" +
                        "</hover></click>"
        ));
        p.sendMessage(miniMessage.deserialize(
                "<aqua>• 发口令：</aqua>" +
                        "<click:run_command:'/redpacket send keyword 30 3 happy'>" +
                        "<hover:show_text:'示例: 30金 共3份 口令happy'>" +
                        "<green>/redpacket send keyword 30 3 happy</green>" +
                        "</hover></click>"
        ));
        p.sendMessage(miniMessage.deserialize(
                "<aqua>• 发成语接龙：</aqua>" +
                        "<click:run_command:'/redpacket send idiom 40 4 春风'>" +
                        "<hover:show_text:'示例: 40金 共4份 起始“春风”'>" +
                        "<green>/redpacket send idiom 40 4 春风</green>" +
                        "</hover></click>"
        ));
        p.sendMessage(miniMessage.deserialize(
                "<aqua>• 抢红包：</aqua>" +
                        "<click:run_command:'/redpacket grab <ID> [输入]'>" +
                        "<hover:show_text:'示例: /redpacket grab d290f1ee-6c54-4b01-90e6-d701748f0851 happy'>" +
                        "<green>/redpacket grab <ID> [输入]</green>" +
                        "</hover></click>"
        ));
    }

    /**
     * Tab 补全
     */
    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String alias, String[] args) {
        if (!(s instanceof Player)) return Collections.emptyList();
        List<String> res = new ArrayList<>();

        if (args.length == 1) {
            // 第一参数：send 或 grab
            for (String sub : Arrays.asList("send", "grab")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    res.add(sub);
                }
            }

        } else if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            // 第二参数：红包类型
            for (String type : Arrays.asList("luck", "exclusive", "keyword", "idiom")) {
                if (type.startsWith(args[1].toLowerCase())) {
                    res.add(type);
                }
            }

        } else if (args.length == 4
                && args[0].equalsIgnoreCase("send")
                && args[1].equalsIgnoreCase("exclusive")) {
            // /redpacket send exclusive <amount> <player>
            for (Player online : Bukkit.getOnlinePlayers()) {
                String name = online.getName();
                if (name.toLowerCase().startsWith(args[3].toLowerCase())) {
                    res.add(name);
                }
            }

        } else if (args.length == 5
                && args[0].equalsIgnoreCase("send")
                && args[1].equalsIgnoreCase("keyword")) {
            // /redpacket send keyword <total> <count> <keyword>
            for (String kw : Arrays.asList("happy", "redpacket", "hello")) {
                if (kw.startsWith(args[4].toLowerCase())) {
                    res.add(kw);
                }
            }

        } else if (args.length == 5
                && args[0].equalsIgnoreCase("send")
                && args[1].equalsIgnoreCase("idiom")) {
            // /redpacket send idiom <total> <count> <startIdiom>
            for (String idiom : Arrays.asList("春风", "海阔", "万里")) {
                if (idiom.startsWith(args[4])) {
                    res.add(idiom);
                }
            }
        }

        return res;
    }
}