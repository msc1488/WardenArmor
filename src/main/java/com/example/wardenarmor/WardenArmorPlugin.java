package com.example.wardenarmor;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WardenArmorPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private static final String ARMOR_KEY = "warden_armor";
    private static final String OWNER_NAME = "Depervs";
    private static final long COOLDOWN_MS = 5000L;

    private static final int AURA_BLOCKS = 6;      // сколько блоков в ауре
    private static final double AURA_RADIUS = 1.2; // радиус вращения
    private static final double AURA_HEIGHT = 1.0; // высота центра ауры

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, List<BlockDisplay>> activeAuras = new HashMap<>();

    private NamespacedKey armorKey;

    @Override
    public void onEnable() {
        this.armorKey = new NamespacedKey(this, ARMOR_KEY);

        if (getCommand("givewardenarmor") != null) {
            getCommand("givewardenarmor").setExecutor(this);
        }

        getServer().getPluginManager().registerEvents(this, this);

        // Задача ауры — каждые 2 тика обновляем положение блоков
        new BukkitRunnable() {
            double angle = 0;

            @Override
            public void run() {
                angle += 0.15;
                if (angle > Math.PI * 2) angle -= Math.PI * 2;

                for (Player player : getServer().getOnlinePlayers()) {
                    if (hasWardenArmor(player)) {
                        updateAura(player, angle);
                    } else if (activeAuras.containsKey(player.getUniqueId())) {
                        removeAura(player);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 2L);

        getLogger().info("WardenArmorPlugin включён!");
    }

    @Override
    public void onDisable() {
        // Убираем все ауры при выключении
        for (Player player : getServer().getOnlinePlayers()) {
            removeAura(player);
        }
        activeAuras.clear();
        getLogger().info("WardenArmorPlugin выключен!");
    }

    // ==================== АУРА ====================

    private void updateAura(Player player, double baseAngle) {
        UUID uuid = player.getUniqueId();
        List<BlockDisplay> aura = activeAuras.get(uuid);

        // Если ауры нет — создаём
        if (aura == null || aura.isEmpty() || aura.stream().anyMatch(d -> !d.isValid())) {
            if (aura != null) {
                aura.forEach(Entity::remove);
            }
            aura = new ArrayList<>();
            for (int i = 0; i < AURA_BLOCKS; i++) {
                BlockDisplay display = player.getWorld().spawn(player.getLocation(), BlockDisplay.class, bd -> {
                    bd.setBlock(Material.SCULK.createBlockData());
                    bd.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                    bd.setViewRange(0.5f);
                    bd.setTransformation(new Transformation(
                            new Vector3f(-0.15f, -0.15f, -0.15f),
                            new Quaternionf(),
                            new Vector3f(0.3f, 0.3f, 0.3f),
                            new Quaternionf()
                    ));
                });
                aura.add(display);
            }
            activeAuras.put(uuid, aura);
        }

        // Обновляем позиции — вращаем по кругу
        Location center = player.getLocation().add(0, AURA_HEIGHT, 0);
        double pulse = 0.9 + 0.2 * Math.sin(baseAngle * 3);

        for (int i = 0; i < aura.size(); i++) {
            double offset = baseAngle + (Math.PI * 2 / AURA_BLOCKS) * i;
            double x = Math.cos(offset) * AURA_RADIUS;
            double z = Math.sin(offset) * AURA_RADIUS;
            double y = Math.sin(offset * 2) * 0.3;

            Location loc = center.clone().add(x, y, z);

            BlockDisplay display = aura.get(i);
            display.teleport(loc);

            // Пульсация размера
            display.setTransformation(new Transformation(
                    new Vector3f(-0.15f, -0.15f, -0.15f),
                    new Quaternionf(),
                    new Vector3f((float) (0.3f * pulse), (float) (0.3f * pulse), (float) (0.3f * pulse)),
                    new Quaternionf()
            ));
        }
    }

    private void removeAura(Player player) {
        List<BlockDisplay> aura = activeAuras.remove(player.getUniqueId());
        if (aura != null) {
            aura.forEach(display -> {
                if (display.isValid()) display.remove();
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeAura(event.getPlayer());
    }

    // ==================== КОМАНДА ====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("givewardenarmor")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cЭта команда только для игроков!"));
            return true;
        }

        if (!player.getName().equals(OWNER_NAME)) {
            player.sendMessage(Component.text("§cУ вас нет прав на использование этой команды!"));
            return true;
        }

        Player target;
        if (args.length > 0) {
            target = getServer().getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(Component.text("§cИгрок не найден!"));
                return true;
            }
        } else {
            target = player;
        }

        target.getInventory().setHelmet(createWardenArmorPiece(Material.NETHERITE_HELMET, "§5§lШлем Вардена"));
        target.getInventory().setChestplate(createWardenArmorPiece(Material.NETHERITE_CHESTPLATE, "§5§lНагрудник Вардена"));
        target.getInventory().setLeggings(createWardenArmorPiece(Material.NETHERITE_LEGGINGS, "§5§lШтаны Вардена"));
        target.getInventory().setBoots(createWardenArmorPiece(Material.NETHERITE_BOOTS, "§5§lБотинки Вардена"));

        target.sendMessage(Component.text("§aВы получили броню Вардена! Нажмите §eF§a для Sonic Boom."));

        if (!target.equals(player)) {
            player.sendMessage(Component.text("§aВы выдали броню Вардена игроку " + target.getName()));
        }

        return true;
    }

    private ItemStack createWardenArmorPiece(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name));
            meta.getPersistentDataContainer().set(armorKey, PersistentDataType.STRING, "true");
            meta.addEnchant(Enchantment.PROTECTION, 4, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            item.setItemMeta(meta);
        }

        return item;
    }

    // ==================== СПОСОБНОСТЬ (F) ====================

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        if (player.getInventory().getItemInOffHand().getType() != Material.AIR) {
            return;
        }

        if (!hasWardenArmor(player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (cooldowns.containsKey(uuid)) {
            long lastUse = cooldowns.get(uuid);
            if (System.currentTimeMillis() - lastUse < COOLDOWN_MS) {
                long remaining = (COOLDOWN_MS - (System.currentTimeMillis() - lastUse)) / 1000;
                player.sendMessage(Component.text("§cСпособность перезаряжается! Осталось: " + remaining + " сек."));
                return;
            }
        }

        event.setCancelled(true);
        activateSonicBoom(player);
        cooldowns.put(uuid, System.currentTimeMillis());
    }

    private boolean hasWardenArmor(Player player) {
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType() == Material.AIR) return false;

            ItemMeta meta = armor.getItemMeta();
            if (meta == null) return false;

            if (!meta.getPersistentDataContainer().has(armorKey, PersistentDataType.STRING)) {
                return false;
            }
        }
        return true;
    }

    private void activateSonicBoom(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM,
                SoundCategory.PLAYERS, 1.0f, 1.0f);

        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().normalize();

        new BukkitRunnable() {
            double distance = 0;
            final double maxDistance = 15;
            final double step = 0.8;

            @Override
            public void run() {
                if (distance > maxDistance) {
                    this.cancel();
                    return;
                }

                Location point = eyeLocation.clone().add(direction.clone().multiply(distance));
                player.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);

                if (distance > 1) {
                    for (Entity entity : point.getWorld().getNearbyEntities(point, 1.0, 1.0, 1.0)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(10.0, player);
                            Vector knockback = entity.getLocation().toVector()
                                    .subtract(player.getLocation().toVector()).normalize().multiply(1.5);
                            entity.setVelocity(knockback);
                        }
                    }
                }

                distance += step;
            }
        }.runTaskTimer(this, 0L, 1L);
    }
}