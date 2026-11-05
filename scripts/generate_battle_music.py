#!/usr/bin/env python3
"""Generate three original, calm cinematic ambient battle loops for GENERAL."""
from pathlib import Path
import wave
import numpy as np

OUT = Path("app/src/main/res/raw")
RATE = 22050
DURATION = 28.0

TRACKS = [
    {"name":"battle_music_1.wav","chords":[[50,53,57],[46,50,53],[53,57,60],[50,53,57]],
     "melody":[74,72,69,67,69,72,74,77,76,74,72,69]},
    {"name":"battle_music_2.wav","chords":[[52,55,59],[48,52,55],[55,59,62],[52,55,59]],
     "melody":[76,74,71,69,71,74,76,79,78,76,74,71]},
    {"name":"battle_music_3.wav","chords":[[57,60,64],[53,57,60],[60,64,67],[57,60,64]],
     "melody":[81,79,76,74,76,79,81,84,83,81,79,76]},
]

def midi_hz(note):
    return 440.0 * (2.0 ** ((note - 69) / 12.0))

def smoothstep(x):
    x = np.clip(x, 0.0, 1.0)
    return x*x*(3.0-2.0*x)

def make_track(spec, index):
    count = int(RATE * DURATION)
    t = np.arange(count, dtype=np.float64) / RATE
    audio = np.zeros(count, dtype=np.float64)
    chord_len = DURATION / 4.0

    # Warm sustained pads, with slow transitions and no harsh percussion.
    for ci, chord in enumerate(spec["chords"]):
        start = ci * chord_len
        local = t - start
        active = (local >= 0) & (local < chord_len)
        x = np.maximum(local, 0.0)
        env = smoothstep(x / 1.15) * smoothstep((chord_len - x) / 1.35) * active
        for ni, note in enumerate(chord):
            f = midi_hz(note - 12)
            phase = 2*np.pi*f*t
            pad = np.sin(phase) + 0.22*np.sin(2*phase + 0.15*ni) + 0.07*np.sin(3*phase)
            detuned = np.sin(2*np.pi*(f*1.003)*t + 0.3)
            audio += env * (0.027 / len(chord)) * (pad + 0.20*detuned)

    # Gentle bass movement; intentionally no sharp drums or impact sounds.
    for beat in range(16):
        chord_i = beat // 4
        start = beat * (DURATION / 16.0)
        local = t - start
        env = np.exp(-np.maximum(local, 0.0)*1.8) * (local >= 0) * (local < DURATION/16.0)
        f = midi_hz(spec["chords"][chord_i][0] - 24)
        audio += 0.045 * env * np.sin(2*np.pi*f*t)

    # Sparse, rounded high notes keep each of the three themes distinct.
    note_len = DURATION / len(spec["melody"])
    for mi, note in enumerate(spec["melody"]):
        start = mi * note_len + 0.12
        local = t - start
        env = (local >= 0) * (local < note_len*0.78)
        attack = smoothstep(np.maximum(local, 0.0)/0.22)
        decay = np.exp(-np.maximum(local, 0.0)*1.15)
        f = midi_hz(note)
        bell = np.sin(2*np.pi*f*t) + 0.12*np.sin(2*np.pi*f*2.0*t)
        audio += 0.023 * env * attack * decay * bell

    pulse = 0.5 + 0.5*np.sin(2*np.pi*(0.10 + index*0.012)*t + index)
    audio *= 0.91 + 0.09*pulse
    fade = int(RATE * 0.12)
    audio[:fade] *= np.linspace(0.0, 1.0, fade)
    audio[-fade:] *= np.linspace(1.0, 0.0, fade)
    audio = np.tanh(audio * 1.65) * 0.78

    # Subtle stereo width, with a centered mix for phone speakers.
    stereo = np.stack([audio, np.roll(audio, int(RATE*0.003))*0.96], axis=1)
    pcm = np.clip(stereo * 32767, -32768, 32767).astype("<i2")
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / spec["name"]), "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(RATE)
        wf.writeframes(pcm.tobytes())
    print(f"Generated {OUT / spec['name']} ({DURATION:.0f}s)")

if __name__ == "__main__":
    for i, track in enumerate(TRACKS):
        make_track(track, i)
