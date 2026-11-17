package dev.parrots;

import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

final class PhraseRepeater {
    private final PlasmoParrotsPlugin plugin;
    private volatile PlasmoVoiceBridge voiceBridge;
    // Player entity tasks run concurrently in different Folia regions.
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingSearches = new ConcurrentHashMap<>();
    private long epoch;

    PhraseRepeater(PlasmoParrotsPlugin plugin) {
        this.plugin = plugin;
    }

    void setVoiceBridge(PlasmoVoiceBridge voiceBridge) {
        this.voiceBridge = voiceBridge;
    }

    void maybeRepeat(PhraseBuffer phrase, Player player, long expectedGeneration) {
        if (!player.isOnline()) {
            debug("skip: player is offline or not found, uuid=" + phrase.playerId());
            return;
        }
        if (phrase.isEmpty()) {
            debug("skip: empty phrase from " + player.getName());
            return;
        }

        UUID playerId = player.getUniqueId();
        PluginSettings settings;
        long expectedEpoch;
        synchronized (voiceBridge) {
            settings = plugin.settings();
            if (settings.repeatChance() == 0D || !voiceBridge.isCurrentGeneration(expectedGeneration)) return;
            long now = monotonicMillis();
            Long cooldownUntil = cooldowns.get(playerId);
            if (cooldownUntil != null && cooldownUntil > now) {
                debug("skip: cooldown for " + player.getName() + ", left=" + (cooldownUntil - now) + "ms");
                return;
            }
            expectedEpoch = epoch;
            if (pendingSearches.putIfAbsent(playerId, expectedEpoch) != null) {
                debug("skip: parrot search already pending for " + playerId);
                return;
            }
        }
        // World queries must not block all voice capture handlers on the bridge monitor.
        try {
            ParrotSearch.find(plugin, player.getLocation(), settings.parrotRadius(), settings.parrotsMax(), parrots ->
                    EntityTasks.schedule(plugin, player, () -> {
                        synchronized (voiceBridge) {
                            pendingSearches.remove(playerId, expectedEpoch);
                            if (epoch == expectedEpoch && voiceBridge.isReady() && player.isOnline()) {
                                repeatWithParrots(phrase, player, parrots, settings);
                            }
                        }
                    }, () -> pendingSearches.remove(playerId, expectedEpoch), 1L));
        } catch (RuntimeException e) {
            pendingSearches.remove(playerId, expectedEpoch);
            plugin.getLogger().warning("Parrot search failed: " + e.getMessage());
        }
    }

    private void repeatWithParrots(PhraseBuffer phrase, Player player, List<Parrot> parrots, PluginSettings settings) {
        if (parrots.isEmpty()) {
            debug("skip: no parrot near " + player.getName() + " in radius " + settings.parrotRadius());
            return;
        }

        if (voiceBridge != null && voiceBridge.isReady()) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int parrotCount = Math.min(parrots.size(), randomInt(random, settings.parrotsMin(), settings.parrotsMax()));
            List<byte[]> phrasePackets = phrase.packets();
            int repeats = 0;
            for (int i = 0; i < parrotCount; i++) {
                Parrot parrot = parrots.get(i);
                double parrotChance = individualChance(settings.repeatChance(), i, parrotCount, random);
                if (random.nextDouble() >= parrotChance) {
                    if (settings.debug()) debug("skip: chance failed for parrot " + parrot.getUniqueId()
                            + ", chance=" + parrotChance);
                    continue;
                }

                ReplayEffect effect = settings.randomEffect(random);
                double pitch = effect.randomPitch(random) * settings.pitchFactor();
                List<byte[]> packets = randomFragment(phrasePackets, settings, random);
                long staggerMillis = randomLong(random, settings.parrotStaggerMinMillis(), settings.parrotStaggerMaxMillis())
                        + randomLong(random, 0L, 180L * i);

                if (settings.debug()) debug("repeat: " + player.getName() + " -> parrot " + parrot.getUniqueId()
                        + ", effect=" + effect.name()
                        + ", chance=" + String.format(java.util.Locale.ROOT, "%.2f", parrotChance)
                        + ", pitch=" + String.format(java.util.Locale.ROOT, "%.2f", pitch)
                        + ", packets=" + packets.size()
                        + ", stagger=" + staggerMillis + "ms");
                if (voiceBridge.playParrotRepeat(parrot, packets, phrase.stereo(), pitch, effect, staggerMillis)) repeats++;
            }
            if (repeats == 0) {
                debug("skip: all nearby parrots ignored " + player.getName());
                return;
            }
            cooldowns.put(player.getUniqueId(), monotonicMillis() + settings.playerCooldownMillis());
        } else {
            debug("skip: voice bridge is not ready");
        }
    }

    private List<byte[]> randomFragment(List<byte[]> packets, PluginSettings settings, ThreadLocalRandom random) {
        if (packets.isEmpty()) return List.of();

        int minPackets = Math.max(1, (int) Math.ceil(settings.repeatDurationMinMillis() / 20D));
        int maxPackets = Math.max(minPackets, (int) Math.ceil(settings.repeatDurationMaxMillis() / 20D));
        int length = Math.min(packets.size(), randomInt(random, minPackets, maxPackets));
        int start = packets.size() <= length ? 0 : random.nextInt(packets.size() - length + 1);
        return List.copyOf(packets.subList(start, start + length));
    }

    static double individualChance(double baseChance, int index, int total, ThreadLocalRandom random) {
        if (baseChance <= 0D) return 0D;
        if (baseChance >= 1D) return 1D;

        double spread = random.nextDouble(-0.16D, 0.19D);
        double crowdBonus = total > 1 ? 0.06D * (1D - ((double) index / total)) : 0D;
        return Math.max(0D, Math.min(1D, baseChance + spread + crowdBonus));
    }

    void purgeExpiredCooldowns() {
        purgeExpiredCooldowns(monotonicMillis());
    }

    private void purgeExpiredCooldowns(long now) {
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    void clear() {
        epoch++;
        cooldowns.clear();
        pendingSearches.clear();
    }

    private int randomInt(ThreadLocalRandom random, int min, int max) {
        if (max <= min) return min;
        return random.nextInt(min, max + 1);
    }

    private long randomLong(ThreadLocalRandom random, long min, long max) {
        if (max <= min) return min;
        return random.nextLong(min, max + 1L);
    }

    private void debug(String message) {
        if (plugin.settings().debug()) plugin.getLogger().info("[debug] " + message);
    }

    private static long monotonicMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }
}
