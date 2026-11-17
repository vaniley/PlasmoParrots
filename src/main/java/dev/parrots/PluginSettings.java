package dev.parrots;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

record PluginSettings(
        double repeatChance,
        long maxPhraseMillis,
        long phraseIdleMillis,
        double parrotRadius,
        long playerCooldownMillis,
        int maxBufferedPackets,
        int maxConcurrentReplays,
        double pitchFactor,
        double parrotVolume,
        long repeatDurationMinMillis,
        long repeatDurationMaxMillis,
        int parrotsMin,
        int parrotsMax,
        long parrotStaggerMinMillis,
        long parrotStaggerMaxMillis,
        List<ReplayEffect> effects,
        boolean debug
) {
    static PluginSettings load(FileConfiguration config) {
        long repeatMin = bounded(config.getLong("repeat-duration-min-millis", 420L), 100L, 60_000L);
        long repeatMax = bounded(config.getLong("repeat-duration-max-millis", 1900L), repeatMin, 60_000L);
        int parrotsMin = (int) bounded(config.getInt("parrots-min", 1), 1, 64);
        int parrotsMax = (int) bounded(config.getInt("parrots-max", 4), parrotsMin, 64);
        long staggerMin = bounded(config.getLong("parrot-stagger-min-millis", 60L), 0L, 60_000L);
        long staggerMax = bounded(config.getLong("parrot-stagger-max-millis", 680L), staggerMin, 60_000L);

        return new PluginSettings(
                clamp(finite(config.getDouble("repeat-chance", 0.52D), 0.52D), 0D, 1D),
                bounded(config.getLong("max-phrase-millis", 6500L), 250L, 60_000L),
                bounded(config.getLong("phrase-idle-millis", 450L), 100L, 60_000L),
                clamp(finite(config.getDouble("parrot-radius", 12D), 12D), 1D, 64D),
                bounded(config.getLong("player-cooldown-millis", 3200L), 0L, 3_600_000L),
                (int) bounded(config.getInt("max-buffered-packets", 220), 8, 3000),
                (int) bounded(config.getInt("max-concurrent-replays", 6), 1, 64),
                clamp(finite(config.getDouble("pitch-factor", 1.18D), 1.18D), 1.0D, 2.0D),
                clamp(finite(config.getDouble("parrot-volume", 0.9D), 0.9D), 0D, 1D),
                repeatMin,
                repeatMax,
                parrotsMin,
                parrotsMax,
                staggerMin,
                staggerMax,
                loadEffects(config),
                config.getBoolean("debug", false)
        );
    }

    ReplayEffect randomEffect(java.util.concurrent.ThreadLocalRandom random) {
        long totalWeight = effects.stream().mapToLong(effect -> Math.max(0, effect.weight())).sum();
        if (totalWeight <= 0L) return fallbackEffect();

        long value = random.nextLong(totalWeight);
        for (ReplayEffect effect : effects) {
            value -= Math.max(0, effect.weight());
            if (value < 0) return effect;
        }

        return fallbackEffect();
    }

    private static List<ReplayEffect> loadEffects(FileConfiguration config) {
        List<ReplayEffect> effects = new ArrayList<>();
        for (Map<?, ?> section : config.getMapList("effects")) {
            String name = string(section, "name", "custom");
            int weight = Math.max(0, integer(section, "weight", 1));
            double pitchMin = clamp(number(section, "pitch-min", 1.35D), 0.5D, 3.0D);
            double pitchMax = clamp(number(section, "pitch-max", pitchMin), pitchMin, 3.0D);
            double stutterChance = clamp(number(section, "stutter-chance", 0D), 0D, 1D);
            double jumpBackChance = clamp(number(section, "jump-back-chance", 0D), 0D, 1D);
            double tailRepeatChance = clamp(number(section, "tail-repeat-chance", 0D), 0D, 1D);
            double burstChance = clamp(number(section, "burst-chance", 0D), 0D, 1D);

            effects.add(new ReplayEffect(name, weight, pitchMin, pitchMax,
                    stutterChance, jumpBackChance, tailRepeatChance, burstChance));
        }

        if (effects.isEmpty()) effects.add(fallbackEffect());
        return List.copyOf(effects);
    }

    private static ReplayEffect fallbackEffect() {
        return new ReplayEffect("chatty-parrot", 1, 1.04D, 1.18D, 0.12D, 0.08D, 0.38D, 0.10D);
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double number(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) return fallback;
        return finite(number.doubleValue(), fallback);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, finite(value, min)));
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static long bounded(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
