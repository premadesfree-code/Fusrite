package me.keano.azurite.modules.totem.menu;

import lombok.Getter;
import me.keano.azurite.modules.framework.menu.Menu;
import me.keano.azurite.modules.framework.menu.MenuManager;
import me.keano.azurite.modules.framework.menu.button.Button;
import me.keano.azurite.modules.totem.TotemManager;
import me.keano.azurite.utils.CC;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Copyright (c) 2025. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class TotemMenu extends Menu {

    private final TotemManager totemManager;

    public TotemMenu(MenuManager manager, Player player, TotemManager totemManager) {
        super(manager, player, totemManager.getGuiTitle(), totemManager.getGuiSize(), true);
        this.totemManager = totemManager;
    }

    @Override
    public Map<Integer, Button> getButtons(Player player) {
        Map<Integer, Button> buttons = new HashMap<>();
        Set<Integer> occupiedSlots = new HashSet<>();
        int invSize = totemManager.getGuiSize();

        // Add totem buttons
        for (TotemManager.Totem totem : totemManager.getTotems().values()) {
            int slot = totem.getSlot();
            if (slot < 0 || slot >= invSize) continue;
            occupiedSlots.add(slot);

            boolean active = totem.getName().equalsIgnoreCase(totemManager.getTotemOf(player.getUniqueId()));

            buttons.put(slot + 1, new Button() {
                @Override
                public void onClick(InventoryClickEvent e) {
                    totemManager.equipTotem(player, totem.getName());
                    player.sendMessage(CC.t("&aYou have equipped the &c&l" +
                            totem.getName().substring(0, 1).toUpperCase() + totem.getName().substring(1) +
                            " Totem&a!"));
                    player.closeInventory();
                }

                @Override
                public ItemStack getItemStack() {
                    ItemStack item = totem.getItem().clone();
                    if (active) {
                        ItemMeta meta = item.getItemMeta();
                        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                        lore.add("");
                        lore.add(CC.t("&a&lEQUIPPED"));
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                    return item;
                }
            });
        }

        // Add no-totem-selected or totem-selected item
        boolean hasTotem = totemManager.hasTotem(player.getUniqueId());
        int statusSlot = hasTotem ? totemManager.getTotemSelectedSlot() : totemManager.getNoTotemSlot();

        if (statusSlot >= 0 && statusSlot < invSize) {
            occupiedSlots.add(statusSlot);
            ItemStack statusItem = hasTotem ? totemManager.getTotemSelectedItem() : totemManager.getNoTotemItem();

            buttons.put(statusSlot + 1, new Button() {
                @Override
                public void onClick(InventoryClickEvent e) {
                    if (hasTotem) {
                        totemManager.unequipTotem(player);
                        player.sendMessage(CC.t("&cYour totem has been disabled."));
                        player.closeInventory();
                    }
                }

                @Override
                public ItemStack getItemStack() {
                    return statusItem.clone();
                }
            });
        }

        // Fill decorative items
        for (int i = 0; i < invSize; i++) {
            if (occupiedSlots.contains(i)) continue;
            if (buttons.containsKey(i + 1)) continue;

            List<TotemManager.DecorativeItem> decs = totemManager.getDecorativeForSlot(i, occupiedSlots, invSize);
            if (decs.isEmpty()) continue;

            // Use the first matching decorative item
            TotemManager.DecorativeItem dec = decs.get(0);
            ItemStack decItem = totemManager.createDecorativeItem(dec);
            final int slot = i;

            buttons.put(i + 1, new Button() {
                @Override
                public void onClick(InventoryClickEvent e) {
                }

                @Override
                public ItemStack getItemStack() {
                    return decItem.clone();
                }
            });
        }

        return buttons;
    }
}
