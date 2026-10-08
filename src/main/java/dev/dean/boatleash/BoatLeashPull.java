package dev.dean.boatleash;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Leashable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class BoatLeashPull extends JavaPlugin {

    // Start pulling once the boat is farther than this from the holder (blocks)
    private static final double START_PULL = 3.0;
    // Beyond this distance, teleport the boat next to the holder so the lead never snaps
    private static final double TELEPORT_DIST = 7.0;
    // Boat stays this far from the holder after a teleport (blocks)
    private static final double TELEPORT_GAP = 2.0;
    // Max pull speed in blocks per tick
    private static final double MAX_SPEED = 4.0;

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    }

    private void tick() {
        for (World world : getServer().getWorlds()) {
            for (Boat boat : world.getEntitiesByClass(Boat.class)) {
                if (!(boat instanceof Leashable leashable)) continue;
                if (!leashable.isLeashed()) continue;
                if (!hasPlayerPassenger(boat)) continue;

                Entity holder = leashable.getLeashHolder();
                // Only pull toward players (not fence knots)
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
