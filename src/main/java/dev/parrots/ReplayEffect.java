package dev.parrots;

record ReplayEffect(
        String name,
        int weight,
        double pitchMin,
        double pitchMax,
        double stutterChance,
        int stutterRepeatsMin,
        int stutterRepeatsMax,
        double reverseChance,
        double dropChance,
        double scrambleChance,
        double jumpBackChance,
        double tailRepeatChance,
        double burstChance,
        int burstLengthMin,
        int burstLengthMax
) {
    double randomPitch(java.util.concurrent.ThreadLocalRandom random) {
        if (pitchMax <= pitchMin) return pitchMin;
        return random.nextDouble(pitchMin, pitchMax);
    }
}
