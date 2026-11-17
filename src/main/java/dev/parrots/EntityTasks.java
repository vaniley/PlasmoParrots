package dev.parrots;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** Paper and Folia both implement the entity scheduler, including teleport tracking. */
final class EntityTasks {
    private EntityTasks() {
    }

    static boolean schedule(Plugin plugin, Entity entity, Runnable action, Runnable retired, long delayTicks) {
        if (!plugin.isEnabled()) {
            retired.run();
            return false;
        }
        try {
            // execute() invokes retired if an entity disappears before the callback runs.
            boolean accepted = entity.getScheduler().execute(plugin, action, retired, Math.max(1L, delayTicks));
            if (!accepted) retired.run();
            return accepted;
        } catch (org.bukkit.plugin.IllegalPluginAccessException e) {
            retired.run();
            return false;
        }
    }
}
