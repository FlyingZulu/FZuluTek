/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * See ztek_engine.h. The DSP itself is C (dsp/); this file is the C++ glue that owns one
 * instance of each block and renders a stereo block: looper clock/events, acid sequencer,
 * voice (engine 0 = the ATEK303 voice) or bass engine (1..4), FX chain, resample ring. */
#include "ztek_engine.h"

extern "C" {
#include "dsp/atek_voice.h"
#include "dsp/bass_engines.h"
#include "dsp/fx.h"
#include "dsp/acid_pattern.h"
#include "dsp/midi_looper.h"
#include "dsp/drum_engine.h"
#include "dsp/fastmath.h"
}

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <new>
#include <memory>
#include <mutex>
#include <atomic>
#include <android/log.h>
#include <oboe/Oboe.h>

#define ZTEK_MAX_BLOCK 4096

/* Mutable DSP state is shared by JNI control calls and Oboe callbacks.
 * The recursive gate also permits render-side looper callbacks to reuse
 * public setters. Realtime callbacks only try-lock; they never wait. */
static std::recursive_mutex g_ztek_state_mutex;
static std::atomic<bool> g_audio_callback_logged{false};
static std::atomic<bool> g_audio_signal_logged{false};
static std::atomic<bool> g_audio_lock_miss_logged{false};

void ztek_control_lock(void) { g_ztek_state_mutex.lock(); }
int ztek_audio_try_lock(void) { return g_ztek_state_mutex.try_lock() ? 1 : 0; }
void ztek_control_unlock(void) { g_ztek_state_mutex.unlock(); }

/* bitset des assignations : ZTEK_AWORDS x uint64 par source, ZTEK_NDST ports au total */
static inline void ztek_bit_set(uint64_t *bm, int p, int on)
{
    if (on) bm[p >> 6] |= (1ull << (p & 63));
    else bm[p >> 6] &= ~(1ull << (p & 63));
}
static inline int ztek_bit_get(const uint64_t *bm, int p)
{
    return (int)((bm[p >> 6] >> (p & 63)) & 1ull);
}

struct ztek_s : public oboe::AudioStreamDataCallback {
    float sr;
    float bpm;
    float master;
    float master_peak;              /* VU master, retenu avec decay dans ztek_render */
    int engine;                     /* 0..AT_NENGINES-1 */
    int eco;                        /* OPTIONS > ECO ON/OFF (runtime DSP éco) */
    int luxe;                       /* OPTIONS > LUXE ON/OFF (APK only) : decay 0.99, pre max, BBD stéréo, drums 8 voix */
    int root;                       /* MIDI note of the acid scale root */
    int scale;

    at_voice_t voice;
    bass_t bass;
    float param[AT_NPARAMS];        /* last AT_ values, re-applied on engine switch */
    uint8_t opt[ZTEK_NOPT];         /* page FINE : réglages fins voix (AT_OPT_* + f_model + f_morph) */

    ml_t loop;
    drum_t drum;                    /* boîte à rythmes (source DRUMS), bus propre z->drumbuf */
    int drum_on;                    /* marche/arrêt locale (PLAY page DRUMS) */
    int drum_tr;                    /* transport drums indépendant (SHIFT+PLAY page DRUMS, patch 0023) */
    float *drumbuf;                 /* ZTEK_MAX_BLOCK, heap */

    /* table de mixage principale : 4 tranches BASSE/LEAD/DRUMS/LOOP */
    struct strip_t {
        float cut;                  /* cutoff du filtre traversant 0..1 */
        int   fmode;                /* 0 LP, 1 neutre, 2 HP (pré-fader, en tête) */
        float vol;
        float pan;                  /* 0..1, 0.5 = centre (pré-fader, après filtre) */
        int   mut;
        int   solo;
        int   ldr;                  /* choke général de la tranche (voir header) */
        float aux_pow, aux_loop, aux1, aux2, aux3, aux4;   /* départs auxiliaires post-fader */
        float lp;                   /* mémoire one-pole du filtre */
    } strip[ZTEK_NSTRIP];
    float strip_mod[ZTEK_NSTRIP][ZTEK_MIX_N];  /* modulation baie (base + câble, lu dans render) */
    float master_par[ZTEK_MIXM_N];             /* GAIN = loop_gain, AUX1/AUX2 master */
    float master_mod[ZTEK_MIXM_N];
    float *mixbuf, *mixbufL, *mixbufR, *loopbuf, *sendbuf, *rsbuf, *sirenbuf;  /* ZTEK_MAX_BLOCK, heap */

    acid_pattern_t pat;
    acid_params_t ap;
    acid_grid_t grid;
    int acid_on;
    int seq_step;
    float acid_clk;                 /* 1/64-bar accumulator */
    int seq_note;                   /* the note the sequencer is holding, -1 none */

    /* arpégiateur (bouton ARP, page 13) */
    int arp_on;
    float arp_k[4];                 /* 0 MODE 1 OCT 2 DIV 3 GATE */
    int arp_held[16];               /* notes tenues, triées croissant */
    int arp_order[16];              /* notes en ordre de jeu (AS-PLAYED) */
    int arp_n, arp_nord;
    int arp_idx;
    float arp_clk;                  /* accumulateur en pas */
    int arp_note;                   /* note en cours de l'arp, -1 = aucune */
    int32_t arp_gate;               /* échantillons restants avant le note-off */
    uint32_t arp_rng;

    float *mono;                    /* scratch, ZTEK_MAX_BLOCK */
    float lbuf[ZTEK_MAX_BLOCK], rbuf[ZTEK_MAX_BLOCK];

    std::shared_ptr<oboe::AudioStream> out;

    /* SMP page: 4 s audio-in buffer + one-shot player */
    std::shared_ptr<oboe::AudioStream> in;
    float *smp;                     /* ZTEK_SMP_SECONDS * sr frames, heap (".pool" on the FM-1) */
    uint32_t smp_cap, smp_len, smp_wr;
    int smp_rec, smp_play;
    float smp_start, smp_end, smp_vol, smp_pitch;   /* 0..1, 0..1, 0..1, 0..1 (±24 st) */
    int smp_mode;                   /* 0 OFF 1 BEAT 2 CLEAN 3 OLD */
    double smp_pos;                 /* play position, in frames within [start,end) */
    int32_t smp_delay;              /* BEAT: samples until the next beat */

    /* circuit bend (post-master) */
    int bend_on, bend_mode;
    float bend_x, bend_y;
    float bend_hold;                /* sample-hold state */
    float bend_trem_ph;             /* trémolo phase */
    uint32_t bend_rng;

    /* copie des derniers fx_set (fx.c garde les siens en static) — pour SNAP/KIT */
    float fxparam[FX_NPARAMS];

    /* page XY (la modulation LFO/ENV/ASSIGN vit côté branche seq-plus, PR #3) */
    int xy_on, xy_hold;
    int xy_target[2];               /* 0..7 voix/AT_ENGINE, 8..25 FX */
    float xy_saved[26];             /* valeurs à l'entrée de la page (MOM) */

    /* sirène */
    int siren_on;
    int siren_wave;                 /* 0 CLASSIQUE 1 CHOP 2 POLICE 3 LASER */
    float siren_lvl, siren_t, siren_fb, siren_mix;   /* siren_mix : 0 pre-FX, 1 post-FX */
    float siren_ph, siren_f, siren_out, siren_sw;

    /* PoW! (dernier état, pour les snapshots) */
    int punch_on, punch_type;
    float punch_a, punch_b, punch_mix;

    /* SNAP (16) / KIT (16) / undo-redo (3 niveaux) */
    struct ztek_state_t {
        int engine;
        float param[AT_NPARAMS];
        float fx[FX_NPARAMS];
        int punch_on, punch_type;
        float pa, pb, pmix;
        int xy_on, xy_hold;
        int xy_target[2];
        float bpm;
        uint16_t slotmask;
        /* choke des séquenceurs (préservé par snapshots/kits/undo) */
        uint8_t  loop_choke[ML_NSLOTS];
        uint32_t loop_choke_order[ML_NSLOTS];
        int8_t   loop_lead[7];
        uint8_t  loop_leader;
        uint8_t  drum_choke[DR_NPADS];
        uint32_t drum_choke_order[DR_NPADS];
        int8_t   drum_lead[DR_NCHOKE];
        uint8_t  drum_leader;
        /* table de mixage 4 tranches (snapshot) */
        float strip_cut[ZTEK_NSTRIP];
        int   strip_fmode[ZTEK_NSTRIP];
        float strip_vol[ZTEK_NSTRIP];
        int   strip_mut[ZTEK_NSTRIP];
        float strip_aux[ZTEK_NSTRIP][4];   /* pow, loop, aux1, aux2 */
        float strip_par[ZTEK_NSTRIP][ZTEK_MIX_N];   /* base manuelle de chaque param */
        float master_par[ZTEK_MIXM_N];
        /* pedalboard (menu FX) */
        int   fxslot_type[ZTEK_NSLOT];
        float fxslot_p[ZTEK_NSLOT][4];
        float fxslot_mix[ZTEK_NSLOT];
        int   chain_order[ZTEK_NCHAIN];
        float chain_mix[ZTEK_NCHAIN];
        uint8_t opt[ZTEK_NOPT];            /* page FINE (préservé par snapshots/kits/undo) */
    } snap[16], kit[16], undo_st[3], redo_st[3];
    int undo_n, redo_n;

    /* ---- modulation section (pages LFO/ENV), all per 32-sample block -------------- */
    float mod_knob[ZT_NSRC][4];     /* 4 knobs per source */
    float src_val[ZT_NSRC];         /* last computed source values 0..1 */
    int   lfo_sync, lfo_trig;
    float lfo_ph[4], lfo_sh[4], lfo_drunk[4];
    int   lab_type[4];              /* 0..7: LINK MYCELIUM HUMUS NET VINTAGE CHAOS OISEAUX INSECTES MAREE */
    int   lab_src[4][2];            /* two modulation sources per labo */
    float lab_out[4];
    float lab_bird[4][2][16];       /* per-lab bird ring (OISEAUX), two axes */
    int   lab_birdpos[4][2];
    uint32_t lab_rng[4];
    int   lab_clk[4];
    float lab_phase[4];
    float env_ph[2];                /* 0..1 ADSR phase */
    float env_lvl[2];               /* current env level */
    int   env_loop[2];
    int   env_stage[2];             /* 0 A, 1 D, 2 S, 3 R */
    float ef_lvl;                   /* envelope follower state */
    float blk_peak;                 /* peak of the last synth block (EF source) */
    float tool_state[6];            /* SLEW/CMP history etc. */
    int   asg_src[ZT_NDST_ALL];     /* 255 = off; dst ZT_NDST..ZT_NDST_ALL-1 = mix des macros */
    float asg_depth[ZT_NDST_ALL];
    float dst_base[ZT_NDST_ALL];    /* base value of each destination (set by the UI setters) */
    struct { int dst, sub, used; float val; } macro[ZT_NMACRO];   /* page MACRO (16 slots assignables) */
    float fx_base[FX_NPARAMS];

    /* ---- sequencer advanced ------------------------------------------------------- */
    float seqk[5];                  /* 5 TM LOCK, 6 TM LEN, 7 MUT, 8 PLAY, 9 RAND */
    int   play_pos;                 /* position in the current play mode */
    int   play_dir;                 /* ping-pong direction */
    uint32_t seq_rng;
    int   seq_wrapped;              /* a loop wrap happened, pending TM/RAND at block edge */

    /* user presets per part (bass/lead/drums) x 16 slots (banc libre) */
    ztek_preset_t pstore[ZT_NPARTS][ZT_PSLOTS];

    /* baie de patch : patch[src] = bitset des dst (branche looper, PR #1 @ 7d87264) */
    uint32_t patch[4];
    /* baie de patch v3 : assign[src] = bitset ZTEK_AWORDS x uint64 des ZTEK_NDST ports */
    uint64_t assign[ZTEK_NSRC][ZTEK_AWORDS];
    uint64_t xy_assign[2][ZTEK_AWORDS]; /* cibles des axes X/Y (sous-ensemble des ports params) */

    /* ---- pedalboard FX (menu FX) : 4 chaînes x 3 slots ---- */
    struct fxslot_t {
        int   type;                 /* FXT_* */
        float p[4];                 /* 4 réglages fins du module */
        float mix;                  /* dry/wet du slot (K4 dans le sous-menu slot) */
        /* état DSP des modules légers (par slot) */
        float st[4];                /* ex. SVF lp/bp, env, lfo ph, delay pos... */
        float buf[64];              /* petit buffer par slot si besoin (ring mod, gate...) */
        float dbuf[2048];           /* ligne délai par slot (slap/echo/chorus) 8 Ko */
        int16_t rvb[2048];          /* mini-FDN spring/room par slot (4 Ko) */
    } fxslot[ZTEK_NSLOT];
    int   chain_order[ZTEK_NCHAIN]; /* permutation des chaînes 0..3 */
    float chain_mix[ZTEK_NCHAIN];   /* dry/wet par pédale (SEL sur la pédale active) */
    struct { int used; char name[12]; } fxblock[ZTEK_NBLOCK];
    struct {
        int   type[ZTEK_NSLOT];
        float p[ZTEK_NSLOT][4];
        float mix[ZTEK_NSLOT];
        int   order[ZTEK_NCHAIN];
        float cmix[ZTEK_NCHAIN];
    } fxblock_st[ZTEK_NBLOCK];
    float *pwL, *pwR, *pwCI, *pwCO, *pwW, *pwW2;   /* pedalboard: cur(L/R), chainIn(L/R), wet(L/R) */

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override
    {
        /* UI/JNI may update shared DSP state. Never block the realtime thread:
         * emit a silent output block or drop this input block, then resume next callback. */
        if (!ztek_audio_try_lock()) {
            if (!g_audio_lock_miss_logged.exchange(true, std::memory_order_relaxed))
                __android_log_print(ANDROID_LOG_WARN, "ZuluTekAudio",
                    "realtime state lock busy; one output block may be silenced");
            if (stream == out.get() && audioData && numFrames > 0)
                memset(audioData, 0, (size_t)numFrames * 2u * sizeof(float));
            return oboe::DataCallbackResult::Continue;
        }
        if (stream == out.get()) {
            if (!g_audio_callback_logged.exchange(true, std::memory_order_relaxed)) {
                __android_log_print(ANDROID_LOG_INFO, "ZuluTekAudio",
                    "first callback frames=%d rate=%d channels=%d format=%d device=%d burst=%d",
                    numFrames, stream->getSampleRate(), stream->getChannelCount(),
                    (int)stream->getFormat(), stream->getDeviceId(), stream->getFramesPerBurst());
            }
            float *o = static_cast<float *>(audioData);
            uint32_t done = 0;
            while (done < (uint32_t)numFrames) {
                uint32_t blk = (uint32_t)numFrames - done;
                if (blk > ZTEK_MAX_BLOCK) blk = ZTEK_MAX_BLOCK;
                memset(lbuf, 0, blk * sizeof(float));
                memset(rbuf, 0, blk * sizeof(float));
                ztek_render(this, lbuf, rbuf, blk);
                if (!g_audio_signal_logged.load(std::memory_order_relaxed)) {
                    float peak = 0.0f;
                    for (uint32_t i = 0; i < blk; i++) {
                        float al = lbuf[i] < 0.0f ? -lbuf[i] : lbuf[i];
                        float ar = rbuf[i] < 0.0f ? -rbuf[i] : rbuf[i];
                        if (al > peak) peak = al;
                        if (ar > peak) peak = ar;
                    }
                    if (peak > 0.00001f &&
                        !g_audio_signal_logged.exchange(true, std::memory_order_relaxed))
                        __android_log_print(ANDROID_LOG_INFO, "ZuluTekAudio",
                            "first non-silent render peak=%g frames=%u", peak, blk);
                }
                for (uint32_t i = 0; i < blk; i++) {
                    o[(done + i) * 2] = lbuf[i];
                    o[(done + i) * 2 + 1] = rbuf[i];
                }
                done += blk;
            }
        } else if (stream == in.get() && smp_rec) {
            /* record the audio-in into the SMP buffer */
            const float *ip = static_cast<const float *>(audioData);
            for (int32_t i = 0; i < numFrames; i++) {
                if (smp_wr < smp_cap) smp[smp_wr++] = ip[i];
            }
            if (smp_wr >= smp_cap) smp_wr = 0;   /* ring: overwrite the oldest */
        }
        ztek_control_unlock();
        return oboe::DataCallbackResult::Continue;
    }
};

int ztek_start(ztek_t *z)
{
    if (z->out) return 1;
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Output)
     ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
     ->setSharingMode(oboe::SharingMode::Exclusive)
     ->setSampleRate((int32_t)z->sr)
     ->setChannelCount(2)
     ->setFormat(oboe::AudioFormat::Float)
     ->setDataCallback(z);
    oboe::Result r = b.openStream(z->out);
    if (r != oboe::Result::OK) {
        b.setSharingMode(oboe::SharingMode::Shared);
        r = b.openStream(z->out);
    }
    if (r != oboe::Result::OK) return 0;
    return z->out->requestStart() == oboe::Result::OK ? 1 : 0;
}

void ztek_stop(ztek_t *z)
{
    if (z->out) {
        z->out->stop();
        z->out->close();
        z->out.reset();
    }
}

/* ---- looper callbacks (audio thread): loop events drive the synth -------------------- */

static void ztek_note_on_int(ztek_t *z, int note, int vel, int accent, int slide, int record);
static void ztek_arp_note_in(ztek_t *z, int note, int vel);
static void ztek_arp_note_out(ztek_t *z, int note);
static void ztek_macro_apply_int(ztek_t *z, int slot);

static void cb_note_on(void *ctx, int slot, int note, int vel)
{
    ztek_t *z = (ztek_t *)ctx;
    int n = note + z->loop.slot[slot].sp.pitch;
    if (n < 0) n = 0;
    if (n > 127) n = 127;
    ztek_note_on_int(z, n, vel, vel > 100 ? 1 : 0, 0, 0);
}

static void cb_note_off(void *ctx, int slot, int note)
{
    ztek_t *z = (ztek_t *)ctx;
    int n = note + z->loop.slot[slot].sp.pitch;
    if (n < 0) n = 0;
    if (n > 127) n = 127;
    if (z->engine == 0) at_gate(&z->voice, 0, n, 0, 0);
    else bass_gate(&z->bass, 0, n, 0, 0);
}

static void cb_param(void *ctx, int slot, int key, float v)
{
    ztek_t *z = (ztek_t *)ctx;
    (void)slot;
    if (key >= 0 && key < AT_NPARAMS) ztek_set_param(z, key, v);
}

/* ---- engine ------------------------------------------------------------------------ */

ztek_t *ztek_create(float sr)
{
    ztek_t *z = new (std::nothrow) ztek_t();   /* value-init: ztek_s has a shared_ptr member */
    if (!z) return NULL;
    z->sr = sr;
    z->bpm = 120.0f;
    z->master = 0.8f;
    z->master_peak = 0.0f;
    z->engine = 0;
    z->root = 45;                   /* A2 */
    z->scale = 0;

    at_init(&z->voice, sr);
    for (int i = 0; i < AT_NOPTS; i++) {         /* page FINE : defaults AT_OPT_DEFAULT (DRIFT subtle inclus) */
        z->opt[i] = AT_OPT_DEFAULT[i];
        at_option(&z->voice, i, z->opt[i]);
    }
    z->opt[AT_NOPTS] = 0;                         /* filtre ORG */
    z->opt[AT_NOPTS + 1] = 0;                     /* morph LP */
    z->voice.f_model = 0;
    z->voice.f_morph = 0.0f;
    bass_init(&z->bass, sr);
    for (int p = 0; p < AT_NPARAMS; p++) {
        z->param[p] = 0.5f;
        at_set(&z->voice, p, z->param[p]);
        bass_set(&z->bass, p, z->param[p]);
    }
    z->param[AT_CUTOFF] = 0.7f;
    z->param[AT_DECAY] = 0.55f;
    at_set(&z->voice, AT_CUTOFF, 0.7f);
    at_set(&z->voice, AT_DECAY, 0.55f);
    bass_set(&z->bass, AT_CUTOFF, 0.7f);
    bass_set(&z->bass, AT_DECAY, 0.55f);

    fx_init(sr);
    fx_set(FX_LEVEL, 0.9f);

    ml_init(&z->loop, sr);
    ml_set_cb(&z->loop, z, cb_note_on, cb_note_off, cb_param);

    acid_params_default(&z->ap);
    z->ap.scale = 0;
    acid_clear(&z->pat);
    acid_generate(&z->pat, &z->ap, 0x20261119u);
    acid_render(&z->pat, &z->grid);
    z->acid_on = 0;
    z->seq_step = 0;
    z->acid_clk = 0.0f;
    z->seq_note = -1;

    /* arp defaults : UP, 2 octaves, 1/16, gate 50% */
    z->arp_on = 0;
    z->arp_k[0] = 0.0f; z->arp_k[1] = 0.34f; z->arp_k[2] = 0.4f; z->arp_k[3] = 0.5f;
    z->arp_n = 0; z->arp_nord = 0;
    z->arp_idx = 0;
    z->arp_clk = 0.0f;
    z->arp_note = -1;
    z->arp_gate = 0;
    z->arp_rng = 0x2545F491u;

    /* modulation defaults */
    for (int s = 0; s < ZT_NSRC; s++) {
        z->src_val[s] = 0.0f;
        for (int k = 0; k < 4; k++) z->mod_knob[s][k] = 0.5f;
    }
    z->mod_knob[0][0] = 0.35f;      /* LFO1 rate */
    z->lfo_sync = 0;
    z->lfo_trig = 0;
    for (int i = 0; i < 4; i++) {
        z->lfo_ph[i] = 0.0f;
        z->lfo_sh[i] = 0.0f;
        z->lfo_drunk[i] = 0.5f;
        z->lab_type[i] = (i == 0) ? 4 : 0;      /* LABO1 defaults to CHAOS */
        z->lab_src[i][0] = 0;
        z->lab_src[i][1] = 1;
        z->lab_out[i] = 0.0f;
        for (int a = 0; a < 2; a++) {
            z->lab_birdpos[i][a] = 0;
            for (int b = 0; b < 16; b++) z->lab_bird[i][a][b] = 0.5f;
        }
        z->lab_rng[i] = 0x1234567u + (uint32_t)i * 2654435761u;
        z->lab_clk[i] = 0;
        z->lab_phase[i] = 0.0f;
    }
    for (int e = 0; e < 2; e++) {
        z->env_ph[e] = 0.0f;
        z->env_lvl[e] = 0.0f;
        z->env_loop[e] = 1;         /* the two ENVs are loopable by default */
        z->env_stage[e] = 0;
    }
    z->ef_lvl = 0.0f;
    z->blk_peak = 0.0f;
    for (int t = 0; t < 6; t++) z->tool_state[t] = 0.0f;
    for (int d = 0; d < ZT_NDST_ALL; d++) {
        z->asg_src[d] = 255;
        z->asg_depth[d] = 0.0f;
        z->dst_base[d] = 0.5f;
    }
    for (int m = 0; m < ZT_NMACRO; m++) { z->macro[m].dst = 0; z->macro[m].sub = 0; z->macro[m].used = 0; z->macro[m].val = 0.0f; }
    for (int p = 0; p < FX_NPARAMS; p++) z->fx_base[p] = 0.5f;
    /* voice destinations mirror z->param (set above) */
    for (int p = 0; p < AT_NPARAMS; p++) z->dst_base[p] = z->param[p];

    /* sequencer advanced defaults */
    for (int k = 0; k < 5; k++) z->seqk[k] = 0.0f;
    z->play_pos = 0;
    z->play_dir = 1;
    z->seq_rng = 0x9e3779b9u;
    z->seq_wrapped = 0;
    memset(z->pstore, 0, sizeof(z->pstore));

    z->mono = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->drumbuf = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->mixbuf = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->mixbufL = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->mixbufR = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->loopbuf = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->sendbuf = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->rsbuf = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->sirenbuf = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->pwL = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->pwR = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->pwCI = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->pwCO = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->pwW = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->pwW2 = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->smp_cap = (uint32_t)(ZTEK_SMP_SECONDS * sr);
    z->smp = (float *)malloc(z->smp_cap * sizeof(float));
    if (!z->mono || !z->smp || !z->drumbuf || !z->mixbuf || !z->mixbufL || !z->mixbufR || !z->loopbuf || !z->sendbuf || !z->rsbuf || !z->sirenbuf || !z->pwL || !z->pwR || !z->pwCI || !z->pwCO || !z->pwW || !z->pwW2) {
        free(z->mono); free(z->smp); free(z->drumbuf);
        free(z->mixbuf); free(z->mixbufL); free(z->mixbufR); free(z->loopbuf); free(z->sendbuf); free(z->rsbuf); free(z->sirenbuf);
        free(z->pwL); free(z->pwR); free(z->pwCI); free(z->pwCO); free(z->pwW); free(z->pwW2);
        delete z; return NULL;
    }
    drum_init(&z->drum, sr);
    z->drum_on = 1;                 /* le drum est armé d'emblee (kick 4OTF) ; transport indépendant : SHIFT+PLAY page DRUMS (patch APK 0023) */
    z->drum_tr = 1;
    z->smp_len = 0; z->smp_wr = 0;
    z->smp_rec = 0; z->smp_play = 0;
    z->smp_start = 0.0f; z->smp_end = 1.0f; z->smp_vol = 0.8f; z->smp_pitch = 0.5f;
    z->smp_mode = 2;                 /* CLEAN par défaut */
    z->smp_pos = 0.0; z->smp_delay = 0;
    z->bend_on = 0; z->bend_mode = 0; z->bend_x = 0.0f; z->bend_y = 0.0f;
    z->bend_hold = 0.0f; z->bend_trem_ph = 0.0f; z->bend_rng = 0xBEAD01u & 0x7fffffffu;
    z->xy_on = 0; z->xy_hold = 0; z->xy_target[0] = 1; z->xy_target[1] = 2;   /* X->CUTOFF Y->RESO */
    z->siren_on = 0; z->siren_lvl = 0.3f; z->siren_t = 0.5f; z->siren_fb = 0.0f;
    z->siren_wave = 0; z->siren_mix = 0.0f;
    z->siren_ph = 0.0f; z->siren_f = 600.0f; z->siren_out = 0.0f; z->siren_sw = 0.0f;
    z->punch_on = 0; z->punch_type = 8; z->punch_a = z->punch_b = z->punch_mix = 0.0f;   /* POW! défaut = ECHO */
    z->punch_on = 0; z->punch_type = 0; z->punch_a = z->punch_b = z->punch_mix = 0.0f;
    for (int s = 0; s < ZTEK_NSTRIP; s++) {          /* tranches par défaut : vol 0.8, filtre neutre, pan centre */
        z->strip[s].cut = 0.5f; z->strip[s].fmode = 1; z->strip[s].vol = 0.8f; z->strip[s].pan = 0.5f;
        z->strip[s].mut = 0; z->strip[s].solo = 0;
        z->strip[s].ldr = (s == ZTEK_STRIP_DRUMS || s == ZTEK_STRIP_LOOP) ? 1 : 0;  /* leader/hybride par défaut */
        z->strip[s].aux_pow = 0.0f; z->strip[s].aux1 = 0.0f; z->strip[s].aux2 = 0.0f;
        z->strip[s].aux3 = 0.0f; z->strip[s].aux4 = 0.0f;
        z->strip[s].aux_loop = (s == ZTEK_STRIP_LOOP) ? 0.0f : 1.0f;   /* pas de départ Loop sur la tranche LOOP */
        z->strip[s].lp = 0.0f;
        for (int p = 0; p < ZTEK_MIX_N; p++) z->strip_mod[s][p] = 0.0f;
    }
    /* tranche SIREN : vol/mute sont des miroirs de siren_lvl / siren_on (décision mixeur 4 tranches) */
    z->strip[ZTEK_STRIP_SIREN].vol = z->siren_lvl;
    z->strip[ZTEK_STRIP_SIREN].mut = z->siren_on ? 0 : 1;
    z->master_par[ZTEK_MIXM_GAIN] = 1.0f; z->master_par[ZTEK_MIXM_AUX1] = 0.0f; z->master_par[ZTEK_MIXM_AUX2] = 0.0f;
    for (int p = 0; p < ZTEK_MIXM_N; p++) z->master_mod[p] = 0.0f;
    z->undo_n = 0; z->redo_n = 0;
    memset(z->assign, 0, sizeof z->assign);
    memset(z->xy_assign, 0, sizeof z->xy_assign);
    /* pedalboard par défaut : tout OFF, ordre identité, dry/wet pédales à 1 (full wet) */
    memset(z->fxslot, 0, sizeof z->fxslot);
    for (int s = 0; s < ZTEK_NSLOT; s++) z->fxslot[s].type = FXT_OFF;
    for (int c = 0; c < ZTEK_NCHAIN; c++) { z->chain_order[c] = c; z->chain_mix[c] = 1.0f; }
    memset(z->fxblock, 0, sizeof z->fxblock);
    memset(z->fxblock_st, 0, sizeof z->fxblock_st);
    ztek_bit_set(z->assign[ZTEK_PSRC_VOIX], ZTEK_MOD_OFF[ZTEK_MOD_OUT] + 0, 1);   /* VOIX -> FX */
    ztek_bit_set(z->assign[ZTEK_PSRC_SIREN], ZTEK_MOD_OFF[ZTEK_MOD_OUT] + 0, 1);  /* SIREN -> FX */
    ztek_bit_set(z->assign[ZTEK_PSRC_SMP], ZTEK_MOD_OFF[ZTEK_MOD_OUT] + 1, 1);    /* SMP/AIN -> MASTER */
    ztek_bit_set(z->assign[ZTEK_PSRC_LOOP], ZTEK_MOD_OFF[ZTEK_MOD_OUT] + 1, 1);   /* LOOP -> MASTER */
    ztek_bit_set(z->assign[ZTEK_PSRC_DRUMS], ZTEK_MOD_OFF[ZTEK_MOD_OUT] + 1, 1); /* DRUMS -> MASTER */
    ztek_assign_set(z, 4, 1, 1);                /* X -> AT_CUTOFF (mirroré dans xy_assign) */
    ztek_assign_set(z, 5, 2, 1);                /* Y -> AT_RESONANCE */
    memset(z->fxparam, 0, sizeof z->fxparam);
    memset(z->snap, 0, sizeof z->snap);
    memset(z->kit, 0, sizeof z->kit);
    return z;
}

void ztek_destroy(ztek_t *z)
{
    if (!z) return;
    ztek_stop(z);
    if (z->in) { z->in->stop(); z->in->close(); z->in.reset(); }
    free(z->mono);
    free(z->smp);
    free(z->drumbuf);
    free(z->mixbuf);
    free(z->mixbufL);
    free(z->mixbufR);
    free(z->loopbuf);
    free(z->sendbuf);
    free(z->rsbuf);
    free(z->sirenbuf);
    free(z->pwL); free(z->pwR); free(z->pwCI); free(z->pwCO); free(z->pwW); free(z->pwW2);
    delete z;
}

static void ztek_note_on_int(ztek_t *z, int note, int vel, int accent, int slide, int record)
{
    int cls = z->engine == 0 ? 0 : 1;           /* 0 bass, 1 synth (midi_looper.h) */
    if (record) ml_note_in(&z->loop, note, vel, 4, cls);
    if (z->lfo_trig) {                          /* LFO TRIG: retrigger the 4 LFOs + 2 ENVs */
        for (int i = 0; i < 4; i++) { z->lfo_ph[i] = 0.0f; z->lfo_sh[i] = 0.0f; }
        for (int e = 0; e < 2; e++) { z->env_ph[e] = 0.0f; z->env_stage[e] = 0; }
    }
    if (z->engine == 0) {
        at_gate(&z->voice, 1, note, accent, slide);
        if (z->strip[ZTEK_STRIP_VOICE].ldr) bass_panic(&z->bass);   /* LDR VOICE : la lead coupe la basse */
    } else {
        bass_gate(&z->bass, 1, note, accent, slide);
        if (z->strip[ZTEK_STRIP_VOICE].ldr) at_panic(&z->voice);     /* LDR VOICE : la basse coupe la lead */
    }
}

void ztek_note_on(ztek_t *z, int note, int vel, int accent, int slide)
{
    if (z->arp_on) { ztek_arp_note_in(z, note, vel); return; }   /* l'arp capture les notes tenues */
    ztek_note_on_int(z, note, vel, accent, slide, 1);
}

void ztek_note_off(ztek_t *z, int note)
{
    ml_note_off_in(&z->loop, note);
    if (z->arp_on) { ztek_arp_note_out(z, note); return; }
    if (z->engine == 0) at_gate(&z->voice, 0, note, 0, 0);
    else bass_gate(&z->bass, 0, note, 0, 0);
}

void ztek_all_notes_off(ztek_t *z)
{
    if (z->engine == 0) at_panic(&z->voice);
    else bass_panic(&z->bass);
    z->seq_note = -1;
    z->arp_n = 0; z->arp_nord = 0;
    z->arp_note = -1;
    z->arp_gate = 0;
}

void ztek_set_param(ztek_t *z, int param, float v)
{
    if (param < 0 || param >= AT_NPARAMS) return;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    z->param[param] = v;
    z->dst_base[param] = v;
    at_set(&z->voice, param, v);
    bass_set(&z->bass, param, v);
}

void ztek_set_option(ztek_t *z, int opt, int v)   /* page FINE (APK 18 / firmware 0012) */
{
    if (opt < 0 || opt >= ZTEK_NOPT) return;
    if (opt == AT_NOPTS + 1) {                     /* morph LP->HP continu 0..255 */
        if (v < 0) v = 0;
        if (v > 255) v = 255;
        z->opt[opt] = (uint8_t)v;
        z->voice.f_morph = (float)v / 255.0f;
    } else {
        int n = opt == AT_NOPTS ? 2 : AT_OPT_N[opt];
        if (v < 0) v = 0;
        if (v >= n) v = n - 1;
        z->opt[opt] = (uint8_t)v;
        if (opt == AT_NOPTS) z->voice.f_model = v;
        else at_option(&z->voice, opt, v);
    }
}

int ztek_get_option(const ztek_t *z, int opt)
{
    if (opt < 0 || opt >= ZTEK_NOPT) return 0;
    return z->opt[opt];
}

void ztek_set_engine(ztek_t *z, int engine)
{
    if (engine < 0) engine = 0;
    if (engine >= AT_NENGINES) engine = AT_NENGINES - 1;
    z->engine = engine;
    z->param[AT_ENGINE] = (float)engine / (float)(AT_NENGINES - 1);
    /* re-apply the shared knobs (incl. AT_ENGINE) so the new engine starts from the same page */
    for (int p = 0; p < AT_NPARAMS; p++) {
        at_set(&z->voice, p, z->param[p]);
        bass_set(&z->bass, p, z->param[p]);
    }
}

int ztek_get_engine(const ztek_t *z) { return z->engine; }

void ztek_set_bpm(ztek_t *z, float bpm)
{
    z->bpm = bpm;
    z->dst_base[ZT_SEQ_DST0 + 2] = bpm / 300.0f;
    ml_set_bpm(&z->loop, bpm);
    drum_set_bpm(&z->drum, bpm);
}

void ztek_set_root(ztek_t *z, int root)
{
    z->root = root;
    z->dst_base[ZT_SEQ_DST0 + 3] = (float)(root - 24) / 48.0f;
}
void ztek_set_scale(ztek_t *z, int scale)
{
    z->scale = scale;
    z->dst_base[ZT_SEQ_DST0 + 1] = (float)scale / 7.0f;
    z->ap.scale = scale;
}

void ztek_fx_set(ztek_t *z, int param, float v)
{
    if (param < 0 || param >= FX_NPARAMS) return;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    float cap = z->luxe ? 0.99f : (z->eco ? 0.70f : 0.89f);   /* luxe (APK only) relève le decay à 0.99 */
    if (param == FX_REV_DECAY && v > cap) v = cap;
    if (param == FX_REV_PRE && z->eco && !z->luxe && v > 0.5f) v = 0.5f;   /* eco: pre-delay max réduit (luxe: pre max) */
    z->fx_base[param] = v;
    z->fxparam[param] = v;
    if (param < ZT_NDST - 8) z->dst_base[8 + param] = v;
    fx_set(param, v);
}

void ztek_set_eco(ztek_t *z, int on)
{
    z->eco = on ? 1 : 0;
    int eff = z->eco && !z->luxe;           /* luxe (APK only) gagne sur eco si les deux sont ON */
    fx_set_eco(eff);                        /* crush 96+tanh, BBD mono, swirl off, pre/2 */
    drum_set_eco(&z->drum, eff);            /* polyphonie drums 8 -> 5 */
    ml_set_eco(&z->loop, eff);              /* resample loop: µ-law+lo-fi, ring 32 kHz, capa /2 */
    ztek_fx_set(z, FX_REV_DECAY, z->fxparam[FX_REV_DECAY]);   /* re-clamp selon eco/luxe */
    ztek_fx_set(z, FX_REV_PRE, z->fxparam[FX_REV_PRE]);
}

int ztek_get_eco(const ztek_t *z) { return z->eco; }

void ztek_set_luxe(ztek_t *z, int on)
{
    z->luxe = on ? 1 : 0;
    int eff = z->eco && !z->luxe;           /* luxe ON neutralise eco (BBD stéréo, swirl, pre max, drums 8 voix) */
    fx_set_eco(eff);
    drum_set_eco(&z->drum, eff);
    ml_set_eco(&z->loop, eff);
    ztek_fx_set(z, FX_REV_DECAY, z->fxparam[FX_REV_DECAY]);   /* re-clamp: decay 0.99 en luxe */
    ztek_fx_set(z, FX_REV_PRE, z->fxparam[FX_REV_PRE]);
}

int ztek_get_luxe(const ztek_t *z) { return z->luxe; }

float ztek_fx_get(const ztek_t *z, int param)
{
    (void)z;
    return fx_get(param);      /* fx_set a déjà reçu la valeur clampée (decay Black Hole) */
}

void ztek_punch(ztek_t *z, int on, int type, float a, float b, float mix)
{
    z->punch_on = on; z->punch_type = type;
    z->punch_a = a; z->punch_b = b; z->punch_mix = mix;
    fx_punch(on, type, a, b, mix);
}

void ztek_punch_tail(ztek_t *z, float t) { (void)z; fx_punch_off_tail(t); }

void ztek_loop_play(ztek_t *z, int on) { ml_play(&z->loop, on); }   /* transport du looper ; drums et acid ont le leur (patch 0023) */
void ztek_loop_toggle(ztek_t *z, int slot) { ml_toggle(&z->loop, slot); }
void ztek_loop_rec_start(ztek_t *z, int slot, int od) { ml_rec_start(&z->loop, slot, od); }
void ztek_loop_rec_stop(ztek_t *z, int slot) { ml_rec_stop(&z->loop, slot); }
void ztek_loop_erase(ztek_t *z, int slot) { ml_erase(&z->loop, slot); }
void ztek_loop_set_len(ztek_t *z, int slot, int len64) { ml_set_len(&z->loop, slot, len64); }
void ztek_loop_set_sp(ztek_t *z, int slot, int idx, float v) { ml_set_sp(&z->loop, slot, idx, v); }
int ztek_loop_slot_on(const ztek_t *z, int slot)
{
    return slot >= 0 && slot < ML_NSLOTS ? z->loop.slot[slot].on : 0;
}
int ztek_loop_slot_rec(const ztek_t *z, int slot)
{
    return slot >= 0 && slot < ML_NSLOTS ? z->loop.slot[slot].rec : 0;
}

float ztek_loop_get_sp(const ztek_t *z, int slot, int idx)
{
    return ml_get_sp((ml_t *)&z->loop, slot, idx);
}

/* one-shot looper (liaison APK/FM-1, patch 0023) : un slot qui wrap s'arrête (notes relevées) */
void ztek_loop_set_oneshot(ztek_t *z, int on) { ml_set_oneshot(&z->loop, on); }
int  ztek_loop_get_oneshot(const ztek_t *z)   { return ml_get_oneshot((ml_t *)&z->loop); }

void ztek_loop_rs_start(ztek_t *z, int dst, uint16_t monmask, int fmt, int khz)
{
    /* cap auto : µ-law = 2x ML_RS_MAX octets ; on prend le max, le ring s'arrête à rs_len */
    ml_rs_start(&z->loop, dst, monmask, ML_RS_MAX, fmt, khz);
}

void ztek_loop_rs_stop(ztek_t *z) { ml_rs_stop(&z->loop); }

int ztek_loop_rs_rec(const ztek_t *z) { return z->loop.rs_rec; }

/* ---- page MIX + baie de patch ------------------------------------------------------- */

void ztek_mix_set_vol(ztek_t *z, int slot, float v) { ml_set_sp(&z->loop, slot, ML_SP_VOL, v); }
void ztek_mix_set_aux(ztek_t *z, int slot, int on) { ml_set_sp(&z->loop, slot, ML_SP_FX, on ? 1.0f : 0.0f); }
void ztek_mix_set_gain(ztek_t *z, float g)
{
    g = g < 0.0f ? 0.0f : g > 2.0f ? 2.0f : g;
    z->loop.loop_gain = g;
    z->master_par[ZTEK_MIXM_GAIN] = g;
    z->master_mod[ZTEK_MIXM_GAIN] = 0.0f;
}

void ztek_mix_set_strip(ztek_t *z, int strip, int param, float v)
{
    ztek_s::strip_t *st;
    if (strip < 0 || strip >= ZTEK_NSTRIP || param < 0 || param >= ZTEK_MIX_N) return;
    st = &z->strip[strip];
    z->strip_mod[strip][param] = 0.0f;          /* réglage manuel = base, la modulation s'ajoute après */
    switch (param) {
    case ZTEK_MIX_CUT:      st->cut = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v; break;
    case ZTEK_MIX_FMODE:    st->fmode = (int)v; if (st->fmode < 0) st->fmode = 0; if (st->fmode > 2) st->fmode = 2; break;
    case ZTEK_MIX_VOL:      st->vol = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v;
        if (strip == ZTEK_STRIP_SIREN) z->siren_lvl = st->vol;   /* tranche SIREN : vol = siren_lvl */
        break;
    case ZTEK_MIX_PAN:      st->pan = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v; break;
    case ZTEK_MIX_MUT:      st->mut = v > 0.5f ? 1 : 0;
        if (strip == ZTEK_STRIP_SIREN) z->siren_on = st->mut ? 0 : 1;  /* tranche SIREN : mute = siren off */
        break;
    case ZTEK_MIX_SOLO:     st->solo = v > 0.5f ? 1 : 0; break;
    case ZTEK_MIX_LDR:
        st->ldr = v > 0.5f ? 1 : 0;
        if (strip == ZTEK_STRIP_DRUMS) drum_set_leader(&z->drum, st->ldr);   /* ON = leader/hybride, OFF = Koala */
        if (strip == ZTEK_STRIP_LOOP) ml_set_leader(&z->loop, st->ldr);
        break;
    case ZTEK_MIX_AUX_POW:  st->aux_pow = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v; break;
    case ZTEK_MIX_AUX_LOOP: st->aux_loop = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v; break;
    case ZTEK_MIX_AUX1:     st->aux1 = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v; break;
    case ZTEK_MIX_AUX2:     st->aux2 = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v; break;
    case ZTEK_MIX_AUX3:     st->aux3 = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v; break;
    case ZTEK_MIX_AUX4:     st->aux4 = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v; break;
    }
}

float ztek_mix_get_strip(const ztek_t *z, int strip, int param)
{
    const ztek_s::strip_t *st;
    if (strip < 0 || strip >= ZTEK_NSTRIP || param < 0 || param >= ZTEK_MIX_N) return 0.0f;
    st = &z->strip[strip];
    switch (param) {
    case ZTEK_MIX_CUT:      return st->cut;
    case ZTEK_MIX_FMODE:    return (float)st->fmode;
    case ZTEK_MIX_VOL:      return (strip == ZTEK_STRIP_SIREN) ? z->siren_lvl : st->vol;
    case ZTEK_MIX_PAN:      return st->pan;
    case ZTEK_MIX_MUT:      return (strip == ZTEK_STRIP_SIREN) ? (z->siren_on ? 0.0f : 1.0f) : (float)st->mut;
    case ZTEK_MIX_SOLO:     return (float)st->solo;
    case ZTEK_MIX_LDR:      return (float)st->ldr;
    case ZTEK_MIX_AUX_POW:  return st->aux_pow;
    case ZTEK_MIX_AUX_LOOP: return st->aux_loop;
    case ZTEK_MIX_AUX1:     return st->aux1;
    case ZTEK_MIX_AUX2:     return st->aux2;
    case ZTEK_MIX_AUX3:     return st->aux3;
    case ZTEK_MIX_AUX4:     return st->aux4;
    }
    return 0.0f;
}

void ztek_mix_set_master(ztek_t *z, int param, float v)
{
    if (param < 0 || param >= ZTEK_MIXM_N) return;
    z->master_mod[param] = 0.0f;
    if (param == ZTEK_MIXM_GAIN) { ztek_mix_set_gain(z, v); return; }
    z->master_par[param] = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v;
}

float ztek_mix_get_master(const ztek_t *z, int param)
{
    if (param < 0 || param >= ZTEK_MIXM_N) return 0.0f;
    if (param == ZTEK_MIXM_GAIN) return z->loop.loop_gain;
    return z->master_par[param];
}
float ztek_mix_get_gain(const ztek_t *z) { return z->loop.loop_gain; }
float ztek_master_peak(const ztek_t *z) { return z->master_peak; }
int ztek_loop_slot_act(const ztek_t *z, int slot)
{
    if (slot < 0 || slot >= ML_NSLOTS) return 0;
    return z->loop.slot[slot].n_act;
}

/* ---- baie de patch v3 : modules groupés, compat façon VCV Rack/miRack -----------------
 * Ports 0..34 identiques à la matrice ASSIGN de la branche seq-plus (ZT_NDST=35) :
 * 0..7 VOIX (AT_), 8..25 FX (FX_), 26..34 SEQ. Puis SIREN, SMP, LOOP (16 slots x 7), OUT. */

const int ZTEK_MOD_OFF[ZTEK_NMOD + 1] = { 0, 8, 26, 35, 38, 43, 155, 160, 168, 223 };
const char *const ZTEK_MOD_NAME[ZTEK_NMOD] = { "VOIX", "FX", "SEQ", "SIREN", "SMP", "LOOP", "OUT", "AUX", "MIX" };
const int ZTEK_MOD_NPORT[ZTEK_NMOD] = { 8, 18, 9, 3, 5, 112, 5, 8, ZTEK_MIX_NPORT };
const char *const ZTEK_SRC_NAME[ZTEK_NSRC] = {
    "VOIX", "SIREN", "SMP", "LOOP", "X", "Y",
    "LFO1", "LFO2", "LFO3", "LFO4", "LAB1", "LAB2", "LAB3", "LAB4",
    "ENV1", "ENV2", "EF", "LOG", "SLEW", "CMP", "QUANT", "ATT",
    "DRUMS"
};

/* ports audio du module OUT */
#define ZTEK_PORT_FXIN   (ZTEK_MOD_OFF[ZTEK_MOD_OUT] + 0)
#define ZTEK_PORT_MASTER (ZTEK_MOD_OFF[ZTEK_MOD_OUT] + 1)
#define ZTEK_PORT_POW    (ZTEK_MOD_OFF[ZTEK_MOD_OUT] + 2)
#define ZTEK_PORT_AUX1   (ZTEK_MOD_OFF[ZTEK_MOD_OUT] + 3)   /* départ aux libre master n°1 */
#define ZTEK_PORT_AUX2   (ZTEK_MOD_OFF[ZTEK_MOD_OUT] + 4)   /* départ aux libre master n°2 */

int ztek_mod_of(int dst)
{
    int m;
    if (dst < 0 || dst >= ZTEK_NDST) return -1;
    for (m = ZTEK_NMOD - 1; m >= 0; m--) if (dst >= ZTEK_MOD_OFF[m]) return m;
    return 0;
}

/* sources audio (0..3) -> OUT seulement ; sources CV (X/Y 4,5 + modulation 6..21) -> modules
 * paramètres 0..5 seulement. Le reste est estompé/refusé (protection façon VCV Rack/miRack). */
int ztek_patch_compat(int src, int dst)
{
    int mod;
    if (src < 0 || src >= ZTEK_NSRC || dst < 0 || dst >= ZTEK_NDST) return 0;
    mod = ztek_mod_of(dst);
    if (src < 4 || src == ZTEK_PSRC_DRUMS)
        return mod == ZTEK_MOD_OUT || mod == ZTEK_MOD_AUX;   /* audio -> OUT/AUX (départs libres) */
    return mod < ZTEK_MOD_OUT || mod == ZTEK_MOD_MIX;        /* CV -> params (modules 0..5 + MIX) */
}

void ztek_assign_set(ztek_t *z, int src, int dst, int on)
{
    if (!ztek_patch_compat(src, dst)) return;      /* protégé : refusé, comme VCV Rack */
    if (src < 0 || src >= ZTEK_NSRC || dst < 0 || dst >= ZTEK_NDST) return;
    ztek_bit_set(z->assign[src], dst, on);
    if (src == 4) ztek_bit_set(z->xy_assign[0], dst, on);
    if (src == 5) ztek_bit_set(z->xy_assign[1], dst, on);
}

int ztek_assign_get(const ztek_t *z, int src, int dst)
{
    if (src < 0 || src >= ZTEK_NSRC || dst < 0 || dst >= ZTEK_NDST) return 0;
    return ztek_bit_get(z->assign[src], dst);
}

void ztek_assign_words(const ztek_t *z, int src, uint64_t out[ZTEK_AWORDS])
{
    int w;
    for (w = 0; w < ZTEK_AWORDS; w++)
        out[w] = (src >= 0 && src < ZTEK_NSRC) ? z->assign[src][w] : 0;
}

/* API v1 (routage audio) : dst 0=FX 1=MASTER 2=POW! -> ports OUT 0..2 */
void ztek_patch_set(ztek_t *z, int src, int dst, int on)
{
    if (dst < 0 || dst > 2) return;
    ztek_assign_set(z, src, ZTEK_MOD_OFF[ZTEK_MOD_OUT] + dst, on);
}

int ztek_patch_get(const ztek_t *z, int src, int dst)
{
    if (dst < 0 || dst > 2) return 0;
    return ztek_assign_get(z, src, ZTEK_MOD_OFF[ZTEK_MOD_OUT] + dst);
}

void ztek_acid_generate(ztek_t *z, uint32_t seed)
{
    z->ap.scale = z->scale;
    acid_generate(&z->pat, &z->ap, seed);
    acid_render(&z->pat, &z->grid);
    z->seq_step = 0;
    z->play_pos = 0;
    z->play_dir = 1;
    z->dst_base[ZT_SEQ_DST0 + 0] = (float)z->pat.time_length / 64.0f;
}

void ztek_acid_play(ztek_t *z, int on) { z->acid_on = on ? 1 : 0; }

void ztek_acid_knob(ztek_t *z, int which, float v)
{
    switch (which) {
    case 0: z->ap.density = v; break;
    case 1: z->ap.accent = v; break;
    case 2: z->ap.slide = v; break;
    case 3: z->ap.range = v; break;
    case 4: z->ap.tie = v; break;
    }
}

/* ---- SMP page (audio-in sampler) ------------------------------------------------ */

void ztek_smp_rec(ztek_t *z, int on)
{
    if (on && !z->in) {
        oboe::AudioStreamBuilder b;
        b.setDirection(oboe::Direction::Input)
         ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
         ->setSharingMode(oboe::SharingMode::Shared)
         ->setSampleRate((int32_t)z->sr)
         ->setChannelCount(1)
         ->setFormat(oboe::AudioFormat::Float)
         ->setDataCallback(z);
        if (b.openStream(z->in) != oboe::Result::OK) return;
        if (z->in->requestStart() != oboe::Result::OK) { z->in->close(); z->in.reset(); return; }
    }
    if (on) {
        z->smp_wr = 0;
        z->smp_len = 0;
        z->smp_rec = 1;
    } else {
        z->smp_rec = 0;
        z->smp_len = z->smp_wr;
        if (z->in) { z->in->stop(); z->in->close(); z->in.reset(); }
    }
}

void ztek_smp_play(ztek_t *z, int on)
{
    if (on && z->smp_len > 0) {
        z->smp_pos = 0.0;
        z->smp_delay = 0;
        if (z->smp_mode == 1 && z->loop.playing) {
            /* BEAT : démarre sur le prochain temps (1/16) de l'horloge du looper */
            float beat64 = 16.0f * 60.0f / z->bpm;            /* 1/64 bar en samples */
            float next = (fm_floorf(z->loop.clk / 16.0f) + 1.0f) * 16.0f;   /* next beat, in 1/64 */
            float d = (next - z->loop.clk) * beat64 / 16.0f;
            z->smp_delay = (int32_t)(d > 0.0f ? d : beat64);
        }
        z->smp_play = 1;
    } else {
        z->smp_play = 0;
    }
}

void ztek_smp_set(ztek_t *z, int which, float v)
{
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    switch (which) {
    case 0: z->smp_start = v; break;
    case 1: z->smp_end = v; if (z->smp_end <= z->smp_start) z->smp_end = z->smp_start + 0.01f; break;
    case 2: z->smp_vol = v; break;
    case 3: z->smp_pitch = v; break;
    case 4: z->smp_mode = (int)(v * 3.999f); break;
    }
}

int ztek_smp_len(const ztek_t *z) { return (int)z->smp_len; }

void ztek_bend(ztek_t *z, int on, float x, float y, int mode)
{
    z->bend_on = on ? 1 : 0;
    z->bend_x = x < 0.0f ? 0.0f : x > 1.0f ? 1.0f : x;
    z->bend_y = y < 0.0f ? 0.0f : y > 1.0f ? 1.0f : y;
    z->bend_mode = mode;
}

/* ---- SNAP / KIT / undo-redo -------------------------------------------------------- */

static void ztek_capture(ztek_t *z, ztek_t::ztek_state_t *s)
{
    int i;
    s->engine = z->engine;
    memcpy(s->param, z->param, sizeof s->param);
    memcpy(s->fx, z->fxparam, sizeof s->fx);
    memcpy(s->opt, z->opt, sizeof s->opt);
    s->punch_on = z->punch_on; s->punch_type = z->punch_type;
    s->pa = z->punch_a; s->pb = z->punch_b; s->pmix = z->punch_mix;
    s->xy_on = z->xy_on; s->xy_hold = z->xy_hold;
    s->xy_target[0] = z->xy_target[0]; s->xy_target[1] = z->xy_target[1];
    s->bpm = z->bpm;
    s->slotmask = 0;
    for (i = 0; i < ML_NSLOTS; i++) if (z->loop.slot[i].on) s->slotmask |= (uint16_t)(1u << i);
    memcpy(s->loop_choke, z->loop.choke, sizeof s->loop_choke);
    memcpy(s->loop_choke_order, z->loop.choke_order, sizeof s->loop_choke_order);
    memcpy(s->loop_lead, z->loop.lead, sizeof s->loop_lead);
    s->loop_leader = z->loop.leader;
    memcpy(s->drum_choke, z->drum.choke, sizeof s->drum_choke);
    memcpy(s->drum_choke_order, z->drum.choke_order, sizeof s->drum_choke_order);
    memcpy(s->drum_lead, z->drum.lead, sizeof s->drum_lead);
    s->drum_leader = z->drum.leader;
    for (i = 0; i < ZTEK_NSTRIP; i++) {
        int p;
        for (p = 0; p < ZTEK_MIX_N; p++) s->strip_par[i][p] = ztek_mix_get_strip(z, i, p);
    }
    for (i = 0; i < ZTEK_MIXM_N; i++) s->master_par[i] = ztek_mix_get_master(z, i);
    /* pedalboard */
    for (i = 0; i < ZTEK_NSLOT; i++) {
        s->fxslot_type[i] = z->fxslot[i].type;
        for (int p = 0; p < 4; p++) s->fxslot_p[i][p] = z->fxslot[i].p[p];
        s->fxslot_mix[i] = z->fxslot[i].mix;
    }
    memcpy(s->chain_order, z->chain_order, sizeof s->chain_order);
    memcpy(s->chain_mix, z->chain_mix, sizeof s->chain_mix);
}

static void ztek_apply(ztek_t *z, const ztek_t::ztek_state_t *s)
{
    int i;
    ztek_set_engine(z, s->engine);
    for (i = 0; i < AT_NPARAMS; i++) ztek_set_param(z, i, s->param[i]);
    for (i = 0; i < FX_NPARAMS; i++) ztek_fx_set(z, i, s->fx[i]);
    for (i = 0; i < ZTEK_NOPT; i++) ztek_set_option(z, i, s->opt[i]);
    ztek_punch(z, s->punch_on, s->punch_type, s->pa, s->pb, s->pmix);
    z->xy_hold = s->xy_hold;
    z->xy_target[0] = s->xy_target[0]; z->xy_target[1] = s->xy_target[1];
    ztek_set_bpm(z, s->bpm);
    /* choke d'abord : les toggles de slots ci-dessous déclenchent les coupures avec les
     * groupes du snapshot */
    memcpy(z->loop.choke, s->loop_choke, sizeof z->loop.choke);
    memcpy(z->loop.choke_order, s->loop_choke_order, sizeof z->loop.choke_order);
    memcpy(z->loop.lead, s->loop_lead, sizeof z->loop.lead);
    z->loop.choke_tick = 1;
    for (i = 0; i < ML_NSLOTS; i++)
        if (z->loop.choke_order[i] > z->loop.choke_tick) z->loop.choke_tick = z->loop.choke_order[i];
    z->loop.leader = s->loop_leader;
    memcpy(z->drum.choke, s->drum_choke, sizeof z->drum.choke);
    memcpy(z->drum.choke_order, s->drum_choke_order, sizeof z->drum.choke_order);
    memcpy(z->drum.lead, s->drum_lead, sizeof z->drum.lead);
    z->drum.choke_tick = 1;
    for (i = 0; i < DR_NPADS; i++)
        if (z->drum.choke_order[i] > z->drum.choke_tick) z->drum.choke_tick = z->drum.choke_order[i];
    z->drum.leader = s->drum_leader;
    for (i = 0; i < ZTEK_NSTRIP; i++) {
        int p;
        for (p = 0; p < ZTEK_MIX_N; p++) ztek_mix_set_strip(z, i, p, s->strip_par[i][p]);
    }
    for (i = 0; i < ZTEK_MIXM_N; i++) ztek_mix_set_master(z, i, s->master_par[i]);
    for (i = 0; i < ZTEK_NSLOT; i++)
        ztek_fxslot_set(z, i, s->fxslot_type[i], s->fxslot_p[i][0], s->fxslot_p[i][1],
                       s->fxslot_p[i][2], s->fxslot_p[i][3], s->fxslot_mix[i]);
    ztek_fxchain_order(z, s->chain_order);
    for (i = 0; i < ZTEK_NCHAIN; i++) ztek_fxchain_mix(z, i, s->chain_mix[i]);
    for (i = 0; i < ML_NSLOTS; i++) {
        int want = (s->slotmask >> i) & 1u;
        if (want != z->loop.slot[i].on) ml_toggle(&z->loop, i);
    }
}

static void ztek_push_undo(ztek_t *z)
{
    if (z->undo_n >= 3) {
        memmove(z->undo_st, z->undo_st + 1, 2 * sizeof(ztek_t::ztek_state_t));
        z->undo_n = 2;
    }
    ztek_capture(z, &z->undo_st[z->undo_n++]);
    z->redo_n = 0;
}

void ztek_snap_store(ztek_t *z, int slot)
{
    if (slot < 0 || slot >= 16) return;
    ztek_push_undo(z);
    ztek_capture(z, &z->snap[slot]);
}

void ztek_snap_load(ztek_t *z, int slot)
{
    if (slot < 0 || slot >= 16) return;
    if (z->snap[slot].bpm <= 0.0f) return;      /* slot vide */
    ztek_push_undo(z);
    ztek_apply(z, &z->snap[slot]);
}

void ztek_undo(ztek_t *z)
{
    if (z->undo_n <= 0) return;
    if (z->redo_n < 3) ztek_capture(z, &z->redo_st[z->redo_n++]);
    ztek_apply(z, &z->undo_st[--z->undo_n]);
}

void ztek_redo(ztek_t *z)
{
    int rn;
    if (z->redo_n <= 0) return;
    rn = z->redo_n - 1;                          /* dépiler AVANT push_undo : celui-ci remet redo_n à 0 (sinon redo_st[-1], lecture hors limites) */
    ztek_push_undo(z);
    z->undo_n--;                                 /* push_undo a remis l'état courant : on l'échange */
    ztek_apply(z, &z->redo_st[rn]);
}

void ztek_kit_store(ztek_t *z, int slot)
{
    if (slot < 0 || slot >= 16) return;
    ztek_capture(z, &z->kit[slot]);
}

void ztek_kit_load(ztek_t *z, int slot)
{
    if (slot < 0 || slot >= 16) return;
    if (z->kit[slot].bpm <= 0.0f) return;
    ztek_push_undo(z);
    ztek_apply(z, &z->kit[slot]);
}

/* ---- page XY ------------------------------------------------------------------------ */

static float ztek_target_value(ztek_t *z, int t)
{
    if (t <= 6) return z->param[t];
    if (t == 7) return (float)z->engine / (float)(AT_NENGINES - 1);
    if (t < 26) return z->fxparam[t - 8];
    return 0.0f;
}

static void ztek_apply_target(ztek_t *z, int t, float v)
{
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    if (t <= 6) ztek_set_param(z, t, v);
    else if (t == 7) ztek_set_engine(z, at_engine_index(v));
    else if (t < 26) ztek_fx_set(z, t - 8, v);
}

void ztek_xy_target(ztek_t *z, int axis, int target)
{
    if (axis < 0 || axis > 1) return;
    if (target < 0) target = 0;
    if (target >= ZTEK_NDST) target = ZTEK_NDST - 1;
    z->xy_target[axis] = target;
    /* la cible simple devient le bit correspondant (les axes pilotent tous les ports branchés) */
    if (ztek_patch_compat(4 + axis, target)) {
        int w;
        for (w = 0; w < ZTEK_AWORDS; w++) z->xy_assign[axis][w] = 0;
        ztek_bit_set(z->xy_assign[axis], target, 1);
    }
}

void ztek_xy_hold(ztek_t *z, int holdMode) { z->xy_hold = holdMode ? 1 : 0; }

void ztek_xy_set(ztek_t *z, int on, float x, float y)
{
    if (on && !z->xy_on) {
        int t;
        for (t = 0; t < 26; t++) z->xy_saved[t] = ztek_target_value(z, t);
    }
    if (!on && z->xy_on && !z->xy_hold) {
        int t;
        for (t = 0; t < 26; t++) ztek_apply_target(z, t, z->xy_saved[t]);
    }
    z->xy_on = on ? 1 : 0;
    if (z->xy_on) {
        int d;
        for (d = 0; d < ZTEK_NDST; d++) {
            if (ztek_bit_get(z->xy_assign[0], d)) ztek_apply_target(z, d, x);
            if (ztek_bit_get(z->xy_assign[1], d)) ztek_apply_target(z, d, y);
        }
    }
}

/* ---- sirène ------------------------------------------------------------------------- */

void ztek_siren(ztek_t *z, int on, float lvl, float t, float fb)
{
    z->siren_on = on ? 1 : 0;
    z->siren_lvl = lvl < 0.0f ? 0.0f : lvl > 1.0f ? 1.0f : lvl;
    z->siren_t = t < 0.0f ? 0.0f : t > 1.0f ? 1.0f : t;
    z->siren_fb = fb;
}

void ztek_siren_wave(ztek_t *z, int w)
{
    if (w < 0) w = 0;
    if (w > 3) w = 3;
    z->siren_wave = w;
}

int ztek_siren_wave_get(const ztek_t *z) { return z->siren_wave; }

void ztek_siren_mix(ztek_t *z, float m)
{
    z->siren_mix = m < 0.0f ? 0.0f : m > 1.0f ? 1.0f : m;
}

float ztek_siren_mix_get(const ztek_t *z) { return z->siren_mix; }

/* un échantillon de sirène : LFO siren_sw (0..1) balaie la fréquence selon le type (wave) */
static float ztek_siren_sample(ztek_t *z, float fsr)
{
    float f = 300.0f + 1100.0f * (0.5f + 0.5f * fm_sin_turns(z->siren_sw));
    float amp = 1.0f;
    switch (z->siren_wave) {
    case 1:   /* CHOP : haché par le LFO */
        { float q = z->siren_sw * 4.0f; if (q - fm_floorf(q) >= 0.55f) return 0.0f; }
        break;
    case 2:   /* POLICE : deux tons, alternance 1x / 1.5x à chaque demi-cycle */
        if (z->siren_sw >= 0.5f) f *= 1.5f;
        break;
    case 3:   /* LASER : balayage linéaire montant + montée d'amplitude */
        f = 300.0f + 1100.0f * z->siren_sw;
        amp = 0.35f + 0.65f * z->siren_sw;
        break;
    default:  /* CLASSIQUE : balayage triangle doux */
        break;
    }
    z->siren_f = f;
    z->siren_ph += f * fsr;
    if (z->siren_ph >= 1.0f) z->siren_ph -= 1.0f;
    float sv = fm_sin_turns(z->siren_ph) + 0.5f * fm_sin_turns(z->siren_ph * 2.003f + z->siren_fb);
    return sv * amp;
}

/* ---- boîte à rythmes (source DRUMS = 22, dsp/drum_engine.c vendored de la PR #5) --------- */

void ztek_drum_play(ztek_t *z, int on) { z->drum_on = on ? 1 : 0; if (on) drum_reset(&z->drum); }
void ztek_drum_hit(ztek_t *z, int pad) { drum_hit(&z->drum, pad); }
void ztek_drum_pat(ztek_t *z, int pad, int step, int on) { drum_set_pat(&z->drum, pad, step, on); }
int  ztek_drum_get_pat(const ztek_t *z, int pad, int step) { return drum_get_pat(&z->drum, pad, step); }
void ztek_drum_clear(ztek_t *z, int pad) { drum_clear_pat(&z->drum, pad); }
void ztek_drum_rand(ztek_t *z, int pad, float density, uint32_t seed) { drum_rand_pat(&z->drum, pad, density, seed); }
void ztek_drum_swing(ztek_t *z, float s) { drum_set_swing(&z->drum, s); }
void ztek_drum_level(ztek_t *z, int pad, float lvl) { drum_set_level(&z->drum, pad, lvl); }
void ztek_drum_master(ztek_t *z, float m) { drum_set_master(&z->drum, m); }
int  ztek_drum_step(const ztek_t *z) { return drum_get_step(&z->drum); }
void ztek_drum_set_oneshot(ztek_t *z, int on) { drum_set_oneshot(&z->drum, on); }   /* one-shot (liaison APK/FM-1, patch 0023) */
int  ztek_drum_get_oneshot(const ztek_t *z)   { return drum_get_oneshot(&z->drum); }
void ztek_drum_tr_set(ztek_t *z, int on) { z->drum_tr = on ? 1 : 0; if (on) drum_reset(&z->drum); }   /* transport drums indépendant (SHIFT+PLAY page DRUMS) */
int  ztek_drum_tr_get(const ztek_t *z)   { return z->drum_tr; }
void ztek_drum_set_choke(ztek_t *z, int pad, int grp) { drum_set_choke(&z->drum, pad, grp); }
int  ztek_drum_get_choke(const ztek_t *z, int pad) { return drum_get_choke(&z->drum, pad); }
int  ztek_drum_choke_master(const ztek_t *z, int grp) { return drum_choke_master(&z->drum, grp); }
void ztek_drum_set_leader(ztek_t *z, int on) { drum_set_leader(&z->drum, on); }
int  ztek_drum_get_leader(const ztek_t *z) { return drum_get_leader(&z->drum); }
void ztek_drum_set_leader_pad(ztek_t *z, int pad, int on) { drum_set_leader_pad(&z->drum, pad, on); }
void ztek_drum_clear_leader(ztek_t *z, int grp) { drum_clear_leader(&z->drum, grp); }
void ztek_loop_set_choke(ztek_t *z, int slot, int grp) { ml_set_choke(&z->loop, slot, grp); }
int  ztek_loop_get_choke(const ztek_t *z, int slot) { return ml_get_choke(&z->loop, slot); }
int  ztek_loop_choke_master(const ztek_t *z, int grp) { return ml_choke_master(&z->loop, grp); }
void ztek_loop_set_leader(ztek_t *z, int on) { ml_set_leader(&z->loop, on); }
int  ztek_loop_get_leader(const ztek_t *z) { return ml_get_leader(&z->loop); }
void ztek_loop_set_leader_slot(ztek_t *z, int slot, int on) { ml_set_leader_slot(&z->loop, slot, on); }
void ztek_loop_clear_leader(ztek_t *z, int grp) { ml_clear_leader(&z->loop, grp); }

void ztek_smp_peaks(const ztek_t *z, float *out, int nbuckets)
{
    /* min/max par bucket pour la waveform du LCD */
    int i;
    uint32_t len = z->smp_len;
    for (i = 0; i < nbuckets; i++) { out[i * 2] = 0.0f; out[i * 2 + 1] = 0.0f; }
    if (len == 0) return;
    for (i = 0; i < nbuckets; i++) {
        uint32_t b0 = (uint32_t)((uint64_t)i * len / nbuckets);
        uint32_t b1 = (uint32_t)((uint64_t)(i + 1) * len / nbuckets);
        float mn = 0.0f, mx = 0.0f;
        uint32_t k;
        if (b1 <= b0) b1 = b0 + 1;
        for (k = b0; k < b1 && k < len; k++) {
            float x = z->smp[k];
            if (x < mn) mn = x;
            if (x > mx) mx = x;
        }
        out[i * 2] = mn;
        out[i * 2 + 1] = mx;
    }
}

static uint32_t ztek_lcg(ztek_t *z)
{
    z->seq_rng = z->seq_rng * 1103515245u + 12345u;
    return z->seq_rng;
}

static float ztek_rnd01(ztek_t *z) { return (float)(ztek_lcg(z) >> 8) / 16777216.0f; }

/* advance the play position according to the PLAY mode (seqk[3]); returns 1 on a loop wrap */
static int ztek_seq_advance(ztek_t *z, int steps)
{
    int mode = z->seqk[3] < 0.34f ? 0 : (z->seqk[3] < 0.67f ? 1 : 2);  /* NORM REV PING-PONG */
    int wrapped = 0;
    if (mode == 0) {
        z->play_pos++;
        if (z->play_pos >= steps) { z->play_pos = 0; wrapped = 1; }
    } else if (mode == 1) {
        z->play_pos--;
        if (z->play_pos < 0) { z->play_pos = steps - 1; wrapped = 1; }
    } else {
        z->play_pos += z->play_dir;
        if (z->play_pos >= steps) {
            z->play_pos = steps - 2; if (z->play_pos < 0) z->play_pos = 0;
            z->play_dir = -1; wrapped = 1;
        } else if (z->play_pos < 0) {
            z->play_pos = 1; if (z->play_pos >= steps) z->play_pos = steps - 1;
            z->play_dir = 1; wrapped = 1;
        }
    }
    return wrapped;
}

/* at each loop wrap: RAND (re-generate) and the Turing Machine (rotate the window) */
static void ztek_seq_wrap(ztek_t *z)
{
    int steps = z->pat.time_length;
    if (steps < 1) steps = 1;
    if (steps > ACID_MAX_STEPS) steps = ACID_MAX_STEPS;

    /* RAND: probability of a full re-generation at each loop wrap */
    if (z->seqk[4] > 0.0001f && ztek_rnd01(z) < z->seqk[4]) {
        z->ap.scale = z->scale;
        acid_generate(&z->pat, &z->ap, ztek_lcg(z));
        acid_render(&z->pat, &z->grid);
        z->play_pos = 0;
        z->play_dir = 1;
        return;
    }

    /* Turing Machine: rotate the window [0, wl) one step to the right. The new head
     * (index 0, the shifted-out tail is not re-injected as-is) is: kept when r < lock,
     * mutated when lock <= r < lock+mut, re-rolled otherwise. lock = 0 -> TM off. */
    float lock = z->seqk[0];
    if (lock > 0.0001f) {
        int wl = (int)(z->seqk[1] * 60.0f + 4.0f);      /* TM LEN 4..64 */
        if (wl < 4) wl = 4;
        if (wl > steps) wl = steps;
        acid_grid_t g = z->grid;
        int8_t tdeg = g.deg[wl-1]; int8_t toct = g.oct[wl-1];
        uint8_t tg = g.gate[wl-1], ta = g.accent[wl-1], ts = g.slide[wl-1], tt = g.tie[wl-1];
        for (int i = wl - 1; i > 0; i--) {
            g.deg[i] = g.deg[i-1]; g.oct[i] = g.oct[i-1];
            g.gate[i] = g.gate[i-1]; g.accent[i] = g.accent[i-1];
            g.slide[i] = g.slide[i-1]; g.tie[i] = g.tie[i-1];
        }
        float r = ztek_rnd01(z);
        g.deg[0] = tdeg; g.oct[0] = toct; g.gate[0] = tg;
        g.accent[0] = ta; g.slide[0] = ts; g.tie[0] = tt;
        if (r >= lock) {
            if (r < lock + z->seqk[2]) {
                uint32_t m = ztek_lcg(z) & 3u;          /* mutate one field of the head */
                if (m == 0) g.deg[0] += (ztek_lcg(z) & 1u) ? 1 : -1;
                else if (m == 1) g.oct[0] += (ztek_lcg(z) & 1u) ? 1 : -1;
                else if (m == 2) g.gate[0] = g.gate[0] ? 0 : 1;
                else g.accent[0] = g.accent[0] ? 0 : 1;
            } else {                                    /* re-rolled head */
                g.gate[0] = (ztek_lcg(z) % 100u) < 70u ? 1 : 0;
                g.deg[0] = (int8_t)((int)(ztek_lcg(z) % 12u) - 6);
                g.oct[0] = (int8_t)((int)(ztek_lcg(z) % 3u) - 1);
                g.accent[0] = (ztek_lcg(z) % 100u) < 30u ? 1 : 0;
                g.slide[0] = 0; g.tie[0] = 0;
            }
        }
        z->grid = g;
        acid_import(&z->pat, &z->grid, steps, z->pat.seed, z->pat.algorithm_version);
        acid_render(&z->pat, &z->grid);
    }
}

/* one 1/64-bar of the acid sequencer: gate the grid's step */
static void ztek_acid_step(ztek_t *z)
{
    int steps = z->pat.time_length;
    int s;
    if (steps < 1) steps = 1;
    if (steps > ACID_MAX_STEPS) steps = ACID_MAX_STEPS;
    if (z->play_pos >= steps) z->play_pos = 0;
    s = z->play_pos;
    if (ztek_seq_advance(z, steps)) z->seq_wrapped = 1;
    z->seq_step = z->play_pos;
    if (z->grid.gate[s]) {
        int semi = acid_semi_of(z->grid.deg[s], z->grid.oct[s], z->scale);
        int note = z->root + semi;
        if (note < 0) note = 0;
        if (note > 127) note = 127;
        if (z->seq_note >= 0 && z->seq_note != note) ztek_note_off(z, z->seq_note);
        ztek_note_on_int(z, note, z->grid.accent[s] ? 110 : 90,
                         z->grid.accent[s] ? 1 : 0, z->grid.slide[s] ? 1 : 0, 0);   /* généré par le seq acid : pas enregistré dans le looper */
        z->seq_note = note;
    } else if (z->seq_note >= 0 && !z->grid.tie[s]) {
        ztek_note_off(z, z->seq_note);
        z->seq_note = -1;
    }
}


/* ---- modulation section: everything below runs per 32-sample block --------------- */

static float ztek_wave(int w, float ph)
{
    float p = ph - floorf(ph);
    switch (w) {
    case 1:  return p < 0.5f ? p * 2.0f : 2.0f - p * 2.0f;   /* TRI 0..1..0 */
    case 2:  return p < 0.5f ? 1.0f : 0.0f;                   /* SQ */
    case 3:  return p;                                        /* SAW */
    default: return 0.5f + 0.5f * sinf(p * 6.2831853f);       /* SIN */
    }
}

static void ztek_lfo_block(ztek_t *z, float dt)
{
    static const float DIVS[6] = { 16.f, 8.f, 4.f, 2.f, 1.f, 0.5f };  /* in beats */
    for (int i = 0; i < 4; i++) {
        float rate = z->mod_knob[i][0];
        float hz;
        if (z->lfo_sync) {                            /* LFO SYNC: snap to clock divisions */
            int idx = (int)(rate * 5.99f); if (idx > 5) idx = 5;
            hz = (z->bpm / 60.0f) / DIVS[idx];
        } else {
            hz = 0.02f + rate * rate * 40.0f;         /* 0.02..40 Hz, musical taper */
        }
        float ph = z->lfo_ph[i] + hz * dt;
        int w = (int)(z->mod_knob[i][1] * 5.99f); if (w > 5) w = 5;   /* SIN TRI SQ SAW S&H DRUNK */
        if (ph >= 1.0f) {
            ph -= floorf(ph);
            if (w == 4) z->lfo_sh[i] = ztek_rnd01(z);                 /* S&H: re-sample on wrap */
        }
        z->lfo_ph[i] = ph;
        float v;
        if (w == 4) v = z->lfo_sh[i];
        else if (w == 5) {                                            /* DRUNK: random walk */
            z->lfo_drunk[i] += (ztek_rnd01(z) - 0.5f) * 0.25f;
            if (z->lfo_drunk[i] < 0.f) z->lfo_drunk[i] = 0.f;
            if (z->lfo_drunk[i] > 1.f) z->lfo_drunk[i] = 1.f;
            v = z->lfo_drunk[i];
        } else {
            v = ztek_wave(w, ph);
            if (w == 2) {                                             /* SQ duty */
                float p = ph - floorf(ph);
                v = p < (0.2f + z->mod_knob[i][3] * 0.6f) ? 1.0f : 0.0f;
            }
        }
        float amp = z->mod_knob[i][2] * 2.0f;                         /* DEPTH around 0.5 */
        if (amp > 2.0f) amp = 2.0f;
        v = 0.5f + (v - 0.5f) * amp;
        if (v < 0.f) v = 0.f;
        if (v > 1.f) v = 1.f;
        z->src_val[i] = v;
    }
}

/* loopable ADSR for ENV1/ENV2 (sources 8..9): A -> D -> S(hold) -> R -> loop */
static void ztek_env_block(ztek_t *z, float dt)
{
    for (int e = 0; e < 2; e++) {
        float A = 0.005f + z->mod_knob[8 + e][0] * 0.5f;
        float D = 0.005f + z->mod_knob[8 + e][1] * 0.5f;
        float S = z->mod_knob[8 + e][2];
        float R = 0.005f + z->mod_knob[8 + e][3] * 1.0f;
        if (A < 1e-4f) A = 1e-4f;
        if (D < 1e-4f) D = 1e-4f;
        if (R < 1e-4f) R = 1e-4f;
        int st = z->env_stage[e];
        float lvl = z->env_lvl[e];
        float ph = z->env_ph[e];
        switch (st) {
        case 0: lvl += dt / A; if (lvl >= 1.f) { lvl = 1.f; st = 1; } break;
        case 1: lvl -= dt / D * (1.f - S); if (lvl <= S) { lvl = S; st = 2; } break;
        case 2: ph += dt / R; if (ph >= 1.f) { ph = 0.f; st = 3; } break;   /* sustain hold */
        default: lvl -= dt / R; if (lvl <= 0.f) { lvl = 0.f; if (z->env_loop[e]) st = 0; } break;
        }
        z->env_stage[e] = st;
        z->env_lvl[e] = lvl;
        z->env_ph[e] = ph;
        z->src_val[8 + e] = lvl;
    }
}

/* the 4 labos (sources 4..7): the ZuluFx network profiles, computed per block.
 * knobs: TYPE, SRC1, SRC2, DEPTH. Sources must be lower ids (no cycles). */
static void ztek_lab_block(ztek_t *z, float dt)
{
    (void)dt;
    for (int i = 0; i < 4; i++) {
        int type = z->lab_type[i];
        if (type < 0) type = 0;
        if (type > 7) type = 7;
        int s1 = z->lab_src[i][0], s2 = z->lab_src[i][1];
        float a = (s1 >= 0 && s1 < 4 + i) ? z->src_val[s1] : 0.5f;
        float b = (s2 >= 0 && s2 < 4 + i) ? z->src_val[s2] : 0.5f;
        float depth = z->mod_knob[4 + i][3];
        uint32_t *rng = &z->lab_rng[i];
        *rng = *rng * 1103515245u + 12345u;
        float jit = ((float)(*rng >> 8) / 16777216.0f - 0.5f) * 2.0f;
        int clk = z->lab_clk[i]++;
        float out;
        switch (type) {
        case 1:  out = a + (b - 0.5f) * 0.5f + jit * 0.12f; break;              /* MYCELIUM */
        case 2:  out = a + (b - 0.5f) * 0.3f + jit * 0.04f; break;              /* HUMUS */
        case 3: {                                                              /* NET VINTAGE */
            out = a + jit * 0.45f + (b - 0.5f) * 0.2f;
            float st = 0.5f - 0.5f * 0.42f;                                    /* aliasing quantize */
            out = floorf(out / st + 0.5f) * st;
            break;
        }
        case 4:  out = a + jit * 0.85f + (b - 0.5f) * 1.6f; break;             /* CHAOS */
        case 5: {                                                              /* OISEAUX */
            if ((clk & 7) == 0) {
                z->lab_bird[i][0][z->lab_birdpos[i][0]] = a;
                z->lab_birdpos[i][0] = (z->lab_birdpos[i][0] + 1) & 15;
                z->lab_bird[i][1][z->lab_birdpos[i][1]] = b;
                z->lab_birdpos[i][1] = (z->lab_birdpos[i][1] + 1) & 15;
            }
            float bird = z->lab_bird[i][1][(z->lab_birdpos[i][1] - 8 + 16) & 15];
            out = a + (bird - 0.5f) * 0.9f + jit * 0.1f;
            break;
        }
        case 6: {                                                              /* INSECTES */
            out = a;
            if ((clk % 8) == 0) out += jit * 0.5f;
            if ((clk / 3) & 1) out += (b - 0.5f) * 0.3f;
            break;
        }
        case 7: {                                                              /* MAREE */
            z->lab_phase[i] += 32.0f / (z->sr * 9.0f);
            if (z->lab_phase[i] >= 1.0f) z->lab_phase[i] -= 1.0f;
            out = a + (sinf(z->lab_phase[i] * 6.2831853f) * 0.5f + 0.5f) * 0.4f + (b - 0.5f) * 0.2f;
            break;
        }
        default: out = a + (b - 0.5f); break;                                  /* LINK */
        }
        if (out < 0.f) out = 0.f;
        if (out > 1.f) out = 1.f;
        float amp = 0.3f + depth * 1.7f;
        out = 0.5f + (out - 0.5f) * amp;
        if (out < 0.f) out = 0.f;
        if (out > 1.f) out = 1.f;
        z->src_val[4 + i] = out;
    }
}

/* the 6 tools (sources 10..15): EF LOG SLEW CMP QUANT ATT. knobs: SRC, P1, P2, DEPTH */
static float ztek_tool_src(ztek_t *z, int tool)
{
    int s = (int)(z->mod_knob[tool][0] * 16.999f);      /* 0..15 source, 16 = OFF */
    if (s >= ZT_NSRC) return 0.0f;
    if (s >= tool) s = 0;                                /* no self/forward cycles */
    return z->src_val[s];
}

static void ztek_tool_block(ztek_t *z, float dt)
{
    for (int t = 0; t < 6; t++) {
        int src = 10 + t;
        float in = ztek_tool_src(z, src);
        float p1 = z->mod_knob[src][1], p2 = z->mod_knob[src][2], depth = z->mod_knob[src][3];
        float out = in;
        switch (t) {
        case 0: {   /* EF: envelope follower of the synth bus (blk_peak) */
            float peak = z->blk_peak;
            float k = peak > z->ef_lvl ? (0.01f + p1 * 2.0f) : (0.001f + p2 * 0.5f);
            z->ef_lvl += (peak - z->ef_lvl) * k;
            out = z->ef_lvl * (0.2f + p1 * 4.0f);
            break;
        }
        case 1:     /* LOG: log curve */
            out = logf(1.0f + in * 9.0f) / logf(10.0f);
            out = out * (0.2f + p1 * 2.0f) + p2;
            break;
        case 2: {   /* SLEW: lag, separate up/down rates (per second) */
            float st = z->tool_state[t];
            float rate = (in > st ? p1 : p2);
            float step = (0.0005f + rate * rate * 4.0f) * dt * 60.0f;
            if (in > st) { st += step; if (st > in) st = in; }
            else { st -= step; if (st < in) st = in; }
            z->tool_state[t] = st;
            out = st;
            break;
        }
        case 3: {   /* CMP: comparator with hysteresis */
            float th = p1, hy = 0.02f + p2 * 0.2f;
            float st = z->tool_state[t];
            if (in > th + hy) st = 1.0f;
            else if (in < th - hy) st = 0.0f;
            z->tool_state[t] = st;
            out = st;
            break;
        }
        case 4: {   /* QUANT: snap to a scale of steps */
            int steps = 2 + (int)(p1 * 30.0f);
            out = floorf(in * steps + 0.5f) / steps + p2 * 0.1f;
            break;
        }
        default:    /* ATT: gain + offset */
            out = in * p1 + p2 * 0.5f;
            break;
        }
        out = out * depth + in * (1.0f - depth);           /* DEPTH = wet */
        if (out < 0.f) out = 0.f;
        if (out > 1.f) out = 1.f;
        z->src_val[src] = out;
    }
}

/* applique une valeur 0..1 à la destination d (voix / FX / seq / mix de macro) */
static void ztek_dst_set(ztek_t *z, int d, float v)
{
    if (d >= ZT_NDST) {                        /* destination = mix d'un macro : on le met à jour et on l'applique */
        int m = d - ZT_NDST;
        if (m < 0 || m >= ZT_NMACRO) return;
        z->macro[m].val = v;
        ztek_macro_apply_int(z, m);
        return;
    }
    if (d < AT_NPARAMS) {
        at_set(&z->voice, d, v);
        bass_set(&z->bass, d, v);
    } else if (d < ZT_SEQ_DST0) {
        fx_set(d - AT_NPARAMS, v);
    } else {
        int k = d - ZT_SEQ_DST0;    /* 0 steps, 1 scale, 2 tempo, 3 root, 4..8 seqk[0..4] */
        if (k == 0) {
            int st = 1 + (int)(v * 63.0f);
            if (st != z->ap.steps) {
                z->ap.steps = st;
                acid_sanitize(&z->pat, z->scale);
                acid_render(&z->pat, &z->grid);
            }
        } else if (k == 1) {
            int sc = (int)(v * 7.99f);
            if (sc != z->scale) ztek_set_scale(z, sc);
        } else if (k == 2) {
            ztek_set_bpm(z, v * 300.0f);
        } else if (k == 3) {
            ztek_set_root(z, 24 + (int)(v * 48.99f));
        } else {
            z->seqk[k - 4] = v;
        }
    }
}

/* the ASSIGN matrix: v = base + (src - 0.5) * 2 * depth, applied to voice/FX/seq/macro-mix */
static void ztek_matrix_block(ztek_t *z)
{
    for (int d = 0; d < ZT_NDST_ALL; d++) {
        int s = z->asg_src[d];
        if (s < 0 || s >= ZT_NSRC) continue;
        float depth = z->asg_depth[d];
        if (depth <= 0.0001f) continue;
        float v = z->dst_base[d] + (z->src_val[s] - 0.5f) * 2.0f * depth;
        if (v < 0.f) v = 0.f;
        if (v > 1.f) v = 1.f;
        ztek_dst_set(z, d, v);
    }
    /* module MIX de la baie (ports ZTEK_MOD_OFF[ZTEK_MOD_MIX]..+ZTEK_MIX_NPORT) : les câbles CV
     * (sources 4..21) s'ajoutent à la valeur manuelle (base) ; flags au seuil > 0.5 à l'usage */
    {
        int base = ZTEK_MOD_OFF[ZTEK_MOD_MIX];
        int s, w, b;
        for (s = 0; s < ZTEK_NSTRIP; s++)
            for (int p = 0; p < ZTEK_MIX_N; p++) z->strip_mod[s][p] = 0.0f;
        for (int p = 0; p < ZTEK_MIXM_N; p++) z->master_mod[p] = 0.0f;
        for (s = 4; s < ZTEK_NSRC && s < ZT_NSRC; s++) {   /* src_val indexé 0..ZT_NSRC-1 : borne ZT_NSRC comme la boucle asg (évite lecture hors limites, UBSan) */
            float sv = (z->src_val[s] - 0.5f) * 2.0f;
            const uint64_t *bm = z->assign[s];
            for (w = base / 64; w < ZTEK_AWORDS; w++) {
                uint64_t bits = bm[w];
                if (!bits) continue;
                int p0 = w * 64;
                if (p0 + 63 < base) continue;
                for (b = 0; b < 64; b++) {
                    int p = p0 + b;
                    if (p < base || p >= base + ZTEK_MIX_NPORT) continue;
                    if (!((bits >> b) & 1u)) continue;
                    int idx = p - base;
                    if (idx < ZTEK_MIX_NSTRIP_PORTS)
                        z->strip_mod[idx / ZTEK_MIX_N][idx % ZTEK_MIX_N] += sv;
                    else
                        z->master_mod[idx - ZTEK_MIX_NSTRIP_PORTS] += sv;
                }
            }
        }
    }
}

/* valeur effective d'un param de tranche : base + modulation baie, clampée 0..1 */
static inline float ztek_strip_eff(const ztek_t *z, int s, int p)
{
    float v = ztek_mix_get_strip(z, s, p) + z->strip_mod[s][p];
    return v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v;
}

static inline float ztek_master_eff(const ztek_t *z, int p)
{
    float v = ztek_mix_get_master(z, p) + z->master_mod[p];
    return v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v;
}

/* ---- page MACRO (16 slots assignables) ---------------------------------------------
 * Chaque slot = { dst, sub, val, used }. apply = la cible reçoit la valeur. */
static void ztek_macro_apply_int(ztek_t *z, int slot)
{
    int dst, sub;
    float v;
    if (slot < 0 || slot >= ZT_NMACRO) return;
    if (!z->macro[slot].used) return;
    dst = z->macro[slot].dst;
    sub = z->macro[slot].sub;
    v = z->macro[slot].val;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    if (dst < ZT_NDST) {                       /* voix / FX / seq : même chemin que la matrice */
        if (dst < AT_NPARAMS) ztek_set_param(z, dst, v);
        else if (dst < ZT_SEQ_DST0) ztek_fx_set(z, dst - AT_NPARAMS, v);
        else ztek_dst_set(z, dst, v);
        z->dst_base[dst] = v;
    } else if (dst >= ZT_MDST_AUX0 && dst < ZT_MDST_AUX0 + 16) {
        ml_set_sp(&z->loop, dst - ZT_MDST_AUX0, ML_SP_FX, v >= 0.5f ? 1.0f : 0.0f);   /* aux -> PoW! */
    } else if (dst >= ZT_MDST_PATCH && dst < ZT_MDST_PATCH + 12) {
        int c = dst - ZT_MDST_PATCH;           /* connexion baie row*3+col (compat respectée) */
        ztek_patch_set(z, c / 3, c % 3, v >= 0.5f ? 1 : 0);
    } else if (dst == ZT_MDST_POWT) {
        int t = (int)(v * (PN_NTYPES - 1) + 0.5f);
        if (t < 0) t = 0;
        if (t >= PN_NTYPES) t = PN_NTYPES - 1;
        z->punch_type = t;                     /* utilisé au prochain appui POW! */
    }
    (void)sub;
}

void ztek_macro_set(ztek_t *z, int slot, int dst, int sub, float val)
{
    if (slot < 0 || slot >= ZT_NMACRO) return;
    if (val < 0.0f) val = 0.0f;
    if (val > 1.0f) val = 1.0f;
    z->macro[slot].dst = dst;
    z->macro[slot].sub = sub;
    z->macro[slot].val = val;
    z->macro[slot].used = 1;
    z->dst_base[ZT_NDST + slot] = val;
}

void ztek_macro_apply(ztek_t *z, int slot)
{
    ztek_macro_apply_int(z, slot);
}

void ztek_macro_level(ztek_t *z, int slot, float v)
{
    if (slot < 0 || slot >= ZT_NMACRO) return;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    z->macro[slot].val = v;
    z->dst_base[ZT_NDST + slot] = v;
    ztek_macro_apply_int(z, slot);
}

void ztek_macro_erase(ztek_t *z, int slot)
{
    if (slot < 0 || slot >= ZT_NMACRO) return;
    z->macro[slot].used = 0;
    z->macro[slot].dst = 0;
    z->macro[slot].sub = 0;
    z->macro[slot].val = 0.0f;
    z->dst_base[ZT_NDST + slot] = 0.0f;
}

int ztek_macro_used(const ztek_t *z, int slot)
{
    if (slot < 0 || slot >= ZT_NMACRO) return 0;
    return z->macro[slot].used;
}

int ztek_macro_dst(const ztek_t *z, int slot)
{
    if (slot < 0 || slot >= ZT_NMACRO) return 0;
    return z->macro[slot].dst;
}

int ztek_macro_sub(const ztek_t *z, int slot)
{
    if (slot < 0 || slot >= ZT_NMACRO) return 0;
    return z->macro[slot].sub;
}

float ztek_macro_val(const ztek_t *z, int slot)
{
    if (slot < 0 || slot >= ZT_NMACRO) return 0.0f;
    return z->macro[slot].val;
}

int ztek_punch_type_get(const ztek_t *z) { return z->punch_type; }

/* one 32-sample block of modulation: sources, then the matrix */
static void ztek_mod_block(ztek_t *z, float dt)
{
    ztek_lfo_block(z, dt);
    ztek_lab_block(z, dt);
    ztek_env_block(z, dt);
    ztek_tool_block(z, dt);
    ztek_matrix_block(z);
    if (z->seq_wrapped) {
        z->seq_wrapped = 0;
        ztek_seq_wrap(z);
    }
}

/* ---- modulation API ---------------------------------------------------------------- */

void ztek_mod_knob(ztek_t *z, int src, int k, float v)
{
    if (src < 0 || src >= ZT_NSRC || k < 0 || k > 3) return;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    z->mod_knob[src][k] = v;
    /* derived fields */
    if (src >= 4 && src < 8) {
        if (k == 0) z->lab_type[src - 4] = (int)(v * 7.99f);
        else if (k == 1) z->lab_src[src - 4][0] = (int)(v * 16.999f) == 16 ? -1 : (int)(v * 15.99f);
        else if (k == 2) z->lab_src[src - 4][1] = (int)(v * 16.999f) == 16 ? -1 : (int)(v * 15.99f);
    }
}

float ztek_mod_knob_get(const ztek_t *z, int src, int k)
{
    if (src < 0 || src >= ZT_NSRC || k < 0 || k > 3) return 0.0f;
    return z->mod_knob[src][k];
}

void ztek_lfo_opt(ztek_t *z, int which, int on)
{
    if (which == 0) z->lfo_sync = on ? 1 : 0;
    else if (which == 1) z->lfo_trig = on ? 1 : 0;
}

void ztek_env_loop(ztek_t *z, int e, int on)
{
    if (e >= 0 && e < 2) z->env_loop[e] = on ? 1 : 0;
}

float ztek_src_val(const ztek_t *z, int src)
{
    if (src < 0 || src >= ZT_NSRC) return 0.0f;
    return z->src_val[src];
}

void ztek_assign(ztek_t *z, int dst, int src, float depth)
{
    if (dst < 0 || dst >= ZT_NDST_ALL) return;
    z->asg_src[dst] = src;
    if (depth < 0.0f) depth = 0.0f;
    if (depth > 1.0f) depth = 1.0f;
    z->asg_depth[dst] = depth;
}

int ztek_assign_src(const ztek_t *z, int dst)
{
    if (dst < 0 || dst >= ZT_NDST_ALL) return 255;
    return z->asg_src[dst];
}

float ztek_assign_depth(const ztek_t *z, int dst)
{
    if (dst < 0 || dst >= ZT_NDST_ALL) return 0.0f;
    return z->asg_depth[dst];
}

/* ---- pre-patched presets (ZuluFx style) ------------------------------------------- */

static const char *const ZT_PRESET_NAMES[ZT_NPRESETS] = {
    "ACID 303", "FILTRE WOBBLE", "PITCH DRIFT", "GATE 303",
    "SLIDE CITY", "LABO CHAOS", "TM MAX", "MAREE"
};

const char *ztek_preset_name(int i)
{
    if (i < 0) i = 0;
    if (i >= ZT_NPRESETS) i = ZT_NPRESETS - 1;
    return ZT_PRESET_NAMES[i];
}

static void ztek_preset_reset(ztek_t *z)
{
    for (int d = 0; d < ZT_NDST_ALL; d++) { z->asg_src[d] = 255; z->asg_depth[d] = 0.0f; }
    for (int k = 0; k < 5; k++) { z->seqk[k] = 0.0f; z->dst_base[ZT_SEQ_DST0 + 4 + k] = 0.0f; }
    z->lfo_sync = 0;
    z->lfo_trig = 0;
    for (int e = 0; e < 2; e++) z->env_loop[e] = 1;
    ztek_set_param(z, AT_CUTOFF, 0.7f);
    ztek_set_param(z, AT_RESONANCE, 0.25f);
    ztek_set_param(z, AT_ENVMOD, 0.5f);
    ztek_set_param(z, AT_DECAY, 0.55f);
    ztek_set_param(z, AT_WAVE, 0.0f);
    ztek_set_bpm(z, 120.0f);
}

void ztek_preset(ztek_t *z, int i)
{
    if (i < 0) i = 0;
    if (i >= ZT_NPRESETS) i = ZT_NPRESETS - 1;
    ztek_preset_reset(z);
    switch (i) {
    case 0:   /* ACID 303: sober defaults, TM off */
        break;
    case 1:   /* FILTRE WOBBLE: LFO1 SIN -> CUTOFF */
        ztek_mod_knob(z, 0, 0, 0.55f);   /* rate */
        ztek_mod_knob(z, 0, 1, 0.05f);   /* SIN */
        ztek_mod_knob(z, 0, 2, 0.7f);    /* depth */
        z->lfo_sync = 1;
        ztek_set_param(z, AT_CUTOFF, 0.35f);
        ztek_assign(z, AT_CUTOFF, 0, 0.7f);
        break;
    case 2:   /* PITCH DRIFT: LFO2 DRUNK -> TUNING */
        ztek_mod_knob(z, 1, 0, 0.35f);
        ztek_mod_knob(z, 1, 1, 0.85f);   /* DRUNK */
        ztek_mod_knob(z, 1, 2, 0.5f);
        ztek_set_param(z, AT_TUNING, 0.5f);
        ztek_assign(z, AT_TUNING, 1, 0.35f);
        break;
    case 3:   /* GATE 303: ENV1 loop -> ENVMOD + DECAY */
        ztek_mod_knob(z, 8, 0, 0.15f);   /* A */
        ztek_mod_knob(z, 8, 1, 0.4f);    /* D */
        ztek_mod_knob(z, 8, 2, 0.6f);    /* S */
        ztek_mod_knob(z, 8, 3, 0.5f);    /* R */
        ztek_assign(z, AT_ENVMOD, 8, 0.7f);
        ztek_assign(z, AT_DECAY, 8, 0.4f);
        break;
    case 4:   /* SLIDE CITY: PING-PONG + LFO3 SAW sync -> TEMPO */
        ztek_seq_knob(z, 8, 0.9f);       /* PLAY = PING-PONG */
        ztek_mod_knob(z, 2, 0, 0.5f);
        ztek_mod_knob(z, 2, 1, 0.55f);   /* SAW */
        ztek_mod_knob(z, 2, 2, 0.6f);
        z->lfo_sync = 1;
        ztek_assign(z, ZT_SEQ_DST0 + 2, 2, 0.15f);   /* -> TEMPO */
        break;
    case 5:   /* LABO CHAOS: LABO1 CHAOS(LFO1 SQ, LFO2) -> RESONANCE */
        ztek_mod_knob(z, 0, 1, 0.55f);   /* LFO1 SQ */
        ztek_mod_knob(z, 0, 0, 0.7f);
        ztek_mod_knob(z, 1, 1, 0.05f);   /* LFO2 SIN */
        ztek_mod_knob(z, 1, 0, 0.4f);
        ztek_mod_knob(z, 4, 0, 0.55f);   /* LABO1 type = CHAOS (4) */
        ztek_mod_knob(z, 4, 1, 0.0f);    /* SRC1 = LFO1 */
        ztek_mod_knob(z, 4, 2, 0.07f);   /* SRC2 = LFO2 */
        ztek_mod_knob(z, 4, 3, 0.8f);    /* DEPTH */
        ztek_assign(z, AT_RESONANCE, 4, 0.8f);
        break;
    case 6:   /* TM MAX: Turing Machine wide open */
        ztek_seq_knob(z, 5, 0.8f);       /* TM LOCK */
        ztek_seq_knob(z, 6, 0.2f);       /* TM LEN ~16 */
        ztek_seq_knob(z, 7, 0.4f);       /* MUT */
        ztek_seq_knob(z, 8, 0.9f);       /* PLAY PING-PONG */
        ztek_seq_knob(z, 9, 0.1f);       /* RAND */
        ztek_set_bpm(z, 138.0f);
        break;
    case 7:   /* MAREE: LABO4 MAREE(ENV1) -> REV MIX, ENV2 -> DLY TIME */
        ztek_mod_knob(z, 8, 0, 0.3f);
        ztek_mod_knob(z, 8, 1, 0.3f);
        ztek_mod_knob(z, 8, 2, 0.7f);
        ztek_mod_knob(z, 8, 3, 0.8f);    /* ENV1 slow */
        ztek_mod_knob(z, 9, 0, 0.1f);    /* ENV2 very slow */
        ztek_mod_knob(z, 9, 1, 0.1f);
        ztek_mod_knob(z, 9, 2, 0.5f);
        ztek_mod_knob(z, 9, 3, 0.9f);
        ztek_mod_knob(z, 7, 0, 0.9f);    /* LABO4 type = MAREE (7) */
        ztek_mod_knob(z, 7, 1, 0.53f);   /* SRC1 = ENV1 (8/16) */
        ztek_mod_knob(z, 7, 2, 0.6f);    /* SRC2 = ENV2 (9/16) */
        ztek_mod_knob(z, 7, 3, 0.7f);
        ztek_assign(z, 8 + FX_REV_MIX, 7, 0.6f);
        ztek_assign(z, 8 + FX_DLY_TIME, 9, 0.5f);
        break;
    }
}

/* ---- factory presets per part (bass/lead/drums), pre-patched with macros -------------
 * Each part gets its own 8 factory presets, built on the generic ztek_preset() (modulation
 * pre-assigned) + part-specific engine/sound + 4 macro slots filled (the "patch" containers).
 * ztek_bank_load() loads these when the slot has no user save. */
static const char *const ZT_PRESET_NAMES_PART[ZT_NPARTS][ZT_NPRESETS] = {
    /* BASSE */ {"SUB 808", "ACID LOW", "FUZZ BASS", "PLUK 303", "FM BASS", "DRONE SUB", "8BIT BASS", "TALK BASS"},
    /* LEAD  */ {"ACID 303", "FILTRE WOBBLE", "PITCH DRIFT", "GATE 303", "SLIDE CITY", "LABO CHAOS", "TM MAX", "MAREE"},
    /* DRUMS */ {"KIT ZULU", "808 HAT", "909 CLAP", "TM DRUMS", "GLITCH KIT", "POW LOOP", "RAND BOX", "SMASH BUS"},
};

const char *ztek_preset_name_part(int part, int i)
{
    if (part < 0) part = 0;
    if (part >= ZT_NPARTS) part = ZT_NPARTS - 1;
    if (i < 0) i = 0;
    if (i >= ZT_NPRESETS) i = ZT_NPRESETS - 1;
    return ZT_PRESET_NAMES_PART[part][i];
}

void ztek_preset_factory(ztek_t *z, int part, int i)
{
    if (i < 0) i = 0;
    if (i >= ZT_NPRESETS) i = ZT_NPRESETS - 1;
    if (part < 0) part = 0;
    if (part >= ZT_NPARTS) part = ZT_NPARTS - 1;
    ztek_preset(z, i);                     /* generic pre-patched base (LFO/ENV/labo/TM assigned) */
    if (part == 0) {                       /* BASSE: low root, bass engines (PLUK/FM/SUB/8BIT), dark filter */
        ztek_set_engine(z, 1 + (i % 4));
        ztek_set_root(z, z->root - 12);
        ztek_set_param(z, AT_CUTOFF, 0.3f);
        ztek_set_param(z, AT_DECAY, 0.75f);
        ztek_set_bpm(z, 100.0f + (float)(i % 4) * 5.0f);
        ztek_macro_set(z, 0, AT_CUTOFF, 0, 0.15f + 0.1f * (float)(i % 4));
        ztek_macro_set(z, 1, AT_RESONANCE, 0, 0.6f);
        ztek_macro_set(z, 2, AT_DECAY, 0, 0.9f);
        ztek_macro_set(z, 3, ZT_MDST_POWT, 0, 8.0f / (float)(PN_NTYPES - 1));   /* ECHO au prochain PoW! */
    } else if (part == 1) {                /* LEAD: ATEK303 bright, macros cutoff/envmod/delay */
        ztek_set_engine(z, 0);
        ztek_set_param(z, AT_CUTOFF, 0.85f);
        ztek_set_param(z, AT_RESONANCE, 0.45f);
        ztek_set_bpm(z, 128.0f);
        ztek_macro_set(z, 0, AT_CUTOFF, 0, 0.9f);
        ztek_macro_set(z, 1, AT_ENVMOD, 0, 0.8f);
        ztek_macro_set(z, 2, 8 + FX_DLY_TIME, 0, 0.4f);
    } else {                               /* DRUMS: SEQ+ heavy, loop return up, macros aux->PoW!/baie/PoW! type */
        ztek_set_engine(z, 0);
        ztek_seq_knob(z, 5, 0.7f);         /* TM LOCK */
        ztek_seq_knob(z, 8, 0.9f);         /* PLAY = PING-PONG */
        ztek_seq_knob(z, 9, 0.2f + 0.1f * (float)(i % 4));   /* RAND */
        ztek_mix_set_gain(z, 0.9f);
        ztek_macro_set(z, 0, ZT_MDST_AUX0 + 0, 0, 1.0f);    /* loop 0 -> PoW! */
        ztek_macro_set(z, 1, ZT_MDST_AUX0 + 1, 0, (i % 2) ? 1.0f : 0.0f);
        ztek_macro_set(z, 2, ZT_MDST_POWT, 0, 8.0f / (float)(PN_NTYPES - 1));   /* ECHO */
        ztek_macro_set(z, 3, ZT_MDST_PATCH + 0, 0, 1.0f);   /* baie: VOIX -> FX */
    }
}

/* ---- user presets per part (bass/lead/drums) -------------------------------------- */

static int ztek_pslot_ok(int part, int slot)
{
    return part >= 0 && part < ZT_NPARTS && slot >= 0 && slot < ZT_PSLOTS;
}

static int ztek_preset_magic_ok(uint32_t m)
{
    return m == ZTEK_PRESET_MAGIC || m == ZTEK_PRESET_MAGIC_V1;
}

/* remap strip_par d'un preset 'ZTP1' (BASSE=0,LEAD=1,DRUMS=2,LOOP=3) vers 'ZTP2'
 * (VOICE=0,DRUMS=1,LOOP=2,SIREN=3) — décision mixeur 4 tranches, docs/ZULUTEK-MIX-SIREN.md :
 * VOICE := ex-LEAD, DRUMS := ex-DRUMS, LOOP := ex-LOOP, SIREN := neuf (vol = siren_lvl, mute = siren off) */
static void ztek_preset_remap_v1(ztek_preset_t *p, float siren_lvl, int siren_on)
{
    float old[ZTEK_NSTRIP][ZTEK_MIX_N];
    memcpy(old, p->strip_par, sizeof old);
    for (int q = 0; q < ZTEK_MIX_N; q++) {
        p->strip_par[ZTEK_STRIP_VOICE][q] = old[ZTEK_V1_STRIP_LEAD][q];
        p->strip_par[ZTEK_STRIP_DRUMS][q] = old[ZTEK_V1_STRIP_DRUMS][q];
        p->strip_par[ZTEK_STRIP_LOOP][q]  = old[ZTEK_V1_STRIP_LOOP][q];
    }
    {
        float sir[ZTEK_MIX_N] = { 0.5f, 1.0f, siren_lvl, 0.5f, siren_on ? 0.0f : 1.0f,
                                  0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f };
        memcpy(p->strip_par[ZTEK_STRIP_SIREN], sir, sizeof sir);
    }
    p->magic = ZTEK_PRESET_MAGIC;
}

void ztek_preset_save(ztek_t *z, int part, int slot)
{
    if (!ztek_pslot_ok(part, slot)) return;
    ztek_preset_t *p = &z->pstore[part][slot];
    memset(p, 0, sizeof(*p));
    p->magic = ZTEK_PRESET_MAGIC;               /* 'ZTP2' */
    p->part = part;
    p->slot = slot;
    snprintf(p->name, sizeof(p->name), "P%d-%d", part + 1, slot + 1);
    p->engine = z->engine;
    memcpy(p->param, z->param, sizeof(p->param));
    memcpy(p->fx, z->fx_base, sizeof(p->fx));
    memcpy(p->opt, z->opt, sizeof(p->opt));
    p->bpm = z->bpm;
    p->root = z->root;
    p->scale = z->scale;
    memcpy(p->mod_knob, z->mod_knob, sizeof(p->mod_knob));
    memcpy(p->lab_type, z->lab_type, sizeof(p->lab_type));
    memcpy(p->lab_src, z->lab_src, sizeof(p->lab_src));
    p->lfo_sync = z->lfo_sync;
    p->lfo_trig = z->lfo_trig;
    memcpy(p->env_loop, z->env_loop, sizeof(p->env_loop));
    memcpy(p->asg_src, z->asg_src, sizeof(p->asg_src));
    memcpy(p->asg_depth, z->asg_depth, sizeof(p->asg_depth));
    memcpy(p->seqk, z->seqk, sizeof(p->seqk));
    for (int m = 0; m < ZT_NMACRO; m++) {
        p->mac_used[m] = (uint8_t)z->macro[m].used;
        p->mac_dst[m] = (int16_t)z->macro[m].dst;
        p->mac_sub[m] = (int16_t)z->macro[m].sub;
        p->mac_val[m] = z->macro[m].val;
    }
    for (int s = 0; s < ZTEK_NSTRIP; s++)
        for (int q = 0; q < ZTEK_MIX_N; q++) p->strip_par[s][q] = ztek_mix_get_strip(z, s, q);
    for (int q = 0; q < ZTEK_MIXM_N; q++) p->master_par[q] = ztek_mix_get_master(z, q);
    p->siren_wave = z->siren_wave;
    p->siren_mix = z->siren_mix;
}

void ztek_preset_load(ztek_t *z, int part, int slot)
{
    if (!ztek_pslot_ok(part, slot)) return;
    ztek_preset_t *p = &z->pstore[part][slot];
    if (!ztek_preset_magic_ok(p->magic)) return;
    if (p->magic == ZTEK_PRESET_MAGIC_V1) ztek_preset_remap_v1(p, z->siren_lvl, z->siren_on);
    ztek_set_engine(z, p->engine);
    for (int i = 0; i < AT_NPARAMS; i++) ztek_set_param(z, i, p->param[i]);
    for (int i = 0; i < FX_NPARAMS; i++) ztek_fx_set(z, i, p->fx[i]);
    for (int i = 0; i < ZTEK_NOPT; i++) ztek_set_option(z, i, p->opt[i]);
    ztek_set_bpm(z, p->bpm);
    ztek_set_root(z, p->root);
    ztek_set_scale(z, p->scale);
    for (int s = 0; s < ZT_NSRC; s++)
        for (int k = 0; k < 4; k++)
            ztek_mod_knob(z, s, k, p->mod_knob[s][k]);
    memcpy(z->lab_type, p->lab_type, sizeof(z->lab_type));
    memcpy(z->lab_src, p->lab_src, sizeof(z->lab_src));
    z->lfo_sync = p->lfo_sync;
    z->lfo_trig = p->lfo_trig;
    memcpy(z->env_loop, p->env_loop, sizeof(z->env_loop));
    for (int d = 0; d < ZT_NDST_ALL; d++) ztek_assign(z, d, p->asg_src[d], p->asg_depth[d]);
    for (int m = 0; m < ZT_NMACRO; m++) {
        z->macro[m].used = p->mac_used[m] ? 1 : 0;
        z->macro[m].dst = p->mac_dst[m];
        z->macro[m].sub = p->mac_sub[m];
        z->macro[m].val = p->mac_val[m];
        z->dst_base[ZT_NDST + m] = p->mac_val[m];
    }
    for (int s = 0; s < ZTEK_NSTRIP; s++)
        for (int q = 0; q < ZTEK_MIX_N; q++) ztek_mix_set_strip(z, s, q, p->strip_par[s][q]);
    for (int q = 0; q < ZTEK_MIXM_N; q++) ztek_mix_set_master(z, q, p->master_par[q]);
    ztek_siren_wave(z, p->siren_wave);
    ztek_siren_mix(z, p->siren_mix);
    for (int k = 0; k < 5; k++) ztek_seq_knob(z, 5 + k, p->seqk[k]);
}

int ztek_preset_used(const ztek_t *z, int part, int slot)
{
    if (!ztek_pslot_ok(part, slot)) return 0;
    return ztek_preset_magic_ok(z->pstore[part][slot].magic);
}

/* unified bank load: user save wins; otherwise the factory preset if the slot is in range */
void ztek_bank_load(ztek_t *z, int part, int slot)
{
    if (!ztek_pslot_ok(part, slot)) return;
    if (ztek_preset_magic_ok(z->pstore[part][slot].magic)) ztek_preset_load(z, part, slot);
    else if (slot < ZT_NPRESETS) ztek_preset_factory(z, part, slot);   /* factory pre-patched, per part */
}

void ztek_preset_clear(ztek_t *z, int part, int slot)
{
    if (!ztek_pslot_ok(part, slot)) return;
    memset(&z->pstore[part][slot], 0, sizeof(ztek_preset_t));
}

void ztek_preset_set_name(ztek_t *z, int part, int slot, const char *name)
{
    if (!ztek_pslot_ok(part, slot) || !name) return;
    ztek_preset_t *p = &z->pstore[part][slot];
    if (!ztek_preset_magic_ok(p->magic)) { p->magic = ZTEK_PRESET_MAGIC; p->part = part; p->slot = slot; }
    snprintf(p->name, sizeof(p->name), "%s", name);
}

const char *ztek_preset_slot_name(const ztek_t *z, int part, int slot)
{
    if (!ztek_pslot_ok(part, slot)) return "";
    return z->pstore[part][slot].name;
}

int ztek_preset_export(const ztek_t *z, int part, int slot, uint8_t *buf, int cap)
{
    if (!ztek_pslot_ok(part, slot)) return 0;
    if (!ztek_preset_magic_ok(z->pstore[part][slot].magic)) return 0;
    if (!buf || cap < (int)sizeof(ztek_preset_t)) return 0;
    memcpy(buf, &z->pstore[part][slot], sizeof(ztek_preset_t));
    return (int)sizeof(ztek_preset_t);
}

int ztek_preset_import(ztek_t *z, int part, int slot, const uint8_t *buf, int len)
{
    if (!ztek_pslot_ok(part, slot)) return 0;
    if (!buf || len != (int)sizeof(ztek_preset_t)) return 0;
    ztek_preset_t tmp;
    memcpy(&tmp, buf, sizeof(tmp));
    if (!ztek_preset_magic_ok(tmp.magic)) return 0;
    if (tmp.magic == ZTEK_PRESET_MAGIC_V1) ztek_preset_remap_v1(&tmp, z->siren_lvl, z->siren_on);
    tmp.part = part;
    tmp.slot = slot;
    z->pstore[part][slot] = tmp;
    return 1;
}

/* ---- sequencer advanced API -------------------------------------------------------- */

void ztek_seq_knob(ztek_t *z, int k, float v)
{
    if (k < 5 || k > 9) return;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    z->seqk[k - 5] = v;
    z->dst_base[ZT_SEQ_DST0 + 4 + (k - 5)] = v;
}

float ztek_seq_knob_get(const ztek_t *z, int k)
{
    if (k < 5 || k > 9) return 0.0f;
    return z->seqk[k - 5];
}

/* live mutation of the current 16-step page (the ATEK303 ENV/LFO buttons) */
void ztek_seq_mutate(ztek_t *z, int layer)
{
    int steps = z->pat.time_length;
    if (steps < 1) steps = 1;
    if (steps > ACID_MAX_STEPS) steps = ACID_MAX_STEPS;
    int page = (z->seq_step / 16) * 16;
    int lo = page, hi = page + 16;
    if (hi > steps) hi = steps;
    if (lo >= hi) { lo = 0; hi = steps; }
    int ops = 1 + (int)(z->seqk[2] * 7.0f);            /* MUT knob: 1..8 operations */
    uint32_t last = 0;
    acid_mutate_burst_range(&z->pat, layer, ztek_lcg(z), ops, z->scale, &last, lo, hi);
    acid_render(&z->pat, &z->grid);
}

void ztek_loop_mutate(ztek_t *z, int slot, int layer)
{
    ml_mutate(&z->loop, slot, layer);
}

void ztek_loop_randp(ztek_t *z, float p)
{
    ml_set_randp(&z->loop, p);
}

/* ---- arpégiateur (bouton ARP, page 13) ------------------------------------------- */

static const float ZTEK_ARP_SPB[6] = { 1.f, 2.f, 4.f, 8.f, 3.f, 6.f };  /* pas par temps : 1/4 1/8 1/16 1/32 1/8T 1/16T */

static uint32_t ztek_arp_rnd(ztek_t *z)
{
    z->arp_rng = z->arp_rng * 1664525u + 1013904223u;
    return z->arp_rng >> 8;
}

/* note jouée au clavier pendant que l'arp est on : enregistrée dans le looper + retenue */
static void ztek_arp_note_in(ztek_t *z, int note, int vel)
{
    int i;
    if (note < 0 || note > 127) return;
    ml_note_in(&z->loop, note, vel, 4, z->engine == 0 ? 0 : 1);
    for (i = 0; i < z->arp_n; i++) if (z->arp_held[i] == note) return;   /* déjà tenue */
    if (z->arp_n == 0) z->arp_idx = 0;                                   /* 1re note : on repart du début */
    if (z->arp_n < 16) {
        i = z->arp_n;
        while (i > 0 && z->arp_held[i - 1] > note) { z->arp_held[i] = z->arp_held[i - 1]; i--; }
        z->arp_held[i] = note;
        z->arp_n++;
    }
    if (z->arp_nord < 16) z->arp_order[z->arp_nord++] = note;
}

static void ztek_arp_note_out(ztek_t *z, int note)
{
    int i, j;
    for (i = 0; i < z->arp_n; i++) if (z->arp_held[i] == note) {
        for (j = i; j < z->arp_n - 1; j++) z->arp_held[j] = z->arp_held[j + 1];
        z->arp_n--;
        break;
    }
    for (i = 0; i < z->arp_nord; i++) if (z->arp_order[i] == note) {
        for (j = i; j < z->arp_nord - 1; j++) z->arp_order[j] = z->arp_order[j + 1];
        z->arp_nord--;
        break;
    }
    if (z->arp_n == 0 && z->arp_note >= 0) {                             /* plus rien tenu : silence */
        if (z->engine == 0) at_gate(&z->voice, 0, z->arp_note, 0, 0);
        else bass_gate(&z->bass, 0, z->arp_note, 0, 0);
        z->arp_note = -1;
        z->arp_gate = 0;
    }
}

static void ztek_arp_silence(ztek_t *z)
{
    if (z->arp_note >= 0) {
        if (z->engine == 0) at_gate(&z->voice, 0, z->arp_note, 0, 0);
        else bass_gate(&z->bass, 0, z->arp_note, 0, 0);
        z->arp_note = -1;
    }
    z->arp_gate = 0;
}

static void ztek_arp_step(ztek_t *z)
{
    int mode = (int)(z->arp_k[0] * 4.99f); if (mode < 0) mode = 0; if (mode > 4) mode = 4;
    int oct  = 1 + (int)(z->arp_k[1] * 3.99f); if (oct < 1) oct = 1; if (oct > 4) oct = 4;
    int div  = (int)(z->arp_k[2] * 5.99f); if (div < 0) div = 0; if (div > 5) div = 5;
    const int *base = (mode == 4) ? z->arp_order : z->arp_held;          /* AS-PLAYED = ordre de jeu */
    int base_n = (mode == 4) ? z->arp_nord : z->arp_n;
    int L, idx, note;
    if (base_n <= 0) return;
    L = base_n * oct;
    switch (mode) {
    case 1:  idx = L - 1 - (z->arp_idx % L); break;                      /* DOWN */
    case 2: { int p = z->arp_idx % (2 * L - 2);                          /* UP-DOWN (ping-pong) */
              idx = p < L ? p : 2 * L - 2 - p; break; }
    case 3:  idx = (int)(ztek_arp_rnd(z) % (uint32_t)L); break;           /* RANDOM */
    default: idx = z->arp_idx % L; break;                                /* UP / AS-PLAYED */
    }
    z->arp_idx++;
    note = base[idx % base_n] + 12 * (idx / base_n);
    if (note < 0) note = 0;
    if (note > 127) note = 127;
    if (z->arp_note >= 0 && z->arp_note != note) ztek_arp_silence(z);
    ztek_note_on_int(z, note, 100, 0, 0, 0);                             /* généré : pas enregistré dans le looper */
    z->arp_note = note;
    {
        float gate = z->arp_k[3] * 0.95f + 0.05f;
        float step_s = z->sr / ((z->bpm / 60.0f) * ZTEK_ARP_SPB[div]);
        z->arp_gate = (int32_t)(gate * step_s);
    }
}

void ztek_arp_set_on(ztek_t *z, int on)
{
    on = on ? 1 : 0;
    if (z->arp_on == on) return;
    z->arp_on = on;
    if (!on) {                                                           /* coupure : silence propre */
        ztek_arp_silence(z);
        z->arp_n = 0; z->arp_nord = 0;
        z->arp_clk = 0.0f;
    } else {
        z->arp_idx = 0;
        z->arp_clk = 1.0f;                                               /* démarre au prochain bloc */
    }
}

int ztek_arp_get_on(const ztek_t *z) { return z->arp_on; }

void ztek_arp_knob(ztek_t *z, int k, float v)
{
    if (k < 0 || k > 3) return;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    z->arp_k[k] = v;
}

float ztek_arp_knob_get(const ztek_t *z, int k)
{
    if (k < 0 || k > 3) return 0.0f;
    return z->arp_k[k];
}

int ztek_arp_held(const ztek_t *z) { return z->arp_n; }

/* ---- pedalboard FX (menu FX, REC+FX) -------------------------------------------------- */

const char *const ZTEK_FXT_NAME[FXT_NCAT] = {
    "OFF", "DRIVE", "CHORUS", "FLANGER", "VIBRATO", "SVF", "WAH",
    "SLAP", "ECHO", "DOTTED", "BBD", "TAPE", "GATE", "OCT",
    "AUTOPAN", "RINGMOD", "TREM", "REV PLATE", "REV SPRING", "ROOM"
};

static int ztek_fxt_heavy(int t) { return t == FXT_REV_PLATE || t == FXT_BBD || t == FXT_TAPE; }

void ztek_fxslot_set(ztek_t *z, int slot, int type, float p0, float p1, float p2, float p3, float mix)
{
    if (slot < 0 || slot >= ZTEK_NSLOT) return;
    if (type < 0 || type >= FXT_NCAT) type = FXT_OFF;
    ztek_s::fxslot_t *s = &z->fxslot[slot];
    if (s->type != type) { memset(s->st, 0, sizeof s->st); memset(s->dbuf, 0, sizeof s->dbuf); memset(s->rvb, 0, sizeof s->rvb); }
    s->type = type;
    s->p[0] = p0; s->p[1] = p1; s->p[2] = p2; s->p[3] = p3;
    s->mix = mix < 0.0f ? 0.0f : mix > 1.0f ? 1.0f : mix;
}

int   ztek_fxslot_type(const ztek_t *z, int slot) { return (slot >= 0 && slot < ZTEK_NSLOT) ? z->fxslot[slot].type : FXT_OFF; }
float ztek_fxslot_par(const ztek_t *z, int slot, int k)
{
    if (slot < 0 || slot >= ZTEK_NSLOT || k < 0 || k > 3) return 0.0f;
    return z->fxslot[slot].p[k];
}
float ztek_fxslot_mix(const ztek_t *z, int slot) { return (slot >= 0 && slot < ZTEK_NSLOT) ? z->fxslot[slot].mix : 0.0f; }

void ztek_fxchain_order(ztek_t *z, const int order[ZTEK_NCHAIN])
{
    int seen[ZTEK_NCHAIN] = {0,0,0,0};
    for (int i = 0; i < ZTEK_NCHAIN; i++) {
        int c = order[i];
        if (c < 0 || c >= ZTEK_NCHAIN || seen[c]) c = i;
        seen[c] = 1;
        z->chain_order[i] = c;
    }
}

void ztek_fxchain_order_get(const ztek_t *z, int out[ZTEK_NCHAIN])
{
    for (int i = 0; i < ZTEK_NCHAIN; i++) out[i] = z->chain_order[i];
}

void ztek_fxchain_mix(ztek_t *z, int chain, float m)
{
    if (chain < 0 || chain >= ZTEK_NCHAIN) return;
    z->chain_mix[chain] = m < 0.0f ? 0.0f : m > 1.0f ? 1.0f : m;
}

float ztek_fxchain_mix_get(const ztek_t *z, int chain)
{
    return (chain >= 0 && chain < ZTEK_NCHAIN) ? z->chain_mix[chain] : 1.0f;
}

void ztek_fxblock_save(ztek_t *z, int blk)
{
    if (blk < 0 || blk >= ZTEK_NBLOCK) return;
    z->fxblock[blk].used = 1;
    snprintf(z->fxblock[blk].name, sizeof z->fxblock[blk].name, "BLOC%d", blk + 1);
    for (int s = 0; s < ZTEK_NSLOT; s++) {
        z->fxblock_st[blk].type[s] = z->fxslot[s].type;
        memcpy(z->fxblock_st[blk].p[s], z->fxslot[s].p, sizeof z->fxslot[s].p);
        z->fxblock_st[blk].mix[s] = z->fxslot[s].mix;
    }
    memcpy(z->fxblock_st[blk].order, z->chain_order, sizeof z->chain_order);
    memcpy(z->fxblock_st[blk].cmix, z->chain_mix, sizeof z->chain_mix);
}

void ztek_fxblock_load(ztek_t *z, int blk)
{
    if (blk < 0 || blk >= ZTEK_NBLOCK || !z->fxblock[blk].used) return;
    for (int s = 0; s < ZTEK_NSLOT; s++) {
        z->fxslot[s].type = z->fxblock_st[blk].type[s];
        memcpy(z->fxslot[s].p, z->fxblock_st[blk].p[s], sizeof z->fxslot[s].p);
        z->fxslot[s].mix = z->fxblock_st[blk].mix[s];
        memset(z->fxslot[s].st, 0, sizeof z->fxslot[s].st);
    }
    memcpy(z->chain_order, z->fxblock_st[blk].order, sizeof z->chain_order);
    memcpy(z->chain_mix, z->fxblock_st[blk].cmix, sizeof z->chain_mix);
}

int ztek_fxblock_used(const ztek_t *z, int blk)
{
    return (blk >= 0 && blk < ZTEK_NBLOCK) ? z->fxblock[blk].used : 0;
}

/* petit SVF (state-variable filter) 2 pôles, par slot. mode: 0 LP 1 HP 2 BP */
static void ztek_svf(ztek_s::fxslot_t *s, float x, float sr, int mode, float *out)
{
    float f = 40.0f * fm_exp2f(7.0f * s->p[0]);
    float q = 0.5f + 8.0f * s->p[1];
    float a = FM_TWO_PI * f / sr;
    if (a > 1.0f) a = 1.0f;
    float lp = s->st[0] + a * s->st[1];
    float hp = x - lp - s->st[1] / q;
    float bp = s->st[1];
    s->st[1] += a * hp;
    s->st[0] = lp;
    *out = mode == 0 ? lp : mode == 1 ? hp : bp;
}

/* module léger d'un slot, in-place sur l/r (n échantillons). Retourne 1 si traité ici. */
static int ztek_slot_light(ztek_t *z, int slot, float *l, float *r, uint32_t n)
{
    ztek_s::fxslot_t *s = &z->fxslot[slot];
    float sr = z->sr, fsr = 1.0f / sr;
    uint32_t i;
    switch (s->type) {
    case FXT_DRIVE: {   /* p0 gain, p1 tone, p2 level, p3 variante 0 od/1 rat/2 fuzz/3 mojo+crush */
        float g = fm_exp2f(6.0f * s->p[0]);
        int var = (int)(s->p[3] * 3.99f);
        for (i = 0; i < n; i++) {
            float x = l[i] * g;
            float y;
            if (var == 1) y = x > 0.7f ? 0.7f : x < -0.7f ? -0.7f : x;            /* RAT hard */
            else if (var == 2) y = fm_tanhf(x * 3.0f) * (x > 0 ? 1.2f : 0.6f);   /* FUZZ asymétrique */
            else if (var == 3) { float b = 8.0f - s->p[1] * 6.0f; if (b < 1) b = 1;
                                 float lv = fm_exp2f(b - 1); y = fm_floorf(fm_tanhf(x * 1.5f) * lv + 0.5f) / lv; } /* MOJO+crush */
            else y = fm_tanhf(x * 1.2f);                                          /* OD soft */
            s->st[2] += (1.0f - fm_expf(-FM_TWO_PI * (200.0f + 8000.0f * s->p[1]) / sr)) * (y - s->st[2]);
            float o = s->st[2] * (0.3f + 1.4f * s->p[2]);
            l[i] = o; r[i] = o;
        }
        return 1;
    }
    case FXT_TREM: {
        float rate = 0.2f + 8.0f * s->p[0];
        for (i = 0; i < n; i++) {
            s->st[0] += rate * FM_TWO_PI * fsr;
            if (s->st[0] > FM_TWO_PI) s->st[0] -= FM_TWO_PI;
            float a = 0.5f + 0.5f * fm_sinf(s->st[0]);
            a = a * a * (1.0f - s->p[1]) + s->p[1];   /* p1 = profondeur inverse (forme) */
            l[i] *= a; r[i] *= a;
        }
        return 1;
    }
    case FXT_RINGMOD: {
        float f = 5.0f * fm_exp2f(8.0f * s->p[0]);   /* 5 Hz .. 1.2 kHz */
        for (i = 0; i < n; i++) {
            s->st[0] += f * fsr;
            if (s->st[0] >= 1.0f) s->st[0] -= 1.0f;
            float m = fm_sin_turns(s->st[0]);
            l[i] *= m; r[i] *= m;
        }
        return 1;
    }
    case FXT_AUTOPAN: {
        float rate = 0.1f + 4.0f * s->p[0];
        float depth = s->p[1];
        for (i = 0; i < n; i++) {
            s->st[0] += rate * FM_TWO_PI * fsr;
            if (s->st[0] > FM_TWO_PI) s->st[0] -= FM_TWO_PI;
            float p = 0.5f + 0.5f * depth * fm_sinf(s->st[0]);
            float pl = p <= 0.5f ? 1.0f : (1.0f - p) * 2.0f;
            float pr = p >= 0.5f ? 1.0f : p * 2.0f;
            l[i] *= pl; r[i] *= pr;
        }
        return 1;
    }
    case FXT_GATE: {   /* p0 = seuil, p1 = hold (s), p2 = retrig rate pattern */
        float thr = 0.02f + 0.5f * s->p[0];
        float hold = (int32_t)(s->p[1] * sr);
        for (i = 0; i < n; i++) {
            float a = l[i] < 0 ? -l[i] : l[i];
            if (a > s->st[1]) s->st[1] = a; else s->st[1] *= 0.999f;
            int open = s->st[1] > thr || s->st[2] > 0;
            if (open && s->st[2] > 0) s->st[2]--;
            if (!open && s->st[1] > thr) s->st[2] = hold;
            float g = (s->st[1] > thr || s->st[2] > 0) ? 1.0f : 0.0f;
            l[i] *= g; r[i] *= g;
        }
        return 1;
    }
    case FXT_OCT: {    /* sub-octave : redressement + LP, p0 = amount */
        float ca = 1.0f - fm_expf(-FM_TWO_PI * 300.0f / sr);
        for (i = 0; i < n; i++) {
            float x = l[i];
            float sub = x > 0 ? x : -x;      /* redressement pleine onde ~ octave bas */
            s->st[0] += ca * (sub - s->st[0]);
            float o = x * (1.0f - s->p[0]) + s->st[0] * 2.0f * s->p[0];
            l[i] = o; r[i] = o;
        }
        return 1;
    }
    case FXT_SVF: {    /* p0 cutoff, p1 reso, p2 mode (0 LP 1 HP 2 BP) */
        int mode = (int)(s->p[2] * 2.99f);
        for (i = 0; i < n; i++) {
            float o;
            ztek_svf(s, l[i], sr, mode, &o);
            l[i] = o; r[i] = o;
        }
        return 1;
    }
    case FXT_WAH: {    /* auto-wah : enveloppe -> cutoff du SVF LP */
        float peak = s->st[3];
        float ca = 1.0f - fm_expf(-FM_TWO_PI * 2000.0f / sr);
        for (i = 0; i < n; i++) {
            float x = l[i]; float a = x < 0 ? -x : x;
            if (a > peak) peak = a; else peak *= 0.9995f;
            s->p[0] = 0.05f + 0.95f * (peak / (peak + 0.1f)) * (0.3f + 0.7f * s->p[1]);
            float o;
            ztek_svf(s, x, sr, 0, &o);
            l[i] = o; r[i] = o;
        }
        s->st[3] = peak;
        (void)ca;
        return 1;
    }
    case FXT_SLAP: case FXT_ECHO: case FXT_DOTTED: case FXT_CHORUS: case FXT_FLANGER: case FXT_VIBRATO: {
        /* ligne à retard par slot dans dbuf[2048] */
        float *d = s->dbuf;
        int dlen = 2048;
        uint32_t pos = (uint32_t)s->st[3];
        float ms, fb = 0.0f, lfoRate = 0.0f, lfoDepth = 0.0f, mod = 0.0f;
        int isMod = (s->type == FXT_CHORUS || s->type == FXT_FLANGER || s->type == FXT_VIBRATO);
        if (s->type == FXT_SLAP) ms = 30.0f + 90.0f * s->p[0];
        else if (s->type == FXT_DOTTED) ms = 750.0f * (60.0f / (z->bpm > 20.0f ? z->bpm : 120.0f));  /* dotted 1/8 */
        else if (s->type == FXT_ECHO) ms = 60.0f + 340.0f * s->p[0];
        else ms = 8.0f;   /* mod: base 8 ms */
        if (isMod) { lfoRate = 0.2f + 5.0f * s->p[0]; lfoDepth = s->p[1] * 8.0f; fb = s->type == FXT_FLANGER ? s->p[2] * 0.7f : 0.0f; mod = 1.0f; }
        else if (s->type == FXT_ECHO) fb = s->p[1] * 0.8f;
        int d0 = (int)(ms * 0.001f * sr);
        if (d0 < 2) d0 = 2; if (d0 >= dlen - 16) d0 = dlen - 16;
        float damp = 1.0f - fm_expf(-FM_TWO_PI * (400.0f + 6000.0f * s->p[2]) / sr);
        for (i = 0; i < n; i++) {
            float x = (l[i] + r[i]) * 0.5f;
            float rd;
            if (isMod) {
                s->st[0] += lfoRate * FM_TWO_PI * fsr;
                if (s->st[0] > FM_TWO_PI) s->st[0] -= FM_TWO_PI;
                int dd = d0 + (int)(lfoDepth * fm_sinf(s->st[0]));
                if (dd < 2) dd = 2; if (dd >= dlen) dd = dlen - 1;
                uint32_t rp = pos - dd; if ((int)rp < 0) rp += dlen;
                rd = d[rp];
            } else {
                uint32_t rp = pos - d0; if ((int)rp < 0) rp += dlen;
                rd = d[rp];
                s->st[1] += damp * (rd - s->st[1]);
                rd = s->st[1];
            }
            d[pos] = x + rd * fb;
            pos++; if (pos >= (uint32_t)dlen) pos = 0;
            float wet = isMod ? (mod ? rd : rd) : rd;
            if (s->type == FXT_VIBRATO) { l[i] = wet; r[i] = wet; }
            else if (isMod) {
                float w = s->p[2] * 0.5f + 0.5f;   /* p2 = amount du mod */
                l[i] = x * (1.0f - w) + rd * w; r[i] = x * (1.0f - w) + rd * w;
            } else {
                l[i] += wet; r[i] += wet;         /* slap/echo: on ajoute (dry/wet géré par mix du slot) */
            }
        }
        s->st[3] = (float)pos;
        return 1;
    }
    case FXT_REV_SPRING: case FXT_ROOM: {
        /* mini-FDN dans rvb[2048] int16 : spring = 2 combs, room = 4 combs */
        int nc = (s->type == FXT_REV_SPRING) ? 2 : 4;
        int clen = 2048 / nc;
        static const int plen[4] = { 911, 1229, 1543, 1871 };  /* premiers, < clen */
        float decay = 0.5f + 0.49f * s->p[0];
        float damp = 1.0f - fm_expf(-FM_TWO_PI * (1500.0f + 5000.0f * s->p[1]) / sr);
        for (i = 0; i < n; i++) {
            float x = (l[i] + r[i]) * 0.5f;
            float acc = 0.0f;
            for (int c = 0; c < nc; c++) {
                int off = c * clen;
                int L = plen[c] % (clen - 8); if (L < 8) L = 8;
                int rp = (int)s->st[c] - L; if (rp < 0) rp += clen;
                float dv = (float)s->rvb[off + rp] * (1.0f / 32768.0f);
                s->st[1] += damp * (dv - s->st[1]);
                float wv = x * 0.25f + s->st[1] * decay;
                s->rvb[off + (int)s->st[c]] = (int16_t)(wv * 32767.0f);
                s->st[c] += 1.0f; if (s->st[c] >= clen) s->st[c] = 0.0f;
                acc += dv;
            }
            float wet = acc * 2.0f;
            l[i] += wet; r[i] += wet;
        }
        return 1;
    }
    default:
        return 0;   /* OFF ou lourd -> géré ailleurs */
    }
}

/* module lourd (REV_PLATE / BBD / TAPE) : délégué à la chaîne fx.c globale (exemplaire unique). */
static void ztek_slot_heavy(ztek_t *z, int slot, const float *in, float *outL, float *outR, uint32_t n)
{
    ztek_s::fxslot_t *s = &z->fxslot[slot];
    float saved[FX_NPARAMS];
    for (int p = 0; p < FX_NPARAMS; p++) saved[p] = fx_get(p);
    for (int p = 0; p < FX_NPARAMS; p++) fx_set(p, 0.0f);
    if (s->type == FXT_REV_PLATE) {
        fx_set(FX_REV_DECAY, s->p[0]); fx_set(FX_REV_DAMP, s->p[1]);
        fx_set(FX_REV_PRE, s->p[2]);   fx_set(FX_REV_MIX, 1.0f);
    } else {   /* BBD / TAPE */
        fx_set(FX_DLY_TIME, s->p[0]); fx_set(FX_DLY_FEEDBACK, s->p[1]);
        fx_set(FX_DLY_TONE, s->p[2]); fx_set(FX_DLY_MIX, 1.0f);
    }
    fx_process(in, outL, outR, n);
    for (int p = 0; p < FX_NPARAMS; p++) fx_set(p, saved[p]);
}

/* le pedalboard complet : 4 chaînes dans l'ordre chain_order, 3 slots chacune,
 * dry/wet par slot (dans la chaîne) puis dry/wet par chaîne (SEL de la pédale). */
static void ztek_pedalboard(ztek_t *z, const float *inL, const float *inR, float *oL, float *oR, uint32_t n)
{
    /* pwL/pwR = signal courant ; pwCI/pwCO = entrée de la chaîne courante ; pwW/pwW2 = wet */
    memcpy(z->pwL, inL, n * sizeof(float));
    memcpy(z->pwR, inR, n * sizeof(float));
    int heavy_used = 0;
    for (int ci = 0; ci < ZTEK_NCHAIN; ci++) {
        int c = z->chain_order[ci];
        float cmix = z->chain_mix[c];
        memcpy(z->pwCI, z->pwL, n * sizeof(float));
        memcpy(z->pwCO, z->pwR, n * sizeof(float));
        for (int k = 0; k < ZTEK_SPC; k++) {
            int slot = c * ZTEK_SPC + k;
            ztek_s::fxslot_t *s = &z->fxslot[slot];
            if (s->type == FXT_OFF) continue;
            float m = s->mix;
            if (ztek_fxt_heavy(s->type)) {
                if (heavy_used) continue;                    /* exemplaire unique */
                heavy_used = 1;
                for (uint32_t i = 0; i < n; i++) z->pwW[i] = (z->pwL[i] + z->pwR[i]) * 0.5f;
                ztek_slot_heavy(z, slot, z->pwW, z->pwW, z->pwW2, n);
                for (uint32_t i = 0; i < n; i++) {
                    z->pwL[i] = z->pwL[i] * (1.0f - m) + z->pwW[i] * m;
                    z->pwR[i] = z->pwR[i] * (1.0f - m) + z->pwW2[i] * m;
                }
            } else {
                memcpy(z->pwW, z->pwL, n * sizeof(float));
                memcpy(z->pwW2, z->pwR, n * sizeof(float));
                ztek_slot_light(z, slot, z->pwW, z->pwW2, n);
                for (uint32_t i = 0; i < n; i++) {
                    z->pwL[i] = z->pwL[i] * (1.0f - m) + z->pwW[i] * m;
                    z->pwR[i] = z->pwR[i] * (1.0f - m) + z->pwW2[i] * m;
                }
            }
        }
        for (uint32_t i = 0; i < n; i++) {                   /* dry/wet de la pédale (chaîne) */
            z->pwL[i] = z->pwCI[i] * (1.0f - cmix) + z->pwL[i] * cmix;
            z->pwR[i] = z->pwCO[i] * (1.0f - cmix) + z->pwR[i] * cmix;
        }
    }
    memcpy(oL, z->pwL, n * sizeof(float));
    memcpy(oR, z->pwR, n * sizeof(float));
}

void ztek_render(ztek_t *z, float *outL, float *outR, uint32_t n)
{
    uint32_t done = 0;
    if (n == 0) return;
    while (done < n) {
        uint32_t blk = n - done;
        if (blk > ZTEK_MAX_BLOCK) blk = ZTEK_MAX_BLOCK;

        /* 0. modulation sources + matrix + seq wrap, per 32-sample block */
        {
            uint32_t i;
            for (i = 0; i < blk; i += 32) {
                uint32_t nb = blk - i;
                if (nb > 32) nb = 32;
                ztek_mod_block(z, (float)nb / z->sr);
            }
        }

        /* 1. the looper's clock; its events fire the synth through the callbacks */
        ml_render(&z->loop, blk);

        /* 2. the acid sequencer, one 1/64 bar at a time — transport indépendant du looper
         * (bouton SEQ = acid_on, SHIFT+PLAY hors pages DRUMS/LOOP = pareil) : le synth seq
         * a sa propre marche/arrêt, il ne suit plus le play du looper (patch APK 0023) */
        if (z->acid_on) {
            float rate = z->bpm * 16.0f / 60.0f / z->sr;   /* 1/64 bar per sample */
            uint32_t i;
            for (i = 0; i < blk; i++) {
                z->acid_clk += rate;
                while (z->acid_clk >= 1.0f) {
                    z->acid_clk -= 1.0f;
                    ztek_acid_step(z);
                }
            }
        }

        /* 2b. l'arpégiateur : horloge en pas + gate sur la durée du pas */
        if (z->arp_on) {
            int div = (int)(z->arp_k[2] * 5.99f); if (div < 0) div = 0; if (div > 5) div = 5;
            float rate = (z->bpm / 60.0f) * ZTEK_ARP_SPB[div] / z->sr;     /* pas par échantillon */
            uint32_t i;
            for (i = 0; i < blk; i++) {
                if (z->arp_gate > 0 && --z->arp_gate == 0 && z->arp_note >= 0) ztek_arp_silence(z);
                z->arp_clk += rate;
                while (z->arp_clk >= 1.0f) {
                    z->arp_clk -= 1.0f;
                    ztek_arp_step(z);
                }
            }
        }

        /* 3. the synth bus: engine 0 is the ATEK303 voice, 1..4 the bass engines */
        if (z->engine == 0) at_render(&z->voice, z->mono, blk);
        else bass_render(&z->bass, z->mono, blk);
        {
            float pk = 0.0f;
            uint32_t i;
            for (i = 0; i < blk; i++) {
                float a = z->mono[i];
                if (a < 0.f) a = -a;
                if (a > pk) pk = a;
            }
            z->blk_peak = pk;
        }

        /* sirène : un buffer par bloc (le LFO siren_sw avance par bloc), puis routage baie */
        if (z->siren_on) {
            float fsr = 1.0f / z->sr;
            float period = 0.5f + 7.5f * z->siren_t;
            z->siren_sw += (float)blk / (z->sr * period);
            if (z->siren_sw >= 1.0f) z->siren_sw -= 1.0f;
            for (uint32_t i = 0; i < blk; i++)
                z->sirenbuf[i] = ztek_siren_sample(z, fsr);
        }
        /* sirène -> FX ou AUX1 = pré-FX (dans mono) */
        if (z->siren_on && (ztek_bit_get(z->assign[ZTEK_PSRC_SIREN], ZTEK_PORT_FXIN) ||
                            ztek_bit_get(z->assign[ZTEK_PSRC_SIREN], ZTEK_PORT_AUX1))) {
            float pre = 0.25f * z->siren_lvl * (1.0f - z->siren_mix);
            for (uint32_t i = 0; i < blk; i++)
                z->mono[i] += z->sirenbuf[i] * pre;
        }
        /* SMP -> FX : le sample est mixé dans le bus pré-FX */
        int smp_prefx = z->smp_play && z->smp_len > 0 &&
                        (ztek_bit_get(z->assign[ZTEK_PSRC_SMP], ZTEK_PORT_FXIN) ||
                         ztek_bit_get(z->assign[ZTEK_PSRC_SMP], ZTEK_PORT_AUX1));
        if (smp_prefx) {
            uint32_t s0 = (uint32_t)(z->smp_start * (float)z->smp_len);
            uint32_t s1 = (uint32_t)(z->smp_end * (float)z->smp_len);
            if (s1 <= s0) s1 = s0 + 1;
            if (s1 > z->smp_len) s1 = z->smp_len;
            float semis = (z->smp_pitch - 0.5f) * 48.0f;
            float ratio = fm_exp2f(semis / 12.0f);
            if (z->smp_mode == 3) ratio *= 0.5f;
            uint32_t span = s1 - s0;
            for (uint32_t i = 0; i < blk; i++) {
                if (z->smp_delay > 0) { z->smp_delay--; continue; }
                uint32_t pp = (uint32_t)z->smp_pos;
                if (pp >= span) { z->smp_play = 0; break; }
                float fr = (float)pp - fm_floorf((float)pp);
                uint32_t p1 = pp + 1 < span ? pp + 1 : pp;
                float x = z->smp[s0 + pp] + (z->smp[s0 + p1] - z->smp[s0 + pp]) * fr;
                z->mono[i] += x * z->smp_vol * 0.9f;
                z->smp_pos += (double)ratio;
            }
        }

        /* 3c. DRUMS : bus propre rendu dans drumbuf (la tranche DRUMS du mixage en est la sortie).
         * Le séquenceur drums a son PROPRE transport (SHIFT+PLAY page DRUMS, patch APK 0023) :
         * drum_tr = transport (reset au passage à ON), drum_on = mute locale (PLAY page DRUMS) ;
         * l'horloge n'avance que si transport on, les pads manuels et les fins de voix sonnent toujours. */
        z->drum.run = (uint8_t)(z->drum_tr && z->drum_on ? 1 : 0);
        memset(z->drumbuf, 0, blk * sizeof(float));
        drum_render(&z->drum, z->drumbuf, (int)blk);

        /* 4. table de mixage : 4 tranches BASSE/LEAD/DRUMS/LOOP — chaîne par tranche :
         *    FILTRE traversant pré-fader (LP/neutre/HP + CUT) -> PAN pré-fader -> VOL -> MUT/SOLO,
         *    somme stéréo sur le bus master ; départs auxiliaires post-fader (L -> Loop, P -> PoW!) */
        memset(z->mixbuf, 0, blk * sizeof(float));    /* somme mono = entrée de la chaîne FX */
        memset(z->mixbufL, 0, blk * sizeof(float));   /* bus master stéréo (pan pré-fader) */
        memset(z->mixbufR, 0, blk * sizeof(float));
        memset(z->sendbuf, 0, blk * sizeof(float));
        memset(z->loopbuf, 0, blk * sizeof(float));
        memset(z->rsbuf, 0, blk * sizeof(float));
        ml_rs_render(&z->loop, z->loopbuf, z->rsbuf, blk);       /* audio du loop -> tranche LOOP */
        {
            const float *src[ZTEK_NSTRIP];
            int any_solo = 0;
            int s;
            src[ZTEK_STRIP_VOICE]  = z->mono;                        /* bus voix unique (engine 0 = lead, 1..4 = basses) */
            src[ZTEK_STRIP_DRUMS]  = z->drumbuf;
            src[ZTEK_STRIP_LOOP]   = z->loopbuf;
            src[ZTEK_STRIP_SIREN]  = NULL;   /* sirène routée via la baie (ZTEK_PSRC_SIREN) ; la tranche pilote siren_lvl/siren_on */
            for (s = 0; s < ZTEK_NSTRIP; s++)
                if (ztek_strip_eff(z, s, ZTEK_MIX_SOLO) > 0.5f) any_solo = 1;
            for (s = 0; s < ZTEK_NSTRIP; s++) {
                ztek_s::strip_t *st = &z->strip[s];
                const float *in = src[s];
                float fc, ca, cut, vol, pan, aux_loop, aux_pow;
                uint32_t i;
                int fmode, muted;
                if (!in) continue;
                cut = ztek_strip_eff(z, s, ZTEK_MIX_CUT);
                fmode = (int)(ztek_strip_eff(z, s, ZTEK_MIX_FMODE) + 0.5f);
                if (fmode < 0) fmode = 0; if (fmode > 2) fmode = 2;
                vol = ztek_strip_eff(z, s, ZTEK_MIX_VOL);
                pan = ztek_strip_eff(z, s, ZTEK_MIX_PAN);
                aux_loop = ztek_strip_eff(z, s, ZTEK_MIX_AUX_LOOP);
                aux_pow = ztek_strip_eff(z, s, ZTEK_MIX_AUX_POW);
                muted = (ztek_strip_eff(z, s, ZTEK_MIX_MUT) > 0.5f) ||
                        (any_solo && ztek_strip_eff(z, s, ZTEK_MIX_SOLO) <= 0.5f);
                fc = 40.0f * fm_exp2f(7.0f * cut);                        /* 40 Hz .. 5 kHz */
                ca = 1.0f - fm_expf(-FM_TWO_PI * fc / z->sr);
                {
                    float panL = pan <= 0.5f ? 1.0f : (1.0f - pan) * 2.0f;
                    float panR = pan >= 0.5f ? 1.0f : pan * 2.0f;
                    for (i = 0; i < blk; i++) {
                        float x = in[i];
                        st->lp += ca * (x - st->lp);
                        float hp = x - st->lp;
                        float f = fmode == 0 ? st->lp : (fmode == 2 ? hp : x);
                        float v = f * vol * (muted ? 0.0f : 1.0f);
                        z->mixbuf[i] += v;                              /* somme mono -> entrée FX */
                        z->mixbufL[i] += v * panL;                      /* bus master stéréo */
                        z->mixbufR[i] += v * panR;
                        if (s != ZTEK_STRIP_LOOP)
                            z->sendbuf[i] += v * aux_loop;             /* départ post-fader vers Loop */
                        /* aux_pow (départ vers PoW!) reste un niveau stocké/modulable : la chaîne
                         * PoW! est un insert post-master (fx_punch), pas un bus séparé */
                        (void)aux_pow;
                    }
                }
            }
        }
        ml_rs_feed(&z->loop, z->sendbuf, blk);                        /* resample = ce qui est envoyé au Loop */

        /* 5. FX sur le bus master — baie : VOIX/DRUMS -> FXIN/AUX1.
         * Si le pedalboard (menu FX) a au moins un slot actif : 4 chaînes ordonnées, 12 slots.
         * Sinon : chaîne fx.c globale historique (comportement d'avant, params CC 20..37). */
        if (ztek_bit_get(z->assign[ZTEK_PSRC_VOIX], ZTEK_PORT_FXIN) ||
            ztek_bit_get(z->assign[ZTEK_PSRC_VOIX], ZTEK_PORT_AUX1) ||
            ztek_bit_get(z->assign[ZTEK_PSRC_DRUMS], ZTEK_PORT_FXIN) ||
            ztek_bit_get(z->assign[ZTEK_PSRC_DRUMS], ZTEK_PORT_AUX1)) {
            int any = 0;
            for (int s2 = 0; s2 < ZTEK_NSLOT; s2++) if (z->fxslot[s2].type != FXT_OFF) any = 1;
            if (any) ztek_pedalboard(z, z->mixbufL, z->mixbufR, outL + done, outR + done, blk);
            else fx_process(z->mixbuf, outL + done, outR + done, blk);
        } else {
            for (uint32_t i = 0; i < blk; i++) { outL[done + i] = z->mixbufL[i]; outR[done + i] = z->mixbufR[i]; }
        }

        /* sirène -> MASTER (direct, post-FX) : siren_mix = part post-FX (0 = tout en pre-FX) */
        if (z->siren_on && ztek_bit_get(z->assign[ZTEK_PSRC_SIREN], ZTEK_PORT_MASTER)) {
            bool pre = ztek_bit_get(z->assign[ZTEK_PSRC_SIREN], ZTEK_PORT_FXIN) ||
                       ztek_bit_get(z->assign[ZTEK_PSRC_SIREN], ZTEK_PORT_AUX1);
            float post = 0.25f * z->siren_lvl * (pre ? z->siren_mix : 1.0f);
            for (uint32_t i = 0; i < blk; i++) {
                float sv = z->sirenbuf[i] * post;
                outL[done + i] += sv;
                outR[done + i] += sv;
            }
        }

        /* 4b. SMP page: one-shot playback (post-FX si SMP -> MASTER, pré-FX sinon géré plus haut) */
        if (z->smp_play && z->smp_len > 0 && !smp_prefx) {
            uint32_t s0 = (uint32_t)(z->smp_start * (float)z->smp_len);
            uint32_t s1 = (uint32_t)(z->smp_end * (float)z->smp_len);
            if (s1 <= s0) s1 = s0 + 1;
            if (s1 > z->smp_len) s1 = z->smp_len;
            /* ratio de lecture : PITCH ±24 demi-tons ; OLD = décimation x2 en plus */
            float semis = (z->smp_pitch - 0.5f) * 48.0f;
            float ratio = fm_exp2f(semis / 12.0f);
            if (z->smp_mode == 3) ratio *= 0.5f;
            uint32_t span = s1 - s0;
            for (uint32_t i = 0; i < blk; i++) {
                if (z->smp_delay > 0) { z->smp_delay--; continue; }
                uint32_t p = (uint32_t)z->smp_pos;
                if (p >= span) { z->smp_play = 0; break; }
                float fr = (float)p - fm_floorf((float)p);
                uint32_t p1 = p + 1 < span ? p + 1 : p;
                float x = z->smp[s0 + p] + (z->smp[s0 + p1] - z->smp[s0 + p]) * fr;
                x *= z->smp_vol * 0.9f;
                outL[done + i] += x;
                outR[done + i] += x;
                z->smp_pos += (double)ratio;
            }
        }

        done += blk;
    }

    /* 5. master + circuit bend (post-master, docs/ZULUTEK.md Lot D) */
    {
        uint32_t i;
        for (i = 0; i < n; i++) {
            float l = outL[i] * z->master;
            float r = outR[i] * z->master;
            l = fm_tanhf(l * 1.2f) * 0.85f;
            r = fm_tanhf(r * 1.2f) * 0.85f;
            if (z->bend_on) {
                z->bend_rng = z->bend_rng * 1103515245u + 12345u;
                if (z->bend_mode == 0) {
                    /* GLITCH : sample-hold aléatoire + bit-crush + trémolo (X = densité, Y = niveau) */
                    if ((float)(z->bend_rng >> 16) / 32768.0f >= z->bend_x * 0.5f)
                        z->bend_hold = l;          /* on met à jour l'échantillon retenu */
                    l = z->bend_hold;              /* sinon on rejoue le précédent (hold) */
                    float bits = 8.0f - z->bend_x * 6.0f;
                    if (bits < 1.0f) bits = 1.0f;
                    float lv = fm_exp2f(bits - 1.0f);
                    l = fm_floorf(l * lv + 0.5f) / lv;
                    z->bend_trem_ph += (0.02f + 0.2f * z->bend_x) * FM_TWO_PI / z->sr * 60.0f;
                    if (z->bend_trem_ph > FM_TWO_PI) z->bend_trem_ph -= FM_TWO_PI;
                    l *= 1.0f - z->bend_y * 0.8f * (0.5f + 0.5f * fm_sinf(z->bend_trem_ph));
                    r = l;
                } else {
                    /* LOW BAT : décimation + soft-clip dur */
                    z->bend_hold = l;
                    l = fm_tanhf(z->bend_hold * (1.5f + 3.0f * z->bend_y));
                    r = l;
                }
            }
            outL[i] = l;
            outR[i] = r;
            float pk = l < 0.0f ? -l : l;
            float pr = r < 0.0f ? -r : r;
            if (pr > pk) pk = pr;
            if (pk > z->master_peak) z->master_peak = pk;
        }
        z->master_peak *= 0.92f;    /* decay du VU entre deux blocs */
    }
}

