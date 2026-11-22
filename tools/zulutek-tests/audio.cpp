#include "ztek_engine.h"
#include <oboe/Oboe.h>
#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>
static constexpr int blockSize = 256;
static std::vector<float> render(ztek_t* engine, int blocks) {
    std::vector<float> signal;
    for (int block = 0; block < blocks; ++block) {
        float left[blockSize] = {}, right[blockSize] = {};
        ztek_render(engine, left, right, blockSize);
        for (int i = 0; i < blockSize; ++i) {
            assert(std::isfinite(left[i]) && std::isfinite(right[i]));
            signal.push_back(left[i]);
            signal.push_back(right[i]);
        }
    }
    return signal;
}
static double rms(const std::vector<float>& signal) {
    double energy = 0;
    for (float value : signal) energy += value * value;
    return std::sqrt(energy / signal.size());
}
static double difference(const std::vector<float>& dry, const std::vector<float>& wet) {
    std::vector<float> diff(dry.size());
    std::transform(dry.begin(), dry.end(), wet.begin(), diff.begin(), std::minus<float>());
    return rms(diff);
}
static void audible(const char* name, const std::vector<float>& signal) {
    double level = rms(signal);
    std::printf("%s RMS %.6f\n", name, level);
    assert(level > 0.0001);
}
int main() {
    std::setbuf(stdout, nullptr);
    auto fresh = [] { auto* engine = ztek_create(48000); assert(engine); ztek_drum_tr_set(engine, 0); return engine; };
    auto* z = fresh();
    oboe::failStart = true;
    assert(!ztek_start(z));
    assert(!ztek_start(z));
    oboe::failStart = false;
    assert(ztek_start(z));
    ztek_destroy(z);
    std::puts("Oboe start failure and retry (stub) PASS");
    for (int engine = 0; engine <= 4; ++engine) {
        z = fresh();
        ztek_set_engine(z, engine);
        ztek_note_on(z, 48, 100, 0, 0);
        double level = rms(render(z, 44));
        std::printf("Synth %d RMS %.6f\n", engine, level);
        assert(level > 0.0001);
        ztek_note_off(z, 48);
        render(z, 600);
        const auto tail = rms(render(z, 44));
        std::printf("Synth %d release RMS %.6f\n", engine, tail);
        assert(tail < level * .1);
        ztek_destroy(z);
    }
    z = fresh();
    ztek_acid_knob(z, 0, 1);
    ztek_acid_generate(z, 1234);
    ztek_acid_play(z, 1);
    audible("Acid sequence", render(z, 220));
    ztek_destroy(z);
    z = fresh();
    ztek_arp_set_on(z, 1);
    ztek_note_on(z, 53, 100, 0, 0);
    assert(ztek_arp_held(z) == 1);
    audible("Arpeggiator", render(z, 220));
    ztek_note_off(z, 53);
    assert(ztek_arp_held(z) == 0);
    ztek_destroy(z);
    z = fresh();
    ztek_drum_hit(z, 0);
    audible("Drum pad", render(z, 45));
    ztek_destroy(z);
    z = fresh();
    for (int step = 0; step < 16; ++step) ztek_drum_pat(z, 0, step, step % 4 == 0);
    ztek_drum_play(z, 1);
    ztek_drum_tr_set(z, 1);
    audible("Drum pattern", render(z, 220));
    ztek_destroy(z);
    z = fresh();
    ztek_loop_play(z, 1);
    ztek_loop_set_len(z, 0, 16);
    ztek_loop_rec_start(z, 0, 0);
    ztek_note_on(z, 48, 100, 0, 0);
    render(z, 15);
    ztek_note_off(z, 48);
    ztek_loop_rec_stop(z, 0);
    if (!ztek_loop_slot_on(z, 0)) ztek_loop_toggle(z, 0);
    render(z, 300);
    audible("Loop note playback", render(z, 220));
    ztek_destroy(z);
    z = fresh();
    ztek_mix_set_strip(z, ZTEK_STRIP_VOICE, ZTEK_MIX_AUX_LOOP, 1);
    ztek_loop_play(z, 1);
    ztek_loop_rs_start(z, 0, 1, 0, 32);
    ztek_note_on(z, 48, 100, 0, 0);
    render(z, 60);
    ztek_note_off(z, 48);
    ztek_loop_rs_stop(z);
    ztek_mix_set_strip(z, ZTEK_STRIP_VOICE, ZTEK_MIX_MUT, 1);
    if (!ztek_loop_slot_on(z, 0)) ztek_loop_toggle(z, 0);
    audible("Resampled playback (voice muted)", render(z, 220));
    ztek_destroy(z);
    z = fresh();
    ztek_siren(z, 1, .6, .4, .2);
    audible("Siren", render(z, 220));
    ztek_destroy(z);
    z = fresh();
    float input[256];
    std::fill_n(input, 256, .5f);
    input[0] = 0;
    ztek_smp_rec(z, 1);
    oboe::capture(input, 256);
    ztek_smp_rec(z, 0);
    assert(ztek_smp_len(z) == 256);
    ztek_smp_set(z, 3, .25f); // -12 semitones: the second frame is interpolated.
    ztek_smp_play(z, 1);
    auto sample = render(z, 1);
    assert(std::fabs(sample[0]) < .00001f && sample[2] > .01f);
    render(z, 10);
    ztek_loop_play(z, 1);
    ztek_smp_set(z, 4, .3f); // BEAT at 120 BPM and 48 kHz.
    ztek_smp_play(z, 1);
    assert(rms(render(z, 93)) < .00001);
    audible("SMP starts on beat", render(z, 3));
    ztek_destroy(z);
    auto voice = [&](bool effects, bool modulation) {
        z = fresh();
        if (effects) {
            ztek_fx_set(z, FX_DRIVE_TYPE, 3);
            ztek_fx_set(z, FX_DRIVE, .9);
        }
        if (modulation) {
            ztek_mod_knob(z, 0, 2, 1);
            ztek_assign(z, AT_CUTOFF, 0, 1);
        }
        ztek_note_on(z, 48, 100, 0, 0);
        auto output = render(z, 90);
        ztek_destroy(z);
        return output;
    };
    auto dry = voice(false, false);
    assert(difference(dry, voice(true, false)) > 0.0001);
    assert(difference(dry, voice(false, true)) > 0.0001);
    std::puts("Effects and LFO routing change rendered audio PASS");
    z = fresh();
    const int drive = AT_NPARAMS + FX_DRIVE;
    ztek_macro_set(z, 0, drive, 0, .8);
    ztek_macro_apply(z, 0);
    assert(std::fabs(ztek_fx_get(z, FX_DRIVE) - .8f) < .001);
    ztek_set_engine(z, 3);
    ztek_snap_store(z, 0);
    ztek_set_engine(z, 0);
    ztek_snap_load(z, 0);
    assert(ztek_get_engine(z) == 3);
    ztek_preset_save(z, 0, 0);
    uint8_t blob[4096];
    const int length = ztek_preset_export(z, 0, 0, blob, sizeof(blob));
    assert(length > 0);
    ztek_destroy(z);
    z = fresh();
    assert(ztek_preset_import(z, 0, 0, blob, length));
    ztek_preset_load(z, 0, 0);
    assert(ztek_get_engine(z) == 3);
    ztek_note_on(z, 48, 100, 0, 0);
    audible("Imported preset + SNAP and macro readback", render(z, 50));
    ztek_destroy(z);
}
