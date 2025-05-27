package com.example.minecartplugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
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
    private ProtocolManager protocolManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        // Initialize ProtocolLib
        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            protocolManager = ProtocolLibrary.getProtocolManager();
            registerPacketListener();
            getLogger().info("ProtocolLib detected and packet listener registered.");
        } else {
            getLogger().warning("ProtocolLib not found! Falling back to PlayerInteractEvent.");
        }

        getLogger().info("MinecartOnRailPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        playerMinecarts.values().forEach(Vehicle::remove);
        playerMinecarts.clear();
        getLogger().info("MinecartOnRailPlugin has been disabled!");
    }

    // Register packet listener for right-click on block
    private void registerPacketListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                this,
                ListenerPriority.NORMAL,
                com.comphenix.protocol.PacketType.Play.Client.USE_ITEM
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                PacketContainer packet = event.getPacket();
                EnumWrappers.PlayerAction action = packet.getPlayerActions().read(0);

                if (action != EnumWrappers.PlayerAction.START_DESTROY_BLOCK &&
                    action != EnumWrappers.PlayerAction.STOP_DESTROY_BLOCK &&
                    action != EnumWrappers.PlayerAction.ABORT_DESTROY_BLOCK) {
                    // Handle right-click
                    BlockPosition blockPos = packet.getBlockPositionModifier().read(0);
                    Block block = blockPos.toLocation(player.getWorld()).getBlock();

                    getLogger().info("Packet detected: Player " + player.getName() + " right-clicked block " + block.getType() + " at " + block.getLocation());

                    handleRailClick(player, block);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || !event.hasBlock()) {
            getLogger().info("Ignoring PlayerInteractEvent: Not a right-click or no block.");
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        getLogger().info("PlayerInteractEvent: Player " + player.getName() + " right-clicked " + block.getType() + " at " + block.getLocation());

        handleRailClick(player, block);
    }

    private void handleRailClick(Player player, Block block) {
        if (!isRail(block.getType())) {
            getLogger().info("Not a rail: " + block.getType());
            return;
        }

        if (!player.hasPermission("minecartplugin.userail")) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.no-permission", "Kamu tidak memiliki izin untuk menggunakan fitur ini!"));
            getLogger().info("Player " + player.getName() + " lacks permission minecartplugin.userail");
            return;
        }

        if (playerMinecarts.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + getConfig().getString("messages.already-active", "Kamu sudah memiliki minecart aktif!"));
            getLogger().info("Player " + player.getName() + " already has an active minecart.");
            return;
        }

        if (player.isSneaking()) {
            getLogger().info("Ignoring: Player " + player.getName() + " is sneaking.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Location spawnLoc = block.getLocation().add(0.5, 0.1, 0.5);
                    Minecart minecart = block.getWorld().spawn(spawnLoc, Minecart.class);
                    playerMinecarts.put(player.getUniqueId(), minecart);
                    player.sendMessage(ChatColor.GREEN + getConfig().getString("messages.spawn", "Kereta siap dinaiki!"));
                    getLogger().info("Spawned minecart for " + player.getName() + " at " + spawnLoc);
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + getConfig().getString("messages.error", "Gagal memunculkan kereta!"));
                    getLogger().log(Level.SEVERE, "Failed to spawn minecart for player " + player.getName(), e);
                }
            }
        }.runTask(this);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart) || !(event.getExited() instanceof Player player)) {
            getLogger().info("Ignoring VehicleExitEvent: Not a minecart or player.");
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!playerMinecarts.containsKey(playerId) || playerMinecarts.get(playerId) != minecart) {
            getLogger().info("Ignoring VehicleExitEvent: Minecart not owned by plugin for " + player.getName());
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    player.sendMessage(ChatColor.YELLOW + getConfig().getString("messages.exit", "Terima kasih sudah menaiki kereta!"));
                    minecart.remove();
                    playerMinecarts.remove(playerId);
                    getLogger().info("Removed minecart for " + player.getName());
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Failed to handle minecart exit for player " + player.getName(), e);
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