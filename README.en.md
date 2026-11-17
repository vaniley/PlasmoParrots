![parrot](./.github/parrot.webp)

[🇷🇺 Русский](./README.md) · 🇬🇧 English

# PlasmoParrots

**PlasmoParrots** is a plugin for Paper, Folia, and Plasmo Voice (a Plasmo Voice add-on) that lets nearby parrots overhear fragments of players' real speech and repeat them back in funny, squeaky "parrot" voices.

The plugin does not generate text, subtitles, jokes, or pre-recorded lines. Every repeat is built solely from a real voice message a player just spoke through Plasmo Voice.

## Features

* Buffers short fragments of player speech from Plasmo Voice.
* Lets one or several parrots reply from their own positions.
* Repeats phrases with a raised pitch, shaky tremolo, bright "chirping" bursts, and characteristic "parrot" tails.
* Adds a dedicated **Parrots** volume slider to the Plasmo Voice interface.
* Ships with a set of playful presets that make the sound more toy-like, chaotic, and high-pitched.

## Requirements

* Paper `1.21.11` through `26.2`, or a matching Folia server. CI compiles and runs unit tests against both Paper API endpoints.
* Java `21` on Paper `1.21.11`; newer Paper versions need the Java version required by the server.
* Plasmo Voice server plugin `2.1.17` or a compatible version.
* Players need the Plasmo Voice client mod to hear the repeats.

## Installation

1. Install JDK 25+ and build against Paper 26.2:

```bash
./scripts/build-plugin.sh --clean
```

To verify Paper 1.21.11 and Java 21 compatibility:

```bash
./scripts/build-plugin.sh --clean --compat-1.21.11
```

Both builds run the tests and produce a Java 21-compatible JAR. The Maven wrapper downloads and verifies Maven `3.9.16`.

2. Copy `target/PlasmoParrots-*.jar` into your server's `plugins/` folder.
3. Make sure Plasmo Voice is installed on the server.
4. Start or restart the server.

## Folia

The same JAR runs on Paper and Folia. Player and parrot operations use entity schedulers, so tasks follow entities across regions.
Searches that cross region boundaries use separate queries in each loaded chunk. The plugin does not intentionally load chunks for searches.
A search waits up to 40 global ticks for chunk results, then uses the results available at that point.
Audio processing and 20 ms packet delivery remain on the Plasmo Voice background executor.

Build and mocked scheduler tests do not replace a live multiplayer test. Before production use, check speech playback, region boundaries,
teleports, parrot removal, and config reloads on your exact Folia and Plasmo Voice versions.

## Default behaviour

The default configuration is intentionally tuned to make the parrots active and fun:

* they repeat speech a bit more often;
* they use shorter, more expressive fragments;
* several parrots can reply at once;
* the sound is noticeably higher in pitch;
* the effects lean harder into squeaks, chirps, "helium" voices, and toy whistles.

As a result, the plugin instantly creates a "possessed plush parrots" feeling without turning voice chat into total chaos.

## Configuration

After the first launch the following file is created:

```text
plugins/PlasmoParrots/config.yml
```

### Useful parameters

* `repeat-chance` — the chance that a selected parrot decides to repeat a phrase.
* `repeat-duration-*` — the length of the repeated speech fragment.
* `pitch-factor` — the overall pitch multiplier applied before per-effect pitch.
* `parrots-max` and `parrot-stagger-*` — control whether single parrots or a whole flock reply.
* `effects` — the set of effects responsible for how "broken-toy" the sound gets.
* `phrase-idle-millis` — the silence after which a phrase is closed.
* `max-concurrent-replays` — the limit for delayed, preparing, and streaming replays together.

Existing keys and default values remain unchanged. Resource settings have safety caps: radius `64` blocks,
parrots and concurrent replays `64`, buffered packets `3000`, phrase/repeat/stagger windows `60000` ms,
and player cooldown `3600000` ms. Larger values are clamped on load.
A full packet buffer closes the phrase immediately. Packets with a different mono/stereo mode are ignored within that phrase.
Setting `repeat-chance` to `0` also skips audio capture and decryption.

If the server starts sounding too noisy, reduce these first:

* `repeat-chance`
* `parrots-max`
* `pitch-factor`

## Commands

### `/plasmoparrots status`

Shows the integration state and the plugin's current settings.

### `/plasmoparrots reload`

Reloads `config.yml`, clears the buffers, and re-registers the audio source.

### `/plasmoparrots debug on|off`

Enables or disables debug mode and saves the setting to the configuration.

### Aliases

```text
/pparrots
/parrots
```

### Permissions

```text
plasmoparrots.admin
plasmoparrots.status
plasmoparrots.reload
```

## Plasmo Voice integration

PlasmoParrots registers a dedicated audio source named **Parrots**.

Players can change its volume from the menu:

```text
Plasmo Voice → Volume → Sources Volume → Parrots
```

This slider does not affect normal voice chat, so you can keep player speech loud and make the parrots quieter.

## Privacy

Audio is kept briefly in memory and is never written to disk. The plugin receives audio only through the standard Plasmo Voice proximity activation after its requirements pass; it does not intercept the global stream of raw voice events.

## Author

**Lazarrew**
