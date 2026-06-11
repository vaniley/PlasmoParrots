package dev.parrots;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

record PluginSettings(
        double repeatChance,
        long maxPhraseMillis,
        double parrotRadius,
        long playerCooldownMillis,
        int maxBufferedPackets,
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
        long repeatMin = Math.max(100L, config.getLong("repeat-duration-min-millis", 650L));
        long repeatMax = Math.max(repeatMin, config.getLong("repeat-duration-max-millis", 2600L));
        int parrotsMin = Math.max(1, config.getInt("parrots-min", 1));
        int parrotsMax = Math.max(parrotsMin, config.getInt("parrots-max", 2));
        long staggerMin = Math.max(0L, config.getLong("parrot-stagger-min-millis", 0L));
        long staggerMax = Math.max(staggerMin, config.getLong("parrot-stagger-max-millis", 280L));

        return new PluginSettings(
                clamp(config.getDouble("repeat-chance", 0.42D), 0D, 1D),
                Math.max(250L, config.getLong("max-phrase-millis", 5000L)),
                Math.max(1D, config.getDouble("parrot-radius", 12D)),
                Math.max(0L, config.getLong("player-cooldown-millis", 4500L)),
                Math.max(8, config.getInt("max-buffered-packets", 260)),
                clamp(config.getDouble("pitch-factor", 1.18D), 1.05D, 2.0D),
                clamp(config.getDouble("parrot-volume", 0.9D), 0D, 1D),
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
        int totalWeight = effects.stream().mapToInt(effect -> Math.max(0, effect.weight())).sum();
        if (totalWeight <= 0) return fallbackEffect();

        int value = random.nextInt(totalWeight);
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
            int stutterMin = Math.max(1, integer(section, "stutter-repeats-min", 1));
            int stutterMax = Math.max(stutterMin, integer(section, "stutter-repeats-max", stutterMin));
            double reverseChance = clamp(number(section, "reverse-chance", 0D), 0D, 1D);
            double dropChance = clamp(number(section, "drop-chance", 0D), 0D, 0.95D);
            double scrambleChance = clamp(number(section, "scramble-chance", 0D), 0D, 1D);
            double jumpBackChance = clamp(number(section, "jump-back-chance", 0D), 0D, 1D);
            double tailRepeatChance = clamp(number(section, "tail-repeat-chance", 0D), 0D, 1D);
            double burstChance = clamp(number(section, "burst-chance", 0D), 0D, 1D);
            int burstLengthMin = Math.max(2, integer(section, "burst-length-min", 2));
            int burstLengthMax = Math.max(burstLengthMin, integer(section, "burst-length-max", burstLengthMin));

            effects.add(new ReplayEffect(name, weight, pitchMin, pitchMax, stutterChance, stutterMin, stutterMax,
                    reverseChance, dropChance, scrambleChance, jumpBackChance, tailRepeatChance,
                    burstChance, burstLengthMin, burstLengthMax));
        }

        if (effects.isEmpty()) effects.add(fallbackEffect());
        return List.copyOf(effects);
    }

    private static ReplayEffect fallbackEffect() {
        return new ReplayEffect("chatty-parrot", 1, 1.04D, 1.18D, 0.12D, 1, 2,
                0D, 0.005D, 0D, 0.08D, 0.38D, 0.10D, 2, 3);
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
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
