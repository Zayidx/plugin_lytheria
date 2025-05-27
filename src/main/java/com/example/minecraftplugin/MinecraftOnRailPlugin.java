package com.example.minecartplugin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MinecartOnRailPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Minecart> playerMinecarts = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MinecartOnRailPlugin has been enabled!");
        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
        playerMinecarts.values().forEach(Vehicle::remove);
        playerMinecarts.clear();
        getLogger().info("MinecartOnRailPlugin has been disabled!");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || !event.hasBlock()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (!isRail(block.getType())) {
            return;
        }

        if (!player.hasPermission("minecartplugin.userail")) {
            player.sendMessage(ChatColor.RED + "Kamu tidak memiliki izin untuk menggunakan fitur ini!");
            return;
        }

        if (playerMinecarts.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Kamu sudah memiliki minecart aktif!");
            return;
        }

        if (player.isSneaking()) {
            return;
        }

        event.setCancelled(true);

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Location spawnLoc = block.getLocation().add(0.5, 0.1, 0.5);
                    Minecart minecart = block.getWorld().spawn(spawnLoc, Minecart.class);
                    playerMinecarts.put(player.getUniqueId(), minecart);
                    player.sendMessage(ChatColor.GREEN + getConfig().getString("messages.spawn", "Kereta siap dinaiki!"));
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Failed to spawn minecart for player " + player.getName(), e);
                    player.sendMessage(ChatColor.RED + "Gagal memunculkan kereta!");
                }
            }
        }.runTask(this);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart) || !(event.getExited() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!playerMinecarts.containsKey(playerId) || playerMinecarts.get(playerId) != minecart) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    player.sendMessage(ChatColor.YELLOW + getConfig().getString("messages.exit", "Terima kasih sudah menaiki kereta!"));
                    minecart.remove();
                    playerMinecarts.remove(playerId);
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Failed to handle minecart exit for player " + player.getName(), e);
                }
            }
        }.runTask(this);
    }

    private boolean isRail(Material material) {
        return material == Material.RAIL ||
               material == Material.POWERED_RAIL ||
               material == Material.DETECTOR_RAIL ||
               material == Material.ACTIVATOR_RAIL;
    }
}