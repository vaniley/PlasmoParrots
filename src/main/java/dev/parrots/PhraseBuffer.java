package dev.parrots;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

final class PhraseBuffer {
    private static final long FRAME_MILLIS = 20L;

    private final UUID playerId;
    private final LongSupplier clock;
    private final long startedAtNanos;
    private final int maxPackets;
    private final List<byte[]> packets = new ArrayList<>();
    private long lastPacketAtNanos;
    private boolean stereo;

    PhraseBuffer(UUID playerId, int maxPackets) {
        this(playerId, maxPackets, System::nanoTime);
    }

    PhraseBuffer(UUID playerId, int maxPackets, LongSupplier clock) {
        this.playerId = playerId;
        this.maxPackets = maxPackets;
        this.clock = clock;
        this.startedAtNanos = clock.getAsLong();
        this.lastPacketAtNanos = startedAtNanos;
    }

    UUID playerId() {
        return playerId;
    }

    synchronized long ageMillis() {
        return TimeUnit.NANOSECONDS.toMillis(clock.getAsLong() - startedAtNanos);
    }

    synchronized long idleMillis() {
        return TimeUnit.NANOSECONDS.toMillis(clock.getAsLong() - lastPacketAtNanos);
    }

    synchronized boolean isEmpty() {
        return packets.isEmpty();
    }

    synchronized long durationMillis() {
        return packets.size() * FRAME_MILLIS;
    }

    synchronized boolean stereo() {
        return stereo;
    }

    synchronized void add(byte[] packet, boolean stereo) {
        if (packet.length == 0 || packets.size() >= maxPackets) return;
        if (!packets.isEmpty() && this.stereo != stereo) return;

        lastPacketAtNanos = clock.getAsLong();
        if (packets.isEmpty()) this.stereo = stereo;
        byte[] copy = new byte[packet.length];
        System.arraycopy(packet, 0, copy, 0, packet.length);
        packets.add(copy);
    }

    synchronized List<byte[]> packets() {
        return List.copyOf(packets);
    }

    synchronized boolean isFull() {
        return packets.size() >= maxPackets;
    }
}
