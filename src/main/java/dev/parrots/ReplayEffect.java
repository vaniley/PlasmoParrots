package dev.parrots;

record ReplayEffect(
        String name,
        int weight,
        double pitchMin,
        double pitchMax,
        double stutterChance,
        double jumpBackChance,
        double tailRepeatChance,
        double burstChance
) {
    double randomPitch(java.util.concurrent.ThreadLocalRandom random) {
        if (pitchMax <= pitchMin) return pitchMin;
        return random.nextDouble(pitchMin, pitchMax);
    }
}
