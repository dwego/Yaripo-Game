package yaripo;

import javax.sound.sampled.*;
import java.util.concurrent.*;

/**
 * PCM audio engine — pure javax.sound.sampled synthesis.
 */
public class Audio {

    private static final int   SAMPLE_RATE = 44100;
    private static final float MASTER_VOL  = 0.55f;

    private final ExecutorService pool;
    private volatile boolean ambientRunning = false;
    private Thread ambientThread;

    public Audio() {
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "yaripo-audio");
            t.setDaemon(true);
            return t;
        };
        pool = Executors.newCachedThreadPool(tf);
    }

    // ─── SFX ──────────────────────────────────────────────────────────────────

    /** Deep rumble: 30, 40, 55 Hz sawtooth, ~3s. */
    public void sfxCave() {
        playTone(30, 3.0, false, 0.35f);
        scheduleTone(40, 3.0, false, 0.30f, 0.0);
        scheduleTone(55, 3.0, false, 0.25f, 0.0);
    }

    /** Pentatonic melody D5 F#5 A5 D6 (~587, 740, 880, 1175 Hz). */
    public void sfxIara() {
        double[] notes = {587.33, 739.99, 880.0, 1174.66};
        double dur = 0.4;
        for (int i = 0; i < notes.length; i++) {
            scheduleTone(notes[i], dur, true, 0.30f, i * dur);
        }
    }

    public void sfxHookShoot() { playTone(1800, 0.05, true, 0.30f); }

    public void sfxHookAttach() {
        playTone(200, 0.10, true, 0.45f);
        scheduleTone(400, 0.05, true, 0.30f, 0.02);
    }

    public void sfxRelease() {
        // 800 -> 200 Hz sweep, 0.2s
        playSweep(800, 200, 0.2, 0.35f);
    }

    public void sfxDeath() {
        playTone(400, 0.3, false, 0.45f);
        scheduleTone(200, 0.3, false, 0.40f, 0.3);
        scheduleTone(100, 0.3, false, 0.35f, 0.6);
    }

    public void sfxPickup() {
        playTone(660, 0.07, true, 0.45f);
        scheduleTone(880, 0.07, true, 0.40f, 0.08);
        scheduleTone(1100, 0.08, true, 0.35f, 0.16);
    }

    public void sfxBreak() {
        playTone(180, 0.06, false, 0.55f);
        scheduleTone(120, 0.10, false, 0.45f, 0.05);
    }

    // ─── Ambient ──────────────────────────────────────────────────────────────

    public void startAmbient() {
        if (ambientRunning) return;
        ambientRunning = true;
        ambientThread = new Thread(this::ambientLoop, "yaripo-ambient");
        ambientThread.setDaemon(true);
        ambientThread.start();
    }

    public void stopAmbient() {
        ambientRunning = false;
        if (ambientThread != null) ambientThread.interrupt();
    }

    /** 4-voice drone (40, 60, 80, 120 Hz) with slow LFO, volume 0.025. */
    private void ambientLoop() {
        double[] freqs = {40.0, 60.0, 80.0, 120.0};
        double lfoRate = 0.06;
        double durSec = 1.2;
        long frame = 0;
        while (ambientRunning && !Thread.currentThread().isInterrupted()) {
            double lfo = 0.5 + 0.5 * Math.sin(2 * Math.PI * lfoRate * frame * durSec);
            int idx = (int)(frame % freqs.length);
            double freq = freqs[idx] * (1.0 + 0.01 * lfo);
            float vol = (float)(0.025 * (0.8 + 0.4 * lfo));
            playToneBlocking((float) freq, durSec, true, vol);
            frame++;
            if (!ambientRunning) break;
            try { Thread.sleep(20); } catch (InterruptedException e) { break; }
        }
    }

    // ─── Core ─────────────────────────────────────────────────────────────────

    public void playTone(double freq, double durSec, boolean isSin, float vol) {
        pool.submit(() -> playToneBlocking((float) freq, durSec, isSin, vol * MASTER_VOL));
    }

    public void scheduleTone(double freq, double durSec, boolean isSin, float vol, double delaySec) {
        pool.submit(() -> {
            try { Thread.sleep((long)(delaySec * 1000)); } catch (InterruptedException ignored) {}
            playToneBlocking((float) freq, durSec, isSin, vol * MASTER_VOL);
        });
    }

    public void playSweep(double f0, double f1, double durSec, float vol) {
        pool.submit(() -> {
            try {
                int numSamples = (int)(SAMPLE_RATE * durSec);
                byte[] buf = new byte[numSamples * 2];
                double phase = 0;
                for (int i = 0; i < numSamples; i++) {
                    double t = (double) i / numSamples;
                    double freq = f0 + (f1 - f0) * t;
                    phase += 2 * Math.PI * freq / SAMPLE_RATE;
                    double env = (i < numSamples * 0.05) ? i / (numSamples * 0.05)
                               : (i > numSamples * 0.7) ? (1.0 - (i - numSamples * 0.7) / (numSamples * 0.3)) : 1.0;
                    short pcm = (short)(Math.sin(phase) * env * vol * MASTER_VOL * Short.MAX_VALUE);
                    buf[i * 2] = (byte)(pcm & 0xFF);
                    buf[i * 2 + 1] = (byte)((pcm >> 8) & 0xFF);
                }
                writePCM(buf);
            } catch (Exception ignored) {}
        });
    }

    private void playToneBlocking(float freq, double durSec, boolean isSin, float vol) {
        try {
            int numSamples = (int)(SAMPLE_RATE * durSec);
            byte[] buf = buildPCM(freq, numSamples, isSin, Math.min(vol, 1.0f));
            writePCM(buf);
        } catch (Exception ignored) {}
    }

    private void writePCM(byte[] buf) throws Exception {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) return;
        try (SourceDataLine audioLine = (SourceDataLine) AudioSystem.getLine(info)) {
            audioLine.open(format, Math.max(buf.length, 4096));
            audioLine.start();
            audioLine.write(buf, 0, buf.length);
            audioLine.drain();
        }
    }

    private byte[] buildPCM(float freq, int numSamples, boolean isSin, float vol) {
        byte[] buf = new byte[numSamples * 2];
        int attack  = (int)(numSamples * 0.05);
        int decay   = (int)(numSamples * 0.15);
        int release = (int)(numSamples * 0.25);
        float sustain = 0.65f;
        for (int i = 0; i < numSamples; i++) {
            double phase = 2.0 * Math.PI * freq * i / SAMPLE_RATE;
            double sample = isSin ? Math.sin(phase)
                                  : 2.0 * ((freq * i / SAMPLE_RATE) % 1.0) - 1.0;
            float env;
            if (i < attack) env = (float) i / attack;
            else if (i < attack + decay) {
                float t = (float)(i - attack) / decay;
                env = 1.0f - t * (1.0f - sustain);
            } else if (i >= numSamples - release) {
                float t = (float)(i - (numSamples - release)) / release;
                env = sustain * (1.0f - t);
            } else env = sustain;
            short pcm = (short)(sample * env * vol * Short.MAX_VALUE);
            buf[i * 2] = (byte)(pcm & 0xFF);
            buf[i * 2 + 1] = (byte)((pcm >> 8) & 0xFF);
        }
        return buf;
    }

    public void shutdown() {
        stopAmbient();
        pool.shutdownNow();
    }
}
