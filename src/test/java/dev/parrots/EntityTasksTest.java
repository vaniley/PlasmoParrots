package dev.parrots;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.entity.Parrot;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityTasksTest {
    @Test
    void usesEntitySchedulerAndClampsZeroDelay() {
        Plugin plugin = mock(Plugin.class);
        Parrot parrot = mock(Parrot.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(parrot.getScheduler()).thenReturn(scheduler);
        Runnable action = mock(Runnable.class);
        Runnable retired = mock(Runnable.class);
        when(scheduler.execute(plugin, action, retired, 1L)).thenReturn(true);

        assertTrue(EntityTasks.schedule(plugin, parrot, action, retired, 0L));
        verify(scheduler).execute(plugin, action, retired, 1L);
        verifyNoInteractions(action, retired);
    }

    @Test
    void releasesReservationWhenEntityIsAlreadyRetired() {
        Plugin plugin = mock(Plugin.class);
        Parrot parrot = mock(Parrot.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(parrot.getScheduler()).thenReturn(scheduler);
        Runnable retired = mock(Runnable.class);

        assertFalse(EntityTasks.schedule(plugin, parrot, () -> {}, retired, 3L));
        verify(retired).run();
    }

    @Test
    void doesNotScheduleAfterDisable() {
        Plugin plugin = mock(Plugin.class);
        Parrot parrot = mock(Parrot.class);
        Runnable retired = mock(Runnable.class);

        assertFalse(EntityTasks.schedule(plugin, parrot, () -> {}, retired, 1L));
        verify(retired).run();
        verifyNoInteractions(parrot);
    }
}
