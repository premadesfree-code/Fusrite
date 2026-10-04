package me.keano.azurite.modules.totem.listener;

import me.keano.azurite.modules.framework.Module;
import me.keano.azurite.modules.totem.TotemManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

/**
 * Copyright (c) 2025. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class TotemListener extends Module<TotemManager> {

    public TotemListener(TotemManager manager) {
        super(manager);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        // Delay 1 tick to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            if (player.isOnline()) {
                getManager().applyTotemOnJoin(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        getManager().removeTotemOnQuit(e.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent e) {
        getManager().removeTotemOnQuit(e.getPlayer());
    }

    @EventHandler
    public void onDisable(PluginDisableEvent e) {
        if (e.getPlugin() == getInstance()) {
            getManager().removeAllAnimations();
            getManager().saveAllTotemData();
        }
    }
}
