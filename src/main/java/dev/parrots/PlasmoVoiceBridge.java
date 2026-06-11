package dev.parrots;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import su.plo.voice.api.audio.codec.AudioDecoder;
import su.plo.voice.api.audio.codec.AudioEncoder;
import su.plo.voice.api.audio.codec.CodecException;
import su.plo.voice.api.encryption.EncryptionException;
import su.plo.slib.api.server.entity.McServerEntity;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.ServerActivation;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerEntitySource;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEndEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.player.VoicePlayer;
import su.plo.voice.proto.data.audio.capture.VoiceActivation;
import su.plo.voice.proto.data.audio.codec.CodecInfo;
import su.plo.voice.proto.packets.tcp.serverbound.PlayerAudioEndPacket;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

final class PlasmoVoiceBridge {

    // Plasmo Voice uses Opus with these fixed params on the server side
    private static final CodecInfo OPUS_CODEC = new CodecInfo("opus", Map.of());
    // Each Opus frame = 20 ms of audio at 48 kHz / frame-size 960
    private static final long FRAME_MS = 20L;
    private static final int OPUS_FRAME_SAMPLES = 960;
    private static final int PCM_TAIL_CROSSFADE_FRAMES = 144;
    private static final int PCM_EDGE_FADE_FRAMES = 480;
    private static final int PCM_SILENCE_PAD_FRAMES = OPUS_FRAME_SAMPLES;
    private static final int PCM_TARGET_PEAK = 25_500;
    private static final double MAX_CLEAN_PITCH_FACTOR = 1.68D;
    private static final long MIN_REPLAY_DELAY_MS = 260L;

    private final PlasmoParrotsPlugin plugin;
    private final Map<UUID, PhraseBuffer> buffers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSequences = new ConcurrentHashMap<>();
    private PhraseRepeater repeater;
    private PlasmoVoiceServer voiceServer;
    private ServerSourceLine sourceLine;
    private ServerActivation proximityActivation;
    private final ServerActivation.PlayerActivationStartListener activationStartListener = this::onActivationStart;
    private final ServerActivation.PlayerActivationListener activationListener = this::onActivation;
    private final ServerActivation.PlayerActivationEndListener activationEndListener = this::onActivationEnd;
    private boolean registered;

    PlasmoVoiceBridge(PlasmoParrotsPlugin plugin, PhraseRepeater repeater) {
        this.plugin = plugin;
        this.repeater = repeater;
    }

    void enable(PlasmoVoiceServer voiceServer) {
        if (registered) return;

        if (voiceServer == null) {
            plugin.getLogger().warning("Plasmo Voice API was not injected; parrot repeats are disabled.");
            return;
        }

        this.voiceServer = voiceServer;

        // Create a dedicated source line for parrot replays
        sourceLine = voiceServer.getSourceLineManager()
                .createBuilder(plugin, "plasmo_parrots", "Parrots", "minecraft:textures/item/parrot_spawn_egg.png", 0)
                .withPlayers(true)
                .setDefaultVolume(plugin.settings().parrotVolume())
                .build();

        proximityActivation = voiceServer.getActivationManager()
                .getActivationByName(VoiceActivation.PROXIMITY_NAME)
                .orElse(null);
        if (proximityActivation == null) {
            plugin.getLogger().warning("Plasmo Voice proximity activation was not found; parrot repeats are disabled.");
            voiceServer.getSourceLineManager().unregister(sourceLine);
            sourceLine = null;
            return;
        }

        proximityActivation.onPlayerActivationStart(activationStartListener);
        proximityActivation.onPlayerActivation(activationListener);
        proximityActivation.onPlayerActivationEnd(activationEndListener);
        voiceServer.getEventBus().register(plugin, PlayerSpeakEvent.class, EventPriority.HIGHEST, this::onSpeakEvent);
        voiceServer.getEventBus().register(plugin, PlayerSpeakEndEvent.class, EventPriority.HIGHEST, this::onSpeakEndEvent);
        registered = true;

        plugin.getLogger().info("Hooked into Plasmo Voice " + voiceServer.getVersion() + ". Parrot repeats enabled.");
        plugin.getLogger().info("PlasmoParrots debug is " + (plugin.settings().debug() ? "enabled" : "disabled") + ".");
    }

    void disable() {
        if (registered && voiceServer != null) {
            if (sourceLine != null) {
                List.copyOf(sourceLine.getSources()).forEach(sourceLine::removeSource);
                voiceServer.getSourceLineManager().unregister(sourceLine);
            }
            if (proximityActivation != null) {
                proximityActivation.removePlayerActivationStartListener(activationStartListener);
                proximityActivation.removePlayerActivationListener(activationListener);
                proximityActivation.removePlayerActivationEndListener(activationEndListener);
            }
            voiceServer.getEventBus().unregister(plugin);
        }
        buffers.clear();
        lastSequences.clear();
        registered = false;
        sourceLine = null;
        proximityActivation = null;
        voiceServer = null;
    }

    void flushIdlePhrases() {
        for (PhraseBuffer phrase : List.copyOf(buffers.values())) {
            if (phrase.idleMillis() < 450L) continue;
            buffers.remove(phrase.playerId());
            repeater.maybeRepeat(phrase);
        }
    }

    /**
     * Replay buffered decrypted Opus packets through a ServerEntitySource attached to the parrot.
     * Pitch shift is baked into the packets: decode Opus to PCM, resample the PCM to
     * raise pitch, then encode it back to Opus. Sending the original packets faster is
     * not enough because clients can smooth the timing and still play the low voice.
     * Plasmo Voice source packets must be encrypted before they are sent to clients.
     */
    void playParrotRepeat(Player player, Parrot parrot, List<byte[]> packets, boolean stereo, double pitchFactor, ReplayEffect effect, long startDelayMillis) {
        if (sourceLine == null || packets.isEmpty()) {
            playParrotSfx(parrot, pitchFactor);
            return;
        }

        long startDelayTicks = Math.max(0L, Math.round(Math.max(startDelayMillis, MIN_REPLAY_DELAY_MS) / 50D));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                McServerEntity entity = voiceServer.getMinecraftServer().getEntityByInstance(parrot);
                ServerEntitySource source = sourceLine.createEntitySource(entity, false, OPUS_CODEC);
                source.setName("Parrot");

                short distance = (short) Math.max(1, Math.min(255, plugin.settings().parrotRadius() * 2));

                voiceServer.getBackgroundExecutor().execute(() -> {
                    try {
                        List<byte[]> playbackPackets = packets;
                        try {
                            playbackPackets = pitchShiftPackets(packets, stereo, pitchFactor, effect);
                        } catch (CodecException e) {
                            debug("pitch shift failed, replaying original voice packets: " + e.getMessage());
                        }

                        for (int i = 0; i < playbackPackets.size(); i++) {
                            byte[] data = encryptAudioFrame(playbackPackets.get(i));
                            long scheduleDelay = i * FRAME_MS;
                            long sequence = i;

                            voiceServer.getBackgroundExecutor().schedule(() -> source.sendAudioFrame(data, sequence, distance), scheduleDelay, TimeUnit.MILLISECONDS);
                        }

                        long playbackMillis = playbackPackets.size() * FRAME_MS;
                        long totalTicks = Math.max(1, (long) Math.ceil(playbackMillis / 50D)) + 5;
                        long endSequence = playbackPackets.size();
                        voiceServer.getBackgroundExecutor().schedule(() -> source.sendAudioEnd(endSequence, distance), playbackMillis, TimeUnit.MILLISECONDS);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> sourceLine.removeSource(source), totalTicks);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> playParrotSfx(parrot, pitchFactor), Math.max(2L, totalTicks - 2L));
                    } catch (Exception e) {
                        debug("pitch shift failed, using parrot sfx only: " + e.getMessage());
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sourceLine.removeSource(source);
                            playParrotSfx(parrot, pitchFactor);
                        });
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Parrot replay failed: " + e.getMessage());
                playParrotSfx(parrot, pitchFactor);
            }
        }, startDelayTicks);
    }

    private List<byte[]> pitchShiftPackets(List<byte[]> packets, boolean stereo, double pitchFactor, ReplayEffect effect) throws CodecException {
        double factor = Math.max(1.05D, Math.min(MAX_CLEAN_PITCH_FACTOR, pitchFactor));
        int channels = stereo ? 2 : 1;
        int frameSamples = OPUS_FRAME_SAMPLES * channels;
        try (AudioDecoder decoder = voiceServer.createOpusDecoder(stereo);
             AudioEncoder encoder = voiceServer.createOpusEncoder(stereo)) {
            decoder.open();
            encoder.open();

            short[] pcm = decodePcm(decoder, packets, frameSamples);
            removeDcOffset(pcm, channels);
            applyAntiAliasLowPass(pcm, channels, factor);
            short[] shifted = resampleForPitch(pcm, factor, channels);
            shifted = applyPcmParrotEffect(shifted, frameSamples, channels, effect, ThreadLocalRandom.current());
            normalizePeak(shifted, PCM_TARGET_PEAK);
            fadeEdges(shifted, channels);
            shifted = padWithSilence(shifted, channels, PCM_SILENCE_PAD_FRAMES);

            List<byte[]> encoded = new java.util.ArrayList<>(Math.max(1, shifted.length / frameSamples));
            for (int offset = 0; offset < shifted.length; offset += frameSamples) {
                short[] frame = new short[frameSamples];
                int length = Math.min(frameSamples, shifted.length - offset);
                System.arraycopy(shifted, offset, frame, 0, length);
                encoded.add(encoder.encode(frame));
            }
            return encoded.isEmpty() ? packets : List.copyOf(encoded);
        }
    }

    private short[] applyPcmParrotEffect(short[] input, int frameSamples, int channels, ReplayEffect effect, ThreadLocalRandom random) {
        if (input.length <= frameSamples) return input;

        short[] output = new short[input.length + tailLength(input, frameSamples, effect, random)];
        int totalFrames = input.length / channels;
        double phase = random.nextDouble(Math.PI * 2D);
        double rate = random.nextDouble(7.5D, 12.5D) / 48_000D;
        double tremoloDepth = Math.min(0.16D, 0.04D + (effect.stutterChance() * 0.22D) + (effect.burstChance() * 0.10D));
        double brightClip = Math.min(0.10D, 0.025D + (effect.jumpBackChance() * 0.12D));

        for (int frame = 0; frame < totalFrames; frame++) {
            double envelope = 1D - (tremoloDepth * (0.5D + (0.5D * Math.sin(phase + (frame * rate * Math.PI * 2D)))));
            for (int channel = 0; channel < channels; channel++) {
                int index = (frame * channels) + channel;
                double sample = input[index] * envelope;
                if (brightClip > 0D) {
                    double normalized = sample / Short.MAX_VALUE;
                    sample = ((normalized * (1D + brightClip)) - (normalized * normalized * normalized * brightClip)) * Short.MAX_VALUE;
                }
                output[index] = clampSample(Math.round(sample));
            }
        }

        if (output.length > input.length) {
            appendSmoothTail(input, output, input.length, frameSamples, channels);
        }
        return output;
    }

    private int tailLength(short[] input, int frameSamples, ReplayEffect effect, ThreadLocalRandom random) {
        int frames = input.length / frameSamples;
        if (frames <= 4 || random.nextDouble() >= effect.tailRepeatChance()) return 0;

        int tailFrames = Math.min(randomInt(random, 1, Math.min(3, frames)), frames);
        return tailFrames * frameSamples;
    }

    private void appendSmoothTail(short[] input, short[] output, int outputOffset, int frameSamples, int channels) {
        int tailSamples = output.length - outputOffset;
        int inputOffset = Math.max(0, input.length - tailSamples);
        int fadeSamples = Math.min(PCM_TAIL_CROSSFADE_FRAMES * channels, Math.min(outputOffset, tailSamples));
        for (int i = 0; i < tailSamples; i++) {
            double fadeOut = 1D - ((double) i / tailSamples);
            short sample = clampSample(Math.round(input[inputOffset + i] * fadeOut * 0.45D));
            if (i < fadeSamples) {
                double weight = (double) (i + 1) / (fadeSamples + 1);
                int outputIndex = outputOffset - fadeSamples + i;
                output[outputIndex] = clampSample(Math.round((output[outputIndex] * (1D - weight)) + (sample * weight)));
            }
            output[outputOffset + i] = sample;
        }
    }

    private short[] decodePcm(AudioDecoder decoder, List<byte[]> packets, int frameSamples) throws CodecException {
        short[] pcm = new short[packets.size() * frameSamples];
        int offset = 0;
        int skipped = 0;
        for (byte[] packet : packets) {
            short[] decoded;
            try {
                decoded = decoder.decode(packet);
            } catch (CodecException e) {
                skipped++;
                decoder.reset();
                continue;
            }
            if (offset + decoded.length > pcm.length) {
                short[] expanded = new short[Math.max(offset + decoded.length, pcm.length * 2)];
                System.arraycopy(pcm, 0, expanded, 0, offset);
                pcm = expanded;
            }
            System.arraycopy(decoded, 0, pcm, offset, decoded.length);
            offset += decoded.length;
        }

        if (offset == 0) {
            throw new CodecException("No valid Opus frames decoded from " + packets.size() + " packets");
        }
        if (skipped > 0) {
            debug("pitch shift skipped " + skipped + " invalid Opus frames");
        }

        short[] result = new short[offset];
        System.arraycopy(pcm, 0, result, 0, offset);
        return result;
    }

    private byte[] decryptAudioFrame(byte[] data) throws EncryptionException {
        return voiceServer.getDefaultEncryption().decrypt(data);
    }

    private byte[] encryptAudioFrame(byte[] data) throws EncryptionException {
        return voiceServer.getDefaultEncryption().encrypt(data);
    }

    private short[] resampleForPitch(short[] input, double factor, int channels) {
        if (input.length == 0) return input;

        int inputFrames = input.length / channels;
        int outputFrames = Math.max(OPUS_FRAME_SAMPLES, (int) Math.ceil(inputFrames / factor));
        int outputLength = outputFrames * channels;
        short[] output = new short[outputLength];
        for (int frame = 0; frame < outputFrames; frame++) {
            double sourceFrame = frame * factor;
            int leftFrame = Math.min(inputFrames - 1, (int) sourceFrame);
            int rightFrame = Math.min(inputFrames - 1, leftFrame + 1);
            double fraction = sourceFrame - leftFrame;
            for (int channel = 0; channel < channels; channel++) {
                int left = (leftFrame * channels) + channel;
                int right = (rightFrame * channels) + channel;
                output[(frame * channels) + channel] = clampSample(Math.round(input[left] + ((input[right] - input[left]) * fraction)));
            }
        }
        return output;
    }

    private short[] padWithSilence(short[] samples, int channels, int padFrames) {
        int padSamples = padFrames * channels;
        short[] padded = new short[samples.length + (padSamples * 2)];
        System.arraycopy(samples, 0, padded, padSamples, samples.length);
        return padded;
    }

    private void fadeEdges(short[] samples, int channels) {
        int fadeFrames = Math.min(PCM_EDGE_FADE_FRAMES, samples.length / channels / 4);
        if (fadeFrames <= 0) return;

        for (int frame = 0; frame < fadeFrames; frame++) {
            double in = (double) frame / fadeFrames;
            double out = (double) frame / fadeFrames;
            int headFrame = frame * channels;
            int tailFrame = (samples.length / channels - 1 - frame) * channels;
            for (int channel = 0; channel < channels; channel++) {
                samples[headFrame + channel] = clampSample(Math.round(samples[headFrame + channel] * in));
                samples[tailFrame + channel] = clampSample(Math.round(samples[tailFrame + channel] * out));
            }
        }
    }

    private void removeDcOffset(short[] samples, int channels) {
        if (samples.length == 0) return;

        long[] sums = new long[channels];
        int frames = samples.length / channels;
        for (int frame = 0; frame < frames; frame++) {
            for (int channel = 0; channel < channels; channel++) {
                sums[channel] += samples[(frame * channels) + channel];
            }
        }

        for (int channel = 0; channel < channels; channel++) {
            long offset = Math.round((double) sums[channel] / frames);
            if (offset == 0L) continue;
            for (int frame = 0; frame < frames; frame++) {
                int index = (frame * channels) + channel;
                samples[index] = clampSample(samples[index] - offset);
            }
        }
    }

    private void applyAntiAliasLowPass(short[] samples, int channels, double pitchFactor) {
        if (samples.length <= channels) return;

        double alpha = Math.max(0.28D, Math.min(0.62D, 0.78D / pitchFactor));
        for (int channel = 0; channel < channels; channel++) {
            double previous = samples[channel];
            for (int frame = 1; frame < samples.length / channels; frame++) {
                int index = (frame * channels) + channel;
                previous += alpha * (samples[index] - previous);
                samples[index] = clampSample(Math.round(previous));
            }
        }
    }

    private void normalizePeak(short[] samples, int targetPeak) {
        int peak = 0;
        for (short sample : samples) {
            peak = Math.max(peak, Math.abs((int) sample));
        }
        if (peak == 0 || peak <= targetPeak) return;

        double gain = (double) targetPeak / peak;
        for (int i = 0; i < samples.length; i++) {
            samples[i] = clampSample(Math.round(samples[i] * gain));
        }
    }

    private short clampSample(long sample) {
        if (sample > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (sample < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) sample;
    }

    private int randomInt(ThreadLocalRandom random, int min, int max) {
        if (max <= min) return min;
        return random.nextInt(min, max + 1);
    }

    private void playParrotSfx(Parrot parrot, double pitchFactor) {
        if (!parrot.isValid()) return;
        float pitch = (float) Math.min(2.0, pitchFactor);
        float volume = (float) Math.min(1.0D, plugin.settings().parrotVolume() * 0.35D);
        parrot.getWorld().playSound(parrot.getLocation(), Sound.ENTITY_PARROT_AMBIENT, volume, pitch);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (parrot.isValid())
                parrot.getWorld().playSound(parrot.getLocation(), Sound.ENTITY_PARROT_IMITATE_ENDERMITE, volume * 0.65F, 2.0F);
        }, 3L);
    }

    private void onActivationStart(VoicePlayer player) {
        UUID id = player.getInstance().getUuid();
        buffers.remove(id);
        lastSequences.remove(id);
        debug("activation start: " + id);
    }

    private ServerActivation.Result onActivation(VoicePlayer player, PlayerAudioPacket packet) {
        return ServerActivation.Result.IGNORED;
    }

    void reload() {
        PlasmoVoiceServer server = voiceServer;
        boolean shouldReenable = registered && server != null;
        disable();
        if (shouldReenable) enable(server);
    }

    boolean isReady() {
        return registered && sourceLine != null;
    }

    int bufferedPhrases() {
        return buffers.size();
    }

    private ServerActivation.Result onActivationEnd(VoicePlayer player, PlayerAudioEndPacket packet) {
        UUID id = player.getInstance().getUuid();
        debug("activation end: " + id);
        finishPhrase(id);
        return ServerActivation.Result.IGNORED;
    }

    private void onSpeakEvent(PlayerSpeakEvent event) {
        if (event.isCancelled()) {
            bufferPacket("cancelled speak event packet", event.getPlayer(), event.getPacket());
            return;
        }

        bufferPacket("speak event packet", event.getPlayer(), event.getPacket());
    }

    private void onSpeakEndEvent(PlayerSpeakEndEvent event) {
        UUID id = event.getPlayer().getInstance().getUuid();
        if (event.isCancelled()) {
            debug("speak end event cancelled: " + id);
            finishPhrase(id, false);
            return;
        }

        debug("speak event end: " + id);
        finishPhrase(id, true);
    }

    private void bufferPacket(String source, VoicePlayer player, PlayerAudioPacket packet) {
        UUID id = player.getInstance().getUuid();
        long sequence = packet.getSequenceNumber();
        Long previousSequence = lastSequences.put(id, sequence);
        if (previousSequence != null && previousSequence == sequence) {
            return;
        }

        byte[] decrypted;
        try {
            decrypted = decryptAudioFrame(packet.getData());
        } catch (EncryptionException e) {
            debug(source + ": skipped encrypted packet for " + id + ", decrypt failed: " + e.getMessage());
            return;
        }

        PhraseBuffer buffer = buffers.computeIfAbsent(id, k -> new PhraseBuffer(k, plugin.settings().maxBufferedPackets()));
        boolean wasEmpty = buffer.isEmpty();
        buffer.add(decrypted, packet.isStereo());
        if (wasEmpty && !buffer.isEmpty()) {
            debug(source + ": started phrase buffer for " + id);
        }
        if (buffer.ageMillis() >= plugin.settings().maxPhraseMillis()) {
            debug(source + ": max phrase window reached for " + id + ", duration=" + buffer.durationMillis() + "ms");
            finishPhrase(id);
        }
    }

    private void finishPhrase(UUID id) {
        finishPhrase(id, true);
    }

    private void finishPhrase(UUID id, boolean debugIfMissing) {
        lastSequences.remove(id);
        PhraseBuffer phrase = buffers.remove(id);
        if (phrase == null) {
            if (debugIfMissing) debug("speak end: no buffered phrase for " + id);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> repeater.maybeRepeat(phrase));
    }

    private void debug(String message) {
        if (plugin.settings().debug()) plugin.getLogger().info("[debug] " + message);
    }
}
