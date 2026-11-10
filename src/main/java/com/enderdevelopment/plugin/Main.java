package com.enderdevelopment.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Main extends JavaPlugin implements Listener, CommandExecutor {
    private static final double PLAYER_HALF_WIDTH = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;

    private final Set<UUID> awaitingRespawn = new HashSet<>();
    private final Set<UUID> animatingDeath = new HashSet<>();
    private final Set<UUID> animatingRespawn = new HashSet<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("tda") != null) {
            getCommand("tda").setExecutor(this);
        }
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : respawnTasks.values()) {
            task.cancel();
        }
        respawnTasks.clear();
        awaitingRespawn.clear();
        animatingDeath.clear();
        animatingRespawn.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID id = player.getUniqueId();
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && animatingRespawn.contains(id)) {
            event.setCancelled(true);
            player.setFallDistance(0);
            return;
        }
        if (animatingDeath.contains(id)) {
            event.setCancelled(true);
            return;
        }
        if (event.getFinalDamage() < player.getHealth()) return;

        event.setCancelled(true);
        animatingDeath.add(id);
        showTitle(player, "death");
        playSound(player, "death");
        beginDeathRise(player);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        animatingDeath.remove(id);
        cancelRespawnAnimation(id);
        awaitingRespawn.add(id);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!awaitingRespawn.remove(id)) return;

        Location destination = event.getRespawnLocation().clone();
        World world = destination.getWorld();
        if (world == null) return;

        double requestedHeight = Math.max(0.0, getConfig().getDouble("respawn.start-height", 5.0));
        double availableHeight = world.getMaxHeight() - PLAYER_HEIGHT - destination.getY();
        double startHeight = Math.min(requestedHeight, availableHeight);
        if (startHeight <= 0.1) return;

        Location start = destination.clone().add(0, startHeight, 0);
        if (!hasClearDescentPath(destination, start)) return;

        event.setRespawnLocation(start);
        animatingRespawn.add(id);
        int duration = Math.max(1, getConfig().getInt("respawn.duration-ticks", 40));
        BukkitTask setupTask = Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline() || player.isDead() || !animatingRespawn.contains(id)) {
                cancelRespawnAnimation(id);
                return;
            }

            Location actualStart = player.getLocation();
            if (actualStart.getWorld() == null
                    || !actualStart.getWorld().equals(destination.getWorld())
                    || !hasClearDescentPath(destination, actualStart)) {
                cancelRespawnAnimation(id);
                return;
            }

            showTitle(player, "respawn");
            playSound(player, "respawn");
            startDescent(player, destination, actualStart, duration);
        });
        respawnTasks.put(id, setupTask);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancelRespawnAnimation(id);
        animatingDeath.remove(id);
        awaitingRespawn.remove(id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancelRespawnAnimation(id);
        animatingDeath.remove(id);
        awaitingRespawn.remove(id);
    }

    private void beginDeathRise(Player player) {
        final Location origin = player.getLocation().clone();
        final int duration = Math.max(1, getConfig().getInt("death.duration-ticks", 20));
        final double height = getConfig().getDouble("death.rise-blocks", 2.0);
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                UUID id = player.getUniqueId();
                if (!player.isOnline() || player.isDead() || !animatingDeath.contains(id)) {
                    animatingDeath.remove(id);
                    cancel();
                    return;
                }
                double progress = Math.min(1.0, (double) ++tick / duration);
                double smooth = progress * progress * (3.0 - 2.0 * progress);
                Location at = origin.clone().add(0, height * smooth, 0);
                player.teleport(at);
                spawnConfiguredParticle(at, "death", getConfig().getInt("death.particle-count", 12));
                spawnSmoke(at, getConfig().getInt("death.smoke-count", 6));
                if (tick >= duration) {
                    animatingDeath.remove(id);
                    cancel();
                    player.setHealth(0.0);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void startDescent(Player player, Location destination, Location start, int duration) {
        UUID id = player.getUniqueId();
        double height = Math.max(0.0, start.getY() - destination.getY());
        BukkitTask animationTask = new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || !animatingRespawn.contains(id)) {
                    cancelRespawnAnimation(id);
                    return;
                }

                double progress = Math.min(1.0, (double) ++tick / duration);
                double smooth = progress * progress * (3.0 - 2.0 * progress);
                Location at = destination.clone().add(0, height * (1.0 - smooth), 0);
                if (!player.teleport(at)) {
                    player.teleport(destination);
                    finishRespawnAnimation(id, player);
                    return;
                }
                player.setFallDistance(0);
                spawnCircle(at);

                if (tick >= duration) {
                    player.teleport(destination);
                    player.setFallDistance(0);
                    finishRespawnAnimation(id, player);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
        respawnTasks.put(id, animationTask);
    }

    private boolean hasClearDescentPath(Location destination, Location start) {
        World world = destination.getWorld();
        if (world == null || start.getWorld() == null || !world.equals(start.getWorld())) return false;
        if (start.getY() < destination.getY()) return false;
        if (start.getY() + PLAYER_HEIGHT >= world.getMaxHeight()) return false;

        int minX = (int) Math.floor(destination.getX() - PLAYER_HALF_WIDTH);
        int maxX = (int) Math.floor(destination.getX() + PLAYER_HALF_WIDTH);
        int minZ = (int) Math.floor(destination.getZ() - PLAYER_HALF_WIDTH);
        int maxZ = (int) Math.floor(destination.getZ() + PLAYER_HALF_WIDTH);
        int minY = Math.max(world.getMinHeight(), (int) Math.floor(destination.getY()));
        int maxY = (int) Math.floor(start.getY() + PLAYER_HEIGHT - 0.001);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.isPassable()) return false;
                }
            }
        }
        return true;
    }

    private void finishRespawnAnimation(UUID id, Player player) {
        animatingRespawn.remove(id);
        respawnTasks.remove(id);
        player.setFallDistance(0);
    }

    private void cancelRespawnAnimation(UUID id) {
        BukkitTask task = respawnTasks.remove(id);
        if (task != null) task.cancel();
        animatingRespawn.remove(id);
    }

    private void spawnCircle(Location center) {
        int points = Math.max(4, getConfig().getInt("respawn.circle-points", 16));
        double radius = Math.max(0.0, getConfig().getDouble("respawn.circle-radius", 1.2));
        Particle particle = particle("respawn");
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0);
        }
    }

    private void spawnConfiguredParticle(Location location, String section, int count) {
        if (count <= 0 || location.getWorld() == null) return;
        try {
            location.getWorld().spawnParticle(particle(section), location, count, 0.35, 0.45, 0.35, 0.01);
        } catch (RuntimeException ignored) {
        }
    }

    private void spawnSmoke(Location location, int count) {
        if (count <= 0 || location.getWorld() == null) return;
        location.getWorld().spawnParticle(Particle.SMOKE, location, count, 0.35, 0.45, 0.35, 0.01);
    }

    private Particle particle(String section) {
        try {
            return Particle.valueOf(getConfig().getString(section + ".particle", section.equals("death") ? "SOUL_FIRE_FLAME" : "END_ROD").toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            return section.equals("death") ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD;
        }
    }

    private void playSound(Player player, String section) {
        try {
            Sound sound = Sound.valueOf(getConfig().getString(section + ".sound", section.equals("death") ? "ENTITY_WITHER_DEATH" : "ENTITY_PLAYER_LEVELUP").toUpperCase());
            player.playSound(player.getLocation(), sound,
                    (float) getConfig().getDouble(section + ".volume", 0.8),
                    (float) getConfig().getDouble(section + ".pitch", 1.0));
        } catch (IllegalArgumentException | NullPointerException ignored) {
        }
    }

    private void showTitle(Player player, String section) {
        String title = getConfig().getString(section + ".title", "");
        String subtitle = getConfig().getString(section + ".subtitle", "");
        player.showTitle(net.kyori.adventure.title.Title.title(gradient(title), gradient(subtitle)));
    }

    private Component gradient(String text) {
        TextColor start = color(getConfig().getString("text.gradient-start", "#8A2BE2"), TextColor.color(138, 43, 226));
        TextColor end = color(getConfig().getString("text.gradient-end", "#FF69B4"), TextColor.color(255, 105, 180));
        Component result = Component.empty();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            double t = length <= 1 ? 0 : (double) i / (length - 1);
            int red = (int) Math.round(start.red() + (end.red() - start.red()) * t);
            int green = (int) Math.round(start.green() + (end.green() - start.green()) * t);
            int blue = (int) Math.round(start.blue() + (end.blue() - start.blue()) * t);
            result = result.append(Component.text(text.substring(i, i + 1), TextColor.color(red, green, blue)));
        }
        return result;
    }

    private TextColor color(String value, TextColor fallback) {
        try {
            return TextColor.fromHexString(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return fallback;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("thornhaven.animation.reload")) {
                sender.sendMessage("You do not have permission.");
                return true;
            }
            reloadConfig();
            sender.sendMessage("ThornhavenDeathAnimation configuration reloaded.");
            return true;
        }
        sender.sendMessage("Usage: /tda reload");
        return true;
    }
}
