package dev.parrots;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

final class PhraseRepeater {
    private final PlasmoParrotsPlugin plugin;
    private PlasmoVoiceBridge voiceBridge;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    PhraseRepeater(PlasmoParrotsPlugin plugin) {
        this.plugin = plugin;
    }

    void setVoiceBridge(PlasmoVoiceBridge voiceBridge) {
        this.voiceBridge = voiceBridge;
    }

    void maybeRepeat(PhraseBuffer phrase) {
        Player player = Bukkit.getPlayer(phrase.playerId());
        if (player == null || !player.isOnline()) {
            debug("skip: player is offline or not found, uuid=" + phrase.playerId());
            return;
        }
        if (phrase.isEmpty()) {
            debug("skip: empty phrase from " + player.getName());
            return;
        }

        PluginSettings settings = plugin.settings();
        long now = System.currentTimeMillis();
        Long cooldownUntil = cooldowns.get(player.getUniqueId());
        if (cooldownUntil != null && cooldownUntil > now) {
            debug("skip: cooldown for " + player.getName() + ", left=" + (cooldownUntil - now) + "ms");
            return;
        }

        List<Parrot> parrots = nearestParrots(player, settings.parrotRadius());
        if (parrots.isEmpty()) {
            debug("skip: no parrot near " + player.getName() + " in radius " + settings.parrotRadius());
            return;
        }

        if (voiceBridge != null) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int parrotCount = Math.min(parrots.size(), randomInt(random, settings.parrotsMin(), settings.parrotsMax()));
            int repeats = 0;
            for (int i = 0; i < parrotCount; i++) {
                Parrot parrot = parrots.get(i);
                double parrotChance = individualChance(settings.repeatChance(), i, parrotCount, random);
                if (random.nextDouble() > parrotChance) {
                    debug("skip: chance failed for parrot " + parrot.getUniqueId()
                            + ", chance=" + String.format(java.util.Locale.ROOT, "%.2f", parrotChance));
                    continue;
                }

                ReplayEffect effect = settings.randomEffect(random);
                double pitch = effect.randomPitch(random) * settings.pitchFactor();
                List<byte[]> packets = randomFragment(phrase.packets(), settings, random);
                long staggerMillis = randomLong(random, settings.parrotStaggerMinMillis(), settings.parrotStaggerMaxMillis())
                        + randomLong(random, 0L, 180L * i);

                debug("repeat: " + player.getName() + " -> parrot " + parrot.getUniqueId()
                        + ", effect=" + effect.name()
                        + ", chance=" + String.format(java.util.Locale.ROOT, "%.2f", parrotChance)
                        + ", pitch=" + String.format(java.util.Locale.ROOT, "%.2f", pitch)
                        + ", packets=" + packets.size()
                        + ", stagger=" + staggerMillis + "ms");
                voiceBridge.playParrotRepeat(player, parrot, packets, phrase.stereo(), pitch, effect, staggerMillis);
                repeats++;
            }
            if (repeats == 0) {
                debug("skip: all nearby parrots ignored " + player.getName());
                return;
            }
            cooldowns.put(player.getUniqueId(), now + settings.playerCooldownMillis());
        } else {
            debug("skip: voice bridge is not ready");
        }
    }

    private List<Parrot> nearestParrots(Player player, double radius) {
        Location origin = player.getLocation();
        return player.getWorld().getNearbyEntities(origin, radius, radius, radius, entity -> entity.getType() == EntityType.PARROT)
                .stream()
                .map(Parrot.class::cast)
                .sorted(Comparator.comparingDouble(parrot -> parrot.getLocation().distanceSquared(origin)))
                .toList();
    }

    private List<byte[]> randomFragment(List<byte[]> packets, PluginSettings settings, ThreadLocalRandom random) {
        if (packets.isEmpty()) return List.of();

        int minPackets = Math.max(1, (int) Math.ceil(settings.repeatDurationMinMillis() / 20D));
        int maxPackets = Math.max(minPackets, (int) Math.ceil(settings.repeatDurationMaxMillis() / 20D));
        int length = Math.min(packets.size(), randomInt(random, minPackets, maxPackets));
        int start = packets.size() <= length ? 0 : random.nextInt(packets.size() - length + 1);
        return List.copyOf(packets.subList(start, start + length));
    }

    private double individualChance(double baseChance, int index, int total, ThreadLocalRandom random) {
        double spread = random.nextDouble(-0.16D, 0.19D);
        double crowdBonus = total > 1 ? 0.06D * (1D - ((double) index / total)) : 0D;
        return Math.max(0.02D, Math.min(0.95D, baseChance + spread + crowdBonus));
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
}
