"""Synthesizes the game's sound effects into app/src/main/res/raw. Usage: python3 tools/sounds.py"""
import math
import os
import random
import struct
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def write(name, samples):
    peak = max(1e-9, max(abs(s) for s in samples))
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s / peak * 0.8 * 32767)) for s in samples))


def pop(freq, ms, noise=0.3, seed=1):
    rnd = random.Random(seed)
    n = int(RATE * ms / 1000)
    out = []
    for i in range(n):
        t = i / RATE
        env = math.exp(-t * 60)
        f = freq * (1 + 0.6 * math.exp(-t * 90))  # quick downward chirp
        out.append(env * (math.sin(2 * math.pi * f * t) + noise * math.exp(-t * 400) * rnd.uniform(-1, 1)))
    return out


def chime(notes, note_ms=110, tail_ms=500):
    n_total = int(RATE * (note_ms * len(notes) + tail_ms) / 1000)
    out = [0.0] * n_total
    for k, freq in enumerate(notes):
        start = int(RATE * note_ms * k / 1000)
        for i in range(n_total - start):
            t = i / RATE
            env = math.exp(-t * 5) * min(1, t * 400)
            out[start + i] += env * (math.sin(2 * math.pi * freq * t) + 0.3 * math.sin(4 * math.pi * freq * t))
    return out


write("snap", pop(880, 90))
write("join", pop(520, 80, noise=0.2, seed=2))
write("win", chime([523.25, 659.25, 783.99, 1046.5]))
