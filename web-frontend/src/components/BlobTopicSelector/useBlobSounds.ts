/**
 * useBlobSounds — Tone.js procedural sound effects for the BlobTopicSelector.
 *
 * All sounds are synthesized (no audio files). Lazy-initialised on first unmute
 * (satisfies browser autoplay policy). Default: muted. Persisted to localStorage.
 *
 * Muting strategy: Tone.getDestination().mute = true/false
 * This bypasses the reverb-tail problem (ConvolverNode keeps playing after source
 * is silenced) and the async-race problem (synths created after a mute toggle).
 * Synths are created once and kept alive — only the destination is toggled.
 */
import { useRef, useCallback, useEffect, useState } from 'react';
// Type-only import: elided at compile time, causes no runtime execution.
// This keeps Tone namespace types available without triggering standardized-audio-context
// capability tests (which create AudioContexts before a user gesture).
import type * as Tone from 'tone';

// Tone.js is lazy-loaded on first unmute (user gesture) to avoid AudioContext
// autoplay-policy warnings from standardized-audio-context capability tests.
type ToneModule = typeof import('tone');
let _tone: ToneModule | null = null;
async function loadTone(): Promise<ToneModule> {
  if (!_tone) {
    _tone = await import('tone');
  }
  return _tone;
}

const STORAGE_KEY = 'batbern_blob_sound_muted';

export interface BlobSounds {
  playSlosh: () => void; // green blob absorbed by blue blob
  playSting: () => void; // red star absorbed by blue blob
  playFlop: () => void; // two blue blobs merge
  playKling: () => void; // blue blob selected (single click)
  playShutdown: () => void; // blue blob deleted
  isMuted: boolean;
  toggleMute: () => void;
}

export function useBlobSounds(): BlobSounds {
  const [isMuted, setIsMuted] = useState<boolean>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      // Default to muted on first visit (avoids autoplay policy issues)
      return stored === null ? true : stored === 'true';
    } catch {
      return true;
    }
  });

  // Stable ref for play-function guards (captured once in D3 callbacks)
  const isMutedRef = useRef(isMuted);
  useEffect(() => {
    isMutedRef.current = isMuted;
  }, [isMuted]);

  // Synth node refs — created once on first unmute, kept alive until unmount.
  // Muting toggles Tone.getDestination().mute, NOT the synth lifetimes.
  const synthsInitializedRef = useRef(false);
  const ambientRef = useRef<Tone.PolySynth | null>(null);
  const ambientReverbRef = useRef<Tone.Reverb | null>(null);
  const sloshSynthRef = useRef<Tone.PluckSynth | null>(null);
  const stingSynthRef = useRef<Tone.FMSynth | null>(null);
  const flopSynthRef = useRef<Tone.MembraneSynth | null>(null);
  const klingSynthRef = useRef<Tone.MetalSynth | null>(null);
  const shutdownSynthRef = useRef<Tone.Synth | null>(null);

  const disposeSynths = useCallback(() => {
    synthsInitializedRef.current = false;
    const safeDispose = (node: { dispose: () => void } | null) => {
      if (!node) return;
      try {
        node.dispose();
      } catch {
        /* ignore */
      }
    };
    try {
      ambientRef.current?.releaseAll();
    } catch {
      /* ignore */
    }
    safeDispose(ambientRef.current);
    ambientRef.current = null;
    safeDispose(ambientReverbRef.current);
    ambientReverbRef.current = null;
    safeDispose(sloshSynthRef.current);
    sloshSynthRef.current = null;
    safeDispose(stingSynthRef.current);
    stingSynthRef.current = null;
    safeDispose(flopSynthRef.current);
    flopSynthRef.current = null;
    safeDispose(klingSynthRef.current);
    klingSynthRef.current = null;
    safeDispose(shutdownSynthRef.current);
    shutdownSynthRef.current = null;
  }, []);

  /**
   * Creates all synths once. Guards against:
   * - Double-init (synthsInitializedRef)
   * - Async race: if user mutes while awaiting Tone.start(), bail out
   */
  const createSynths = useCallback(async () => {
    if (synthsInitializedRef.current) return;

    // loadTone() dynamically imports Tone.js — this is always called from a click handler,
    // satisfying the browser autoplay policy for AudioContext creation.
    const tone = await loadTone();
    await tone.start();

    // Guard: user may have re-muted while we were waiting for AudioContext
    if (isMutedRef.current) return;

    synthsInitializedRef.current = true;

    // ── Ambient space pad ────────────────────────────────────────────────────
    // A minor chord across two octaves (A1 E2 A2 E3), filtered through a long reverb.
    // Volume fades in from -60 dB to -28 dB over 3 s (barely audible — background atmosphere).
    const reverb = new tone.Reverb({ decay: 8, wet: 0.6 });
    reverb.toDestination();
    const pad = new tone.PolySynth(tone.Synth, {
      oscillator: { type: 'sine' },
      envelope: { attack: 3, decay: 0.1, sustain: 1, release: 3 },
    });
    pad.volume.value = -60; // start silent
    pad.connect(reverb);
    ambientRef.current = pad;
    ambientReverbRef.current = reverb;
    pad.triggerAttack(['A1', 'E2', 'A2', 'E3']);
    pad.volume.rampTo(-28, 3);

    // ── Slosh (green→blue absorption) ────────────────────────────────────────
    // PluckSynth uses Karplus-Strong string synthesis — naturally produces a
    // water-drop / liquid pluck sound with no manual frequency ramping needed.
    const slosh = new tone.PluckSynth({
      attackNoise: 1,
      dampening: 3000,
      resonance: 0.96,
    });
    slosh.volume.value = -4;
    slosh.toDestination();
    sloshSynthRef.current = slosh;

    // ── Sting (red star→blue absorption) ─────────────────────────────────────
    // FM synthesis: dissonant harmonic ratio gives a tense "virus-entering" sound.
    const sting = new tone.FMSynth({
      harmonicity: 1.5,
      modulationIndex: 12,
      oscillator: { type: 'sine' },
      envelope: { attack: 0.001, decay: 0.4, sustain: 0, release: 0.2 },
      modulation: { type: 'sawtooth' },
      modulationEnvelope: { attack: 0.001, decay: 0.2, sustain: 0.5, release: 0.3 },
    });
    sting.volume.value = -12;
    sting.toDestination();
    stingSynthRef.current = sting;

    // ── Flop (blue+blue merge) ────────────────────────────────────────────────
    // MembraneSynth: classic kick-drum pitch sweep — deep satisfying thud.
    const flop = new tone.MembraneSynth({
      pitchDecay: 0.15,
      octaves: 4,
      envelope: { attack: 0.001, decay: 0.4, sustain: 0, release: 0.2 },
    });
    flop.volume.value = -10;
    flop.toDestination();
    flopSynthRef.current = flop;

    // ── Kling (blue blob selected) ────────────────────────────────────────────
    // MetalSynth with inharmonic partials — bell-like ping.
    // frequency is a Signal — must be set after construction, not in options.
    const kling = new tone.MetalSynth({
      envelope: { attack: 0.001, decay: 0.3, release: 0.2, sustain: 0 },
      harmonicity: 5.1,
      modulationIndex: 32,
      resonance: 4000,
      octaves: 1.5,
    });
    kling.frequency.value = 400;
    kling.volume.value = -14;
    kling.toDestination();
    klingSynthRef.current = kling;

    // ── Shutdown (blue blob deleted) ──────────────────────────────────────────
    // Sawtooth oscillator sweeping from ~D3 (146 Hz) down to 28 Hz over 1.8 s.
    const shutdown = new tone.Synth({
      oscillator: { type: 'sawtooth' },
      envelope: { attack: 0.001, decay: 0.1, sustain: 0.8, release: 0.3 },
    });
    shutdown.volume.value = -14;
    shutdown.toDestination();
    shutdownSynthRef.current = shutdown;
  }, []);

  const toggleMute = useCallback(() => {
    // Compute next value from the ref (always in sync with state via useEffect).
    // Update the ref SYNCHRONOUSLY first — the async IIFE reads it after awaits,
    // so it must reflect the new state before any microtask checkpoint.
    const next = !isMutedRef.current;
    isMutedRef.current = next;
    setIsMuted(next);
    try {
      localStorage.setItem(STORAGE_KEY, String(next));
    } catch {
      /* ignore */
    }

    if (next) {
      // Mute: silence Tone.js destination immediately.
      // _tone may be null if user never unmuted — in that case nothing to mute.
      // Tone.getDestination().mute bypasses the reverb-tail issue —
      // ConvolverNode keeps its buffer for ~8 s if we only ramp source volume.
      if (_tone) {
        try {
          _tone.getDestination().mute = true;
        } catch {
          /* ignore */
        }
      }
    } else {
      // Unmute: dynamically load Tone (this click IS the user gesture), then init
      void (async () => {
        const tone = await loadTone();
        await tone.start();
        // Guard: user re-muted while AudioContext was resuming
        if (isMutedRef.current) return;
        try {
          tone.getDestination().mute = false;
        } catch {
          /* ignore */
        }
        await createSynths(); // no-op if already initialized
      })();
    }
  }, [createSynths]);

  // Dispose all synths on component unmount; restore destination mute state
  useEffect(() => {
    return () => {
      disposeSynths();
      if (_tone) {
        try {
          _tone.getDestination().mute = false;
        } catch {
          /* ignore */
        }
      }
    };
  }, [disposeSynths]);

  // ── Play functions ────────────────────────────────────────────────────────
  // All check isMutedRef (not isMuted state) for zero-lag guard in D3 callbacks.
  // All are try/catch-wrapped: a synth mid-release will throw on re-trigger.

  const playSlosh = useCallback(() => {
    if (isMutedRef.current || !sloshSynthRef.current) return;
    try {
      // PluckSynth: Karplus-Strong water-drop. Low note = liquid feel.
      sloshSynthRef.current.triggerAttack('A2');
    } catch {
      /* ignore */
    }
  }, []);

  const playSting = useCallback(() => {
    if (isMutedRef.current || !stingSynthRef.current) return;
    try {
      stingSynthRef.current.triggerAttackRelease('A3', '8n');
    } catch {
      /* ignore */
    }
  }, []);

  const playFlop = useCallback(() => {
    if (isMutedRef.current || !flopSynthRef.current) return;
    try {
      flopSynthRef.current.triggerAttackRelease('C2', '8n');
    } catch {
      /* ignore */
    }
  }, []);

  const playKling = useCallback(() => {
    if (isMutedRef.current || !klingSynthRef.current || !_tone) return;
    try {
      klingSynthRef.current.triggerAttackRelease('8n', _tone.now());
    } catch {
      /* ignore */
    }
  }, []);

  const playShutdown = useCallback(() => {
    if (isMutedRef.current || !shutdownSynthRef.current || !_tone) return;
    try {
      const synth = shutdownSynthRef.current;
      const now = _tone.now();
      synth.triggerAttack('D3', now);
      synth.frequency.exponentialRampToValueAtTime(28, now + 1.8);
      setTimeout(() => {
        try {
          synth.triggerRelease();
        } catch {
          /* ignore */
        }
      }, 1800);
    } catch {
      /* ignore */
    }
  }, []);

  return { playSlosh, playSting, playFlop, playKling, playShutdown, isMuted, toggleMute };
}
