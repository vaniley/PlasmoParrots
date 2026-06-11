# PlasmoParrots

PlasmoParrots is a Paper plugin for Plasmo Voice that makes nearby parrots steal bits of real player speech and spit them back as squeaky, toy-like chirps.

It does not invent text, subtitles, jokes, or canned voice lines. Every repeat is cut from actual Plasmo Voice audio that a player just said.

## What It Does

- Buffers short fragments of nearby Plasmo Voice speech.
- Lets one or several parrots answer from their own positions.
- Replays those fragments with raised pitch, stutters, chirp bursts, jump-backs, chopped syllables, and squawk tails.
- Registers a dedicated `Parrots` source volume slider in the Plasmo Voice UI.
- Ships with playful default presets tuned to sound more toy-like, more chaotic, and noticeably higher.

## Requirements

- Paper `1.21.4` or compatible.
- Java `21`.
- Plasmo Voice server plugin `2.1.9` or compatible.
- Players need the Plasmo Voice client/mod to hear the replayed voice.

## Installation

1. Build the jar with `./mvnw clean package`.
2. Copy `target/PlasmoParrots-1.0.0.jar` into your server `plugins/` directory.
3. Ensure `PlasmoVoice` is installed on the same server.
4. Start or restart the server.

## Default Feel

The default config is intentionally lively:

- repeats happen a bit more often;
- fragments are shorter and punchier;
- multiple parrots can join the same reply;
- the base pitch is higher;
- presets lean harder into squeaks, chatter, helium squawks, and toy-whistle energy.

That gives the plugin a stronger "possessed parrot plushie" vibe out of the box without turning every sentence into pure noise.

## Configuration

PlasmoParrots creates its config at `plugins/PlasmoParrots/config.yml`.

```yaml
# Base chance for each nearby selected parrot to repeat a phrase.
# Every parrot rolls separately, so one can answer while the others ignore it.
repeat-chance: 0.58

# Maximum continuous phrase window captured before the plugin closes the buffer.
# Longer speech is split into another phrase window instead of growing forever.
max-phrase-millis: 5000

# Random fragment length that each parrot will repeat.
repeat-duration-min-millis: 420
repeat-duration-max-millis: 1900

# Parrots are searched around the speaking player in this radius.
parrot-radius: 12.0

# How many nearby parrots can repeat one phrase.
parrots-min: 1
parrots-max: 4

# Extra random delay per parrot.
parrot-stagger-min-millis: 60
parrot-stagger-max-millis: 680

# One parrot chorus per player within this cooldown.
player-cooldown-millis: 3200

# Limits memory and packet spam if somebody speaks for too long.
max-buffered-packets: 220

# Real server-side pitch multiplier.
pitch-factor: 1.28

# Default volume for the Parrots source line in the Plasmo Voice menu.
parrot-volume: 0.9

# Random replay effects. Higher weight = selected more often.
effects:
  - name: squeaky-mimic
    weight: 7
    pitch-min: 1.18
    pitch-max: 1.34
    stutter-chance: 0.16
    stutter-repeats-min: 1
    stutter-repeats-max: 3
    reverse-chance: 0.0
    drop-chance: 0.010
    scramble-chance: 0.02
    jump-back-chance: 0.12
    tail-repeat-chance: 0.50
    burst-chance: 0.18
    burst-length-min: 2
    burst-length-max: 4
  - name: toy-whistle
    weight: 6
    pitch-min: 1.24
    pitch-max: 1.42
    stutter-chance: 0.12
    stutter-repeats-min: 1
    stutter-repeats-max: 2
    reverse-chance: 0.0
    drop-chance: 0.006
    scramble-chance: 0.01
    jump-back-chance: 0.09
    tail-repeat-chance: 0.40
    burst-chance: 0.14
    burst-length-min: 2
    burst-length-max: 3
  - name: chatter-beak
    weight: 4
    pitch-min: 1.12
    pitch-max: 1.28
    stutter-chance: 0.22
    stutter-repeats-min: 2
    stutter-repeats-max: 4
    reverse-chance: 0.0
    drop-chance: 0.012
    scramble-chance: 0.03
    jump-back-chance: 0.20
    tail-repeat-chance: 0.56
    burst-chance: 0.20
    burst-length-min: 2
    burst-length-max: 4
  - name: helium-squawk
    weight: 3
    pitch-min: 1.34
    pitch-max: 1.60
    stutter-chance: 0.18
    stutter-repeats-min: 2
    stutter-repeats-max: 4
    reverse-chance: 0.0
    drop-chance: 0.014
    scramble-chance: 0.04
    jump-back-chance: 0.16
    tail-repeat-chance: 0.62
    burst-chance: 0.24
    burst-length-min: 2
    burst-length-max: 5

# Prints why a phrase was or was not repeated.
debug: false
```

### Tuning Notes

- `repeat-chance` controls how often each selected parrot decides to answer.
- `repeat-duration-*` controls how clipped and punchy the mimic line feels.
- `pitch-factor` raises the entire replay before per-effect random pitch kicks in.
- `parrots-max` and `parrot-stagger-*` shape whether replies feel like one bird or a chaotic flock.
- `effects` control how "broken toy" the response becomes.

If the server starts sounding too busy, lower `repeat-chance`, `parrots-max`, or `pitch-factor` first.

## Commands

- `/plasmoparrots status` shows bridge readiness and active runtime settings.
- `/plasmoparrots reload` reloads `config.yml`, clears buffers, and re-registers the source line.
- `/plasmoparrots debug on|off` toggles debug logging and saves the value to config.

Aliases: `/pparrots`, `/parrots`

Permissions:

- `plasmoparrots.admin`
- `plasmoparrots.reload`

## Plasmo Voice Integration

PlasmoParrots registers a separate source called `Parrots`.

Players can change its volume here:

`Plasmo Voice -> Volume -> Sources Volume -> Parrots`

That slider is separate from normal proximity chat, so players can keep voice chat loud while toning down the bird chaos.

## How It Works

When a player speaks, the plugin buffers a short stream of processed Plasmo Voice Opus packets from `PlayerSpeakEvent`. Once the phrase ends, or once the phrase window limit is reached, it selects nearby parrots and asks them to replay a random fragment through Plasmo Voice entity sources attached to those parrots.

To make the result sound genuinely higher instead of just filtered, the plugin decodes Opus to PCM, pitch-shifts the PCM upward by resampling, applies replay effects like stutters and jump-backs, and then encodes the result back to Opus for playback.

## Troubleshooting

- If parrots never answer, confirm that `PlasmoVoice` is loaded and players are actually speaking through the client/mod.
- If replies are too rare, raise `repeat-chance` or lower `player-cooldown-millis`.
- If replies are too chaotic, reduce `parrots-max`, `burst-chance`, `tail-repeat-chance`, or `pitch-factor`.
- If you need to inspect behavior, set `debug: true` temporarily and watch the server log.

## Building

```bash
./mvnw clean package
```

The final plugin jar is written to `target/`.
