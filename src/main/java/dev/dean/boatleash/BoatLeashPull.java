package dev.dean.boatleash;

import io.papermc.paper.entity.TeleportFlag;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class BoatLeashPull extends JavaPlugin {

    private static final double START_PULL = 3.0;
    private static final double TELEPORT_DIST = 7.0;
    private static final double TELEPORT_GAP = 2.0;
    private static final double MAX_SPEED = 4.0;

    private Class<?> leashableClass;
    private Method isLeashedMethod;
    private Method getLeashHolderMethod;
    private Method getBukkitEntityMethod;
    private final Map<Class<?>, Method> getHandleCache = new HashMap<>();

    @Override
    public void onEnable() {
        try {
            leashableClass = Class.forName("net.minecraft.world.entity.Leashable");
            isLeashedMethod = leashableClass.getMethod("isLeashed");
            getLeashHolderMethod = leashableClass.getMethod("getLeashHolder");
            getBukkitEntityMethod = Class.forName("net.minecraft.world.entity.Entity").getMethod("getBukkitEntity");
        } catch (ReflectiveOperationException e) {
            getLogger().severe("Could not hook into leash code: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    }

    private Entity getLeashHolder(Boat boat) {
        try {
            Method getHandle = getHandleCache.computeIfAbsent(boat.getClass(), c -> {
                try {
                    return c.getMethod("getHandle");
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            });
            Object handle = getHandle.invoke(boat);
            if (!leashableClass.isInstance(handle)) return null;
            if (!(Boolean) isLeashedMethod.invoke(handle)) return null;
            Object holder = getLeashHolderMethod.invoke(handle);
            if (holder == null) return null;
            return (Entity) getBukkitEntityMethod.invoke(holder);
        } catch (Exception e) {
            return null;
        }
    }

    private void tick() {
        for (World world : getServer().getWorlds()) {
            for (Boat boat : world.getEntitiesByClass(Boat.class)) {
                if (!hasPlayerPassenger(boat)) continue;

                Entity holder = getLeashHolder(boat);
                if (!(holder instanceof Player)) continue;
                if (!holder.getWorld().equals(world)) continue;

                pull(boat, holder);
            }
        }
    }

    private boolean hasPlayerPassenger(Boat boat) {
        for (Entity passenger : boat.getPassengers()) {
            if (passenger instanceof Player) return true;
        }
        return false;
    }

    private void pull(Boat boat, Entity holder) {
        Location boatLoc = boat.getLocation();
        Location holderLoc = holder.getLocation();

        Vector diff = holderLoc.toVector().subtract(boatLoc.toVector());
        double dist = diff.length();
        if (dist < START_PULL) return;

        Vector dir = diff.clone().normalize();

        if (dist > TELEPORT_DIST) {
            Location target = holderLoc.clone().subtract(dir.clone().multiply(TELEPORT_GAP));
            target.setYaw(boatLoc.getYaw());
            target.setPitch(boatLoc.getPitch());
            if (target.getBlock().isPassable() && target.clone().add(0, 1, 0).getBlock().isPassable()) {
                boat.teleport(target, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                return;
            }
        }

        double speed = Math.min(0.2 + dist * 0.3, MAX_SPEED);
        boat.setVelocity(dir.multiply(speed));
    }
}
