#!/usr/bin/env python3
"""Generates UltimateAlarm's bundled sound pack (docs/spec/02-diseno.md §7).

Every tone is synthesised here from scratch (no samples), so the pack is original work under the project's licence.
Deterministic: a fixed seed and fixed parameters give the same WAV every time; ffmpeg then normalises loudness and
encodes OGG Opus with bit-exact flags. Loops are seamless: note tails that run past the end are wrapped to the start.

Usage: python3 tools/sounds/generate.py OUT_DIR      (needs numpy and ffmpeg with libopus)
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

RATE = 48_000
RNG = np.random.default_rng(20261016)


def note(name):
    """'A4' -> Hz (equal temperament, A4 = 440)."""
    names = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}
    pitch, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((names[pitch] + 12 * (octave - 4)) / 12)


def envelope(n, attack, decay):
    t = np.arange(n) / RATE
    env = np.exp(-t / decay)
    a = max(1, int(attack * RATE))
    env[:a] *= np.linspace(0, 1, a)
    return env


def marimba(freq, length=2.0, bright=1.0):
    n = int(length * RATE)
    t = np.arange(n) / RATE
    tone = np.sin(2 * np.pi * freq * t) + 0.25 * bright * np.sin(2 * np.pi * freq * 3.9 * t) * np.exp(-t / 0.08)
    return tone * envelope(n, 0.004, 0.45)


def bell(freq, length=4.0):
    n = int(length * RATE)
    t = np.arange(n) / RATE
    partials = [(1.0, 1.0, 1.6), (2.0, 0.5, 1.1), (2.76, 0.35, 0.8), (5.4, 0.15, 0.4), (8.93, 0.08, 0.25)]
    tone = sum(a * np.sin(2 * np.pi * freq * r * t) * np.exp(-t / d) for r, a, d in partials)
    return tone * envelope(n, 0.002, 10.0)


def pluck(freq, length=1.5, damping=0.996):
    """Karplus-Strong string, seeded noise burst."""
    n = int(length * RATE)
    period = max(2, int(RATE / freq))
    buf = RNG.uniform(-1, 1, period)
    out = np.empty(n)
    for i in range(n):
        out[i] = buf[i % period]
        buf[i % period] = damping * 0.5 * (buf[i % period] + buf[(i + 1) % period])
    return out * envelope(n, 0.001, 0.6)


def pad(freqs, length):
    n = int(length * RATE)
    t = np.arange(n) / RATE
    tone = sum(np.sin(2 * np.pi * f * d * t + RNG.uniform(0, 6.28)) for f in freqs for d in (0.997, 1.0, 1.003))
    swell = np.sin(np.pi * np.arange(n) / n) ** 2
    return tone * swell


def square_pulse(freq, length, duty=0.5):
    n = int(length * RATE)
    t = np.arange(n) / RATE
    wave_ = np.sign(np.sin(2 * np.pi * freq * t) - (1 - 2 * duty) * 0)
    # Soften the edges: a square wave filtered by a short moving average.
    kernel = np.ones(24) / 24
    return np.convolve(wave_, kernel, mode="same") * envelope(n, 0.003, 10.0)


def sequence(events, total):
    """events: (start_seconds, samples). Tails past the end wrap to the start (seamless loop)."""
    n = int(total * RATE)
    out = np.zeros(n)
    for start, samples in events:
        s = int(start * RATE) % n
        for i in range(0, len(samples), n):
            chunk = samples[i:i + n]
            first = min(len(chunk), n - s)
            out[s:s + first] += chunk[:first]
            out[:len(chunk) - first] += chunk[first:]
    return out


def melody(instrument, notes, step, total, repeats_gap=0.0, **kw):
    events = []
    time = 0.0
    while time < total - 0.01:
        for name in notes:
            if time >= total - 0.01:
                break
            if name:
                events.append((time, instrument(note(name), **kw)))
            time += step
        time += repeats_gap
    return sequence(events, total)


def tones():
    """id -> (category, samples, target LUFS). Categories: gentle, energetic, backup, notification."""
    c_major = ["C5", "E5", "G5", "C6", "G5", "E5"]
    pent = ["D5", "E5", "G5", "A5", "B5", "A5", "G5", "E5"]
    yield "dawn_marimba", "gentle", melody(marimba, c_major, 0.5, 24.0, repeats_gap=1.0), -16
    yield "morning_bells", "gentle", melody(bell, ["E5", "B4", "", "G#5", "", "E5", "", ""], 1.0, 32.0), -16
    yield "soft_pad", "gentle", sequence([(i * 8.0, pad([note(a), note(b), note(c)], 10.0)) for i, (a, b, c) in
                                          enumerate([("C4", "E4", "G4"), ("A3", "C4", "E4"), ("F3", "A3", "C4"), ("G3", "B3", "D4")])], 32.0), -16
    yield "music_box", "gentle", melody(pluck, ["E6", "G6", "C7", "B6", "G6", "E6", "D6", "", ""], 0.4, 28.8), -16
    yield "gentle_chimes", "gentle", melody(bell, ["C6", "", "G5", "", "A5", "", "E5", "", "", ""], 0.6, 30.0), -16
    yield "bright_marimba", "energetic", melody(marimba, pent, 0.25, 24.0, bright=1.8), -16
    yield "sunrise_arpeggio", "energetic", melody(marimba, ["C5", "E5", "G5", "C6", "E6", "G6", "E6", "C6"], 0.18, 23.04, bright=1.4), -16
    yield "wake_pluck", "energetic", melody(pluck, ["A4", "A4", "E5", "A5", "", "G5", "E5", "D5"], 0.25, 24.0), -16
    yield "rise_bells", "energetic", melody(bell, ["G5", "B5", "D6", "G6"], 0.35, 22.4, repeats_gap=0.6), -16
    yield "backup_pulse", "backup", melody(square_pulse, ["A5", "", "A5", "", "A5", "", "", ""], 0.12, 20.16, length=0.1), -12
    yield "backup_two_tone", "backup", melody(square_pulse, ["E6", "C6"], 0.3, 20.4, length=0.28), -12
    yield "eve_chime", "notification", sequence([(0.0, bell(note("G5"))), (0.25, bell(note("E6")))], 4.0), -16
    yield "timer_done", "notification", melody(marimba, ["C6", "E6", "G6", "", "C6", "E6", "G6", "", "", "", "", ""], 0.15, 3.6, bright=1.5), -16


def write_wav(path, samples):
    peak = np.max(np.abs(samples)) or 1.0
    pcm = (samples / peak * 0.8 * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for tone_id, category, samples, lufs in tones():
            wav = os.path.join(tmp, tone_id + ".wav")
            write_wav(wav, samples)
            ogg = os.path.join(out_dir, tone_id + ".ogg")
            subprocess.run([
                "ffmpeg", "-loglevel", "error", "-y", "-i", wav,
                "-af", f"loudnorm=I={lufs}:TP=-1.5:LRA=11",
                "-ar", "48000", "-ac", "1", "-c:a", "libopus", "-b:a", "64k",
                "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", ogg,
            ], check=True)
            print(f"{category:12} {tone_id}  {len(samples) / RATE:.1f}s  {lufs} LUFS")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "feature/sounds/src/main/assets/sounds")
