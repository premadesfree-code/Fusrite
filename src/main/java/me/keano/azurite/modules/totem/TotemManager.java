package me.keano.azurite.modules.totem;

import lombok.Getter;
import me.keano.azurite.HCF;
import me.keano.azurite.modules.framework.Manager;
import me.keano.azurite.utils.CC;
import me.keano.azurite.utils.ItemUtils;
import me.keano.azurite.utils.configs.ConfigYML;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copyright (c) 2025. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
@Getter
public class TotemManager extends Manager {

    private final Map<UUID, String> totemData;
    private final Map<UUID, ArmorStand> armorStands;
    private final Map<UUID, BukkitTask> animationTasks;
    private final Map<UUID, Integer> animationTicks;

    private final Map<String, Totem> totems;
    private final List<String> totemNames;

    private ConfigYML totemsConfig;
    private ConfigYML totemDataFile;

    private int guiRows;
    private String guiTitle;
    private ItemStack noTotemItem;
    private int noTotemSlot;
    private ItemStack totemSelectedItem;
    private int totemSelectedSlot;
    private final List<DecorativeItem> decorativeItems;

    public TotemManager(HCF instance) {
        super(instance);
        this.totemData = new ConcurrentHashMap<>();
        this.armorStands = new ConcurrentHashMap<>();
        this.animationTasks = new ConcurrentHashMap<>();
        this.animationTicks = new ConcurrentHashMap<>();
        this.totems = new LinkedHashMap<>();
        this.totemNames = new ArrayList<>();
        this.decorativeItems = new ArrayList<>();
        this.load();
        new me.keano.azurite.modules.totem.listener.TotemListener(this);
    }

    private void load() {
        this.totemsConfig = getTotemsConfig();
        this.totemDataFile = new ConfigYML(getInstance(), "totem-data");

        this.guiRows = Integer.parseInt(totemsConfig.getString("rows"));
        this.guiTitle = CC.t(totemsConfig.getString("title"));

        // Load decorative items
        ConfigurationSection decSection = totemsConfig.getConfigurationSection("decorative-items");
        if (decSection != null) {
            for (String key : decSection.getKeys(false)) {
                String path = "decorative-items." + key;
                String matName = totemsConfig.getString(path + ".material");
                int data = totemsConfig.getInt(path + ".data");
                String name = totemsConfig.getString(path + ".name");
                List<String> lore = totemsConfig.getStringList(path + ".lore");
                String slot = totemsConfig.getString(path + ".slot");
                decorativeItems.add(new DecorativeItem(matName, data, name, lore, slot));
            }
        }

        // Load no-totem-selected-item
        this.noTotemItem = loadItem("no-totem-selected-item");
        this.noTotemSlot = totemsConfig.getInt("no-totem-selected-item.slot");

        // Load totem-selected-item
        this.totemSelectedItem = loadItem("totem-selected-item");
        this.totemSelectedSlot = totemsConfig.getInt("totem-selected-item.slot");

        // Load totems dynamically (any key starting with totem-)
        totems.clear();
        totemNames.clear();
        for (String key : totemsConfig.getKeys(false)) {
            if (!key.startsWith("totem-")) continue;
            if (key.equals("totem-selected-item")) continue;

            String name = key.substring("totem-".length());
            ItemStack item = loadItem(key);
            int slot = totemsConfig.getInt(key + ".slot");
            Totem totem = new Totem(name, item, slot);
            totems.put(name.toLowerCase(), totem);
            totemNames.add(name.toLowerCase());
        }

        // Load saved totem data
        loadTotemData();
    }

    private ItemStack loadItem(String path) {
        String matName = totemsConfig.getString(path + ".material");
        Material material = ItemUtils.getMat(matName);
        int data = totemsConfig.getInt(path + ".data");
        String name = totemsConfig.getString(path + ".name");
        List<String> lore = totemsConfig.getStringList(path + ".lore");
        String texture = totemsConfig.contains(path + ".texture") ? totemsConfig.getUntranslatedString(path + ".texture") : "";

        ItemStack item = new ItemStack(material, 1, (byte) data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(CC.t(name));
        meta.setLore(CC.t(lore));
        item.setItemMeta(meta);

        if (texture != null && !texture.isEmpty() && material == ItemUtils.getMat("PLAYER_HEAD")) {
            applyTexture(item, texture);
        }

        return item;
    }

    private void applyTexture(ItemStack item, String texture) {
        if (texture == null || texture.isEmpty()) return;
        if (!(item.getItemMeta() instanceof SkullMeta)) return;

        try {
            SkullMeta meta = (SkullMeta) item.getItemMeta();

            Object profile = Class.forName("com.mojang.authlib.GameProfile")
                    .getConstructor(UUID.class, String.class)
                    .newInstance(UUID.randomUUID(), "totem");

            Object property = Class.forName("com.mojang.authlib.properties.Property")
                    .getConstructor(String.class, String.class)
                    .newInstance("textures", texture);

            Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
            properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, "textures", property);

            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
            item.setItemMeta(meta);
        } catch (Exception e) {
            // Silently ignore texture errors
        }
    }

    public Totem getTotem(String name) {
        return totems.get(name.toLowerCase());
    }

    public String getTotemOf(UUID uuid) {
        return totemData.get(uuid);
    }

    public boolean hasTotem(UUID uuid) {
        String totem = totemData.get(uuid);
        return totem != null && !totem.isEmpty();
    }

    public void equipTotem(Player player, String totemName) {
        Totem totem = getTotem(totemName);
        if (totem == null) return;

        // Remove old totem animation if switching
        removeAnimation(player);

        totemData.put(player.getUniqueId(), totemName.toLowerCase());
        saveTotemData(player.getUniqueId(), totemName.toLowerCase());

        spawnAnimation(player, totem);
    }

    public void unequipTotem(Player player) {
        removeAnimation(player);
        totemData.remove(player.getUniqueId());
        saveTotemData(player.getUniqueId(), null);
    }

    private void spawnAnimation(Player player, Totem totem) {
        removeAnimation(player);

        Location loc = player.getLocation().clone().add(0, 1.0, 0);

        ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setSmall(true);
        stand.setHelmet(totem.getItem().clone());
        stand.setCustomNameVisible(false);

        armorStands.put(player.getUniqueId(), stand);
        animationTicks.put(player.getUniqueId(), 0);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(getInstance(), () -> {
            if (!player.isOnline() || !player.isValid()) {
                removeAnimation(player);
                return;
            }

            if (!stand.isValid() || stand.isDead()) {
                removeAnimation(player);
                return;
            }

            int tick = animationTicks.getOrDefault(player.getUniqueId(), 0);
            tick++;
            animationTicks.put(player.getUniqueId(), tick);

            double bob = Math.sin(tick * 0.15) * 0.1;
            Location target = player.getLocation().clone().add(0, 1.0 + bob, 0);

            stand.teleport(target);
        }, 0L, 1L);

        animationTasks.put(player.getUniqueId(), task);
    }

    public void removeAnimation(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask task = animationTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        ArmorStand stand = armorStands.remove(uuid);
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }

        animationTicks.remove(uuid);
    }

    public void removeAllAnimations() {
        for (UUID uuid : new ArrayList<>(armorStands.keySet())) {
            ArmorStand stand = armorStands.get(uuid);
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        for (BukkitTask task : animationTasks.values()) {
            task.cancel();
        }
        armorStands.clear();
        animationTasks.clear();
        animationTicks.clear();
    }

    private void loadTotemData() {
        if (totemDataFile.contains("totems")) {
            ConfigurationSection section = totemDataFile.getConfigurationSection("totems");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    String value = totemDataFile.getString("totems." + key);
                    if (value != null && !value.isEmpty() && value.equalsIgnoreCase("none")) {
                        continue;
                    }
                    if (value != null && !value.isEmpty()) {
                        try {
                            UUID uuid = UUID.fromString(key);
                            totemData.put(uuid, value.toLowerCase());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
        }
    }

    private void saveTotemData(UUID uuid, String totemName) {
        if (totemName == null || totemName.isEmpty()) {
            totemDataFile.set("totems." + uuid.toString(), "none");
        } else {
            totemDataFile.set("totems." + uuid.toString(), totemName);
        }
        totemDataFile.save();
    }

    public void saveAllTotemData() {
        for (Map.Entry<UUID, String> entry : totemData.entrySet()) {
            totemDataFile.set("totems." + entry.getKey().toString(), entry.getValue());
        }
        totemDataFile.save();
    }

    public void applyTotemOnJoin(Player player) {
        String totemName = totemData.get(player.getUniqueId());
        if (totemName != null && !totemName.isEmpty()) {
            Totem totem = getTotem(totemName);
            if (totem != null) {
                spawnAnimation(player, totem);
            } else {
                // Totem no longer exists in config, clear it
                totemData.remove(player.getUniqueId());
                saveTotemData(player.getUniqueId(), null);
            }
        }
    }

    public void removeTotemOnQuit(Player player) {
        removeAnimation(player);
    }

    // Perk helper methods

    public boolean isAssassin(UUID uuid) {
        String totem = totemData.get(uuid);
        return totem != null && totem.equalsIgnoreCase("assassin");
    }

    public boolean isRaider(UUID uuid) {
        String totem = totemData.get(uuid);
        return totem != null && totem.equalsIgnoreCase("raider");
    }

    public boolean isTrapper(UUID uuid) {
        String totem = totemData.get(uuid);
        return totem != null && totem.equalsIgnoreCase("trapper");
    }

    /**
     * Returns the cooldown multiplier for ability items.
     * 0.85 = 15% reduction, 1.0 = no reduction.
     */
    public double getAbilityCooldownMultiplier(UUID uuid, String abilityName) {
        String totem = totemData.get(uuid);
        if (totem == null) return 1.0;

        if (totem.equalsIgnoreCase("assassin")) {
            if (abilityName.equalsIgnoreCase("MedKit") || abilityName.equalsIgnoreCase("FocusMode")) {
                return 0.85;
            }
        }

        if (totem.equalsIgnoreCase("raider")) {
            if (abilityName.equalsIgnoreCase("AntiTrapHalo") || abilityName.equalsIgnoreCase("AntiBuild")) {
                return 0.85;
            }
        }

        if (totem.equalsIgnoreCase("trapper")) {
            if (abilityName.equalsIgnoreCase("Switcher") || abilityName.equalsIgnoreCase("SwitchStick")) {
                return 0.85;
            }
        }

        return 1.0;
    }

    /**
     * Returns the cooldown multiplier for gapples.
     * 0.90 = 10% reduction, 1.0 = no reduction.
     */
    public double getGappleMultiplier(UUID uuid) {
        if (isAssassin(uuid)) return 0.90;
        return 1.0;
    }

    /**
     * Returns the cooldown multiplier for enderpearls.
     * 0.90 = 10% reduction, 1.0 = no reduction.
     */
    public double getEnderpearlMultiplier(UUID uuid) {
        if (isRaider(uuid)) return 0.90;
        return 1.0;
    }

    /**
     * Returns true if the player has Trapper totem (immune to SwitchStick).
     */
    public boolean isImmuneToSwitchStick(UUID uuid) {
        return isTrapper(uuid);
    }

    @Override
    public void disable() {
        removeAllAnimations();
        saveAllTotemData();
    }

    public int getGuiSize() {
        return guiRows * 9;
    }

    public List<DecorativeItem> getDecorativeForSlot(int slot, Set<Integer> occupiedSlots, int invSize) {
        List<DecorativeItem> result = new ArrayList<>();
        for (DecorativeItem item : decorativeItems) {
            if (item.matchesSlot(slot, occupiedSlots, invSize)) {
                result.add(item);
            }
        }
        return result;
    }

    public ItemStack createDecorativeItem(DecorativeItem dec) {
        Material material = ItemUtils.getMat(dec.material);
        ItemStack item = new ItemStack(material, 1, (byte) dec.data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(CC.t(dec.name));
        meta.setLore(CC.t(dec.lore));
        item.setItemMeta(meta);
        return item;
    }

    public static class Totem {
        @Getter
        private final String name;
        @Getter
        private final ItemStack item;
        @Getter
        private final int slot;

        public Totem(String name, ItemStack item, int slot) {
            this.name = name;
            this.item = item;
            this.slot = slot;
        }
    }

    public static class DecorativeItem {
        @Getter
        private final String material;
        @Getter
        private final int data;
        @Getter
        private final String name;
        @Getter
        private final List<String> lore;
        @Getter
        private final String slotSpec;

        public DecorativeItem(String material, int data, String name, List<String> lore, String slotSpec) {
            this.material = material;
            this.data = data;
            this.name = name;
            this.lore = lore;
            this.slotSpec = slotSpec;
        }

        public boolean matchesSlot(int slot, Set<Integer> occupiedSlots, int invSize) {
            if (slotSpec == null || slotSpec.isEmpty()) return false;

            if (slotSpec.equalsIgnoreCase("ALL_EMPTY_SLOTS")) {
                return !occupiedSlots.contains(slot);
            }

            // Range format: "18-26"
            if (slotSpec.contains("-")) {
                try {
                    String[] parts = slotSpec.split("-");
                    int start = Integer.parseInt(parts[0].trim());
                    int end = Integer.parseInt(parts[1].trim());
                    return slot >= start && slot <= end;
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            // Single slot
            try {
                return slot == Integer.parseInt(slotSpec.trim());
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
