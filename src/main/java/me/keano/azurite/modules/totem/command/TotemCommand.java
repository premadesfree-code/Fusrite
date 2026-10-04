package me.keano.azurite.modules.totem.command;

import me.keano.azurite.modules.commands.CommandManager;
import me.keano.azurite.modules.framework.Config;
import me.keano.azurite.modules.framework.commands.Command;
import me.keano.azurite.modules.framework.commands.extra.TabCompletion;
import me.keano.azurite.modules.totem.TotemManager;
import me.keano.azurite.utils.CC;
import me.keano.azurite.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Copyright (c) 2025. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class TotemCommand extends Command {

    public TotemCommand(CommandManager manager) {
        super(manager, "totem");
        this.setPermissible("azurite.totem");
        this.completions.add(new TabCompletion(Arrays.asList("list", "give"), 0));
    }

    @Override
    public List<String> aliases() {
        return Collections.singletonList("totems");
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                CC.t("&7&m-------------------------"),
                CC.t("&cTotem Commands:"),
                CC.t("&e/totem &7- Open the Totem Selection GUI"),
                CC.t("&e/totem list &7- List all available totems"),
                CC.t("&e/totem give <totem> <player> <amount> &7- Give totem items to a player"),
                CC.t("&7&m-------------------------")
        );
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sendMessage(sender, Config.PLAYER_ONLY);
                return;
            }

            Player player = (Player) sender;
            TotemManager totemManager = getInstance().getTotemManager();
            new me.keano.azurite.modules.totem.menu.TotemMenu(
                    getInstance().getMenuManager(), player, totemManager).open();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                if (!sender.hasPermission("azurite.totem.staff")) {
                    sendMessage(sender, Config.INSUFFICIENT_PERM);
                    return;
                }
                listTotems(sender);
                return;

            case "give":
                if (!sender.hasPermission("azurite.totem.staff")) {
                    sendMessage(sender, Config.INSUFFICIENT_PERM);
                    return;
                }
                giveTotem(sender, args);
                return;
        }

        sendUsage(sender);
    }

    private void listTotems(CommandSender sender) {
        TotemManager totemManager = getInstance().getTotemManager();
        sendMessage(sender, CC.t("&7&m-------------------------"));
        sendMessage(sender, CC.t("&c&lAvailable Totems:"));
        for (String name : totemManager.getTotemNames()) {
            TotemManager.Totem totem = totemManager.getTotem(name);
            if (totem != null) {
                sendMessage(sender, CC.t("&e- &c" + totem.getName()));
            }
        }
        sendMessage(sender, CC.t("&7&m-------------------------"));
    }

    private void giveTotem(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendMessage(sender, CC.t("&cUsage: /totem give <totem> <player> <amount>"));
            return;
        }

        String totemName = args[1];
        String playerName = args[2];
        Integer amount = getInt(args[3]);

        TotemManager totemManager = getInstance().getTotemManager();
        TotemManager.Totem totem = totemManager.getTotem(totemName);

        if (totem == null) {
            sendMessage(sender, CC.t("&cTotem '" + totemName + "' not found! Use /totem list to see available totems."));
            return;
        }

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sendMessage(sender, Config.PLAYER_NOT_FOUND.replace("%player%", playerName));
            return;
        }

        if (amount == null || amount <= 0) {
            sendMessage(sender, Config.NOT_VALID_NUMBER.replace("%number%", args[3]));
            return;
        }

        ItemStack item = totem.getItem().clone();
        item.setAmount(Math.min(amount, item.getMaxStackSize()));

        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, item.getMaxStackSize());
            ItemStack toGive = item.clone();
            toGive.setAmount(stackSize);
            ItemUtils.giveItem(target, toGive, target.getLocation());
            remaining -= stackSize;
        }

        target.sendMessage(CC.t("&aYou received &e" + amount + "x &c" + totem.getName() + " Totem&a!"));
        sendMessage(sender, CC.t("&aGave &e" + amount + "x &c" + totem.getName() + " Totem &ato &e" + target.getName() + "&a!"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        TotemManager totemManager = getInstance().getTotemManager();

        if (args.length == 1) {
            return super.tabComplete(sender, args);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[1].toLowerCase();
            return totemManager.getTotemNames().stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return super.tabComplete(sender, args);
    }
}
