package dev.parrots;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class PhraseBuffer {
    private static final long FRAME_MILLIS = 20L;

    private final UUID playerId;
    private final long startedAtMillis;
    private final int maxPackets;
    private final List<byte[]> packets = new ArrayList<>();
    private long lastPacketAtMillis;
    private boolean stereo;

    PhraseBuffer(UUID playerId, int maxPackets) {
        this.playerId = playerId;
        this.maxPackets = maxPackets;
        this.startedAtMillis = System.currentTimeMillis();
        this.lastPacketAtMillis = startedAtMillis;
    }

    UUID playerId() {
        return playerId;
    }

    long ageMillis() {
        return System.currentTimeMillis() - startedAtMillis;
    }

    long idleMillis() {
        return System.currentTimeMillis() - lastPacketAtMillis;
    }

    boolean isEmpty() {
        return packets.isEmpty();
    }

    long durationMillis() {
        return packets.size() * FRAME_MILLIS;
    }

    boolean stereo() {
        return stereo;
    }

    void add(byte[] packet, boolean stereo) {
        lastPacketAtMillis = System.currentTimeMillis();
        if (packet.length == 0 || packets.size() >= maxPackets) return;

        if (packets.isEmpty()) this.stereo = stereo;
        byte[] copy = new byte[packet.length];
        System.arraycopy(packet, 0, copy, 0, packet.length);
        packets.add(copy);
    }

    List<byte[]> packets() {
        return List.copyOf(packets);
    }
}
