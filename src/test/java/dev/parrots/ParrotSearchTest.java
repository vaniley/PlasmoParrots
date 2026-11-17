package dev.parrots;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Parrot;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ParrotSearchTest {
    @Test
    void calculatesNegativeChunkCoordinatesAndClipsQueryBounds() {
        assertEquals(-1, ParrotSearch.floorChunk(-0.1));
        assertEquals(-2, ParrotSearch.floorChunk(-16.1));
        BoundingBox bounds = new BoundingBox(-20, 0, -20, 20, 100, 20);
        BoundingBox slice = ParrotSearch.chunkBounds(bounds, -1, 0);
        assertEquals(-16D, slice.getMinX());
        assertEquals(-1, ParrotSearch.floorChunk(slice.getMaxX()));
        assertEquals(0, ParrotSearch.floorChunk(slice.getMinZ()));
        assertEquals(0, ParrotSearch.floorChunk(slice.getMaxZ()));
    }

    @Test
    void ownedSearchKeepsSphericalDistanceAndNearestLimit() {
        World world = mock(World.class);
        Location origin = new Location(world, 8, 64, 8);
        Parrot near = parrot(world, 9, 64, 8);
        Parrot far = parrot(world, 14, 64, 8);
        Parrot corner = parrot(world, 15, 71, 15);
        when(world.getNearbyEntities(any(BoundingBox.class), any())).thenReturn(List.of(far, corner, near));
        AtomicReference<List<Parrot>> result = new AtomicReference<>();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(eq(world), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(true);
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
            ParrotSearch.find(mock(Plugin.class), origin, 8, 1, result::set);
            assertEquals(List.of(near), result.get());
            bukkit.verify(Bukkit::getRegionScheduler, never());
        }
    }

    @Test
    void crossRegionSearchSchedulesOnlyLoadedChunksAndChecksSliceOwnership() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        GlobalRegionScheduler global = mock(GlobalRegionScheduler.class);
        RegionScheduler regions = mock(RegionScheduler.class);
        ScheduledTask timeout = mock(ScheduledTask.class);
        when(global.runDelayed(eq(plugin), any(), eq(40L))).thenReturn(timeout);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getNearbyEntities(any(BoundingBox.class), any())).thenReturn(List.of());
        AtomicReference<List<Parrot>> result = new AtomicReference<>();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(global);
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(regions);
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(eq(world), anyInt(), anyInt())).thenReturn(true);
            when(regions.run(eq(plugin), eq(world), anyInt(), anyInt(), any())).thenAnswer(invocation -> {
                Consumer<ScheduledTask> callback = invocation.getArgument(4);
                callback.accept(mock(ScheduledTask.class));
                return mock(ScheduledTask.class);
            });
            ParrotSearch.find(plugin, new Location(world, 16, 64, 8), 2, 4, result::set);
            assertEquals(List.of(), result.get());
            verify(regions, times(2)).run(eq(plugin), eq(world), anyInt(), anyInt(), any());
            verify(timeout).cancel();
            verify(world, times(2)).getNearbyEntities(argThat((BoundingBox box) ->
                    ParrotSearch.floorChunk(box.getMinX()) == ParrotSearch.floorChunk(box.getMaxX())
                            && ParrotSearch.floorChunk(box.getMinZ()) == ParrotSearch.floorChunk(box.getMaxZ())),
                    org.mockito.ArgumentMatchers.<Predicate<Entity>>any());
        }
    }

    @Test
    void missingRegionCallbacksCompleteOnceAtTimeout() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        GlobalRegionScheduler global = mock(GlobalRegionScheduler.class);
        RegionScheduler regions = mock(RegionScheduler.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        AtomicReference<Consumer<ScheduledTask>> timeoutCallback = new AtomicReference<>();
        when(global.runDelayed(eq(plugin), any(), eq(40L))).thenAnswer(invocation -> {
            timeoutCallback.set(invocation.getArgument(1));
            return mock(ScheduledTask.class);
        });
        @SuppressWarnings("unchecked")
        Consumer<List<Parrot>> result = mock(Consumer.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(global);
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(regions);
            ParrotSearch.find(plugin, new Location(world, 16, 64, 8), 2, 4, result);
            verifyNoInteractions(result);
            timeoutCallback.get().accept(mock(ScheduledTask.class));
            timeoutCallback.get().accept(mock(ScheduledTask.class));
            verify(result).accept(List.of());
            verifyNoMoreInteractions(result);
            verify(world, never()).getNearbyEntities(any(BoundingBox.class), any());
        }
    }

    private static Parrot parrot(World world, double x, double y, double z) {
        Parrot parrot = mock(Parrot.class);
        when(parrot.getUniqueId()).thenReturn(UUID.randomUUID());
        when(parrot.getLocation()).thenReturn(new Location(world, x, y, z));
        return parrot;
    }
}
