![parrot](./.github/parrot.webp)

[🇷🇺 Русский](./README.md) · 🇬🇧 English

# PlasmoParrots

**PlasmoParrots** is a plugin for Paper and Plasmo Voice (a Plasmo Voice add-on) that lets nearby parrots overhear fragments of players' real speech and repeat them back in funny, squeaky "parrot" voices.

The plugin does not generate text, subtitles, jokes, or pre-recorded lines. Every repeat is built solely from a real voice message a player just spoke through Plasmo Voice.

## Features

* Buffers short fragments of player speech from Plasmo Voice.
* Lets one or several parrots reply from their own positions.
* Repeats phrases with a raised pitch, shaky tremolo, bright "chirping" bursts, and characteristic "parrot" tails.
* Adds a dedicated **Parrots** volume slider to the Plasmo Voice interface.
* Ships with a set of playful presets that make the sound more toy-like, chaotic, and high-pitched.

## Requirements

* Paper `1.21.4` or a compatible version.
* Java `21`.
* Plasmo Voice server plugin `2.1.9` or a compatible version.
* Players need the Plasmo Voice client mod to hear the repeats.

## Installation

1. Build the plugin:

```bash
./mvnw clean package
```

2. Copy `target/PlasmoParrots-*.jar` into your server's `plugins/` folder.
3. Make sure Plasmo Voice is installed on the server.
4. Start or restart the server.

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
plasmoparrots.reload
```

## Plasmo Voice integration

PlasmoParrots registers a dedicated audio source named **Parrots**.

Players can change its volume from the menu:

```text
Plasmo Voice → Volume → Sources Volume → Parrots
```

This slider does not affect normal voice chat, so you can keep player speech loud and make the parrots quieter.
