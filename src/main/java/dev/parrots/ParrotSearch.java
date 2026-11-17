package dev.parrots;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Parrot;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Queries only owned areas, including when a search crosses a Folia region boundary. */
final class ParrotSearch {
    private ParrotSearch() {
    }

    static void find(Plugin plugin, Location origin, double radius, int limit, Consumer<List<Parrot>> result) {
        if (!plugin.isEnabled()) {
            result.accept(List.of());
            return;
        }
        World world = origin.getWorld();
        BoundingBox bounds = BoundingBox.of(origin, radius, radius, radius);
        int minX = floorChunk(bounds.getMinX());
        int minZ = floorChunk(bounds.getMinZ());
        int maxX = floorChunk(bounds.getMaxX());
        int maxZ = floorChunk(bounds.getMaxZ());
        Map<UUID, Candidate> candidates = new ConcurrentHashMap<>();
        if (Bukkit.isOwnedByCurrentRegion(world, minX, minZ, maxX, maxZ)) {
            query(world, bounds, origin, radius, candidates);
            result.accept(nearest(candidates, limit));
            return;
        }

        AtomicInteger remaining = new AtomicInteger((maxX - minX + 1) * (maxZ - minZ + 1));
        AtomicBoolean delivered = new AtomicBoolean();
        Runnable complete = () -> {
            if (delivered.compareAndSet(false, true)) result.accept(nearest(candidates, limit));
        };
        // A chunk can unload before its region task runs. Never retain a phrase indefinitely.
        io.papermc.paper.threadedregions.scheduler.ScheduledTask timeout;
        try {
            timeout = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> complete.run(), 40L);
        } catch (org.bukkit.plugin.IllegalPluginAccessException e) {
            complete.run();
            return;
        }
        Runnable finished = () -> {
            if (remaining.decrementAndGet() == 0) {
                timeout.cancel();
                complete.run();
            }
        };
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int chunkX = x;
                int chunkZ = z;
                if (!world.isChunkLoaded(x, z)) {
                    finished.run();
                    continue;
                }
                try {
                    Bukkit.getRegionScheduler().run(plugin, world, x, z, task -> {
                        try {
                            if (delivered.get() || !world.isChunkLoaded(chunkX, chunkZ)) return;
                            BoundingBox slice = chunkBounds(bounds, chunkX, chunkZ);
                            if (Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
                                query(world, slice, origin, radius, candidates);
                            }
                        } finally {
                            finished.run();
                        }
                    });
                } catch (org.bukkit.plugin.IllegalPluginAccessException e) {
                    finished.run();
                }
            }
        }
    }

    static BoundingBox chunkBounds(BoundingBox bounds, int chunkX, int chunkZ) {
        // nextDown keeps the inclusive ownership check inside this chunk.
        return new BoundingBox(
                Math.max(bounds.getMinX(), chunkX * 16D), bounds.getMinY(),
                Math.max(bounds.getMinZ(), chunkZ * 16D),
                Math.min(bounds.getMaxX(), Math.nextDown((chunkX + 1D) * 16D)), bounds.getMaxY(),
                Math.min(bounds.getMaxZ(), Math.nextDown((chunkZ + 1D) * 16D)));
    }

    static int floorChunk(double coordinate) {
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static void query(World world, BoundingBox bounds, Location origin, double radius,
                              Map<UUID, Candidate> candidates) {
        double radiusSquared = radius * radius;
        world.getNearbyEntities(bounds, entity -> entity.getType() == EntityType.PARROT).stream()
                .filter(Bukkit::isOwnedByCurrentRegion)
                .map(Parrot.class::cast)
                .forEach(parrot -> {
                    double distance = parrot.getLocation().distanceSquared(origin);
                    if (distance <= radiusSquared) candidates.put(parrot.getUniqueId(), new Candidate(parrot, distance));
                });
    }

    private static List<Parrot> nearest(Map<UUID, Candidate> candidates, int limit) {
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::distanceSquared))
                .limit(limit)
                .map(Candidate::parrot)
                .toList();
    }

    private record Candidate(Parrot parrot, double distanceSquared) {
    }
}
