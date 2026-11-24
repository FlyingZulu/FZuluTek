#pragma once
#include <cstdint>
#include <memory>
namespace oboe {
enum class Result { OK, Error };
enum class Direction { Output, Input };
enum class PerformanceMode { LowLatency };
enum class SharingMode { Exclusive, Shared };
enum class AudioFormat { Float };
enum class DataCallbackResult { Continue };
inline bool failStart = false;
class AudioStream {
public:
    Result requestStart() { return failStart ? Result::Error : Result::OK; }
    void stop() {}
    void close() {}
};
class AudioStreamDataCallback {
public:
    virtual DataCallbackResult onAudioReady(AudioStream*, void*, int32_t) = 0;
};
inline AudioStreamDataCallback* inputCallback = nullptr;
inline AudioStream* inputStream = nullptr;
inline void capture(float* samples, int frames) {
    inputCallback->onAudioReady(inputStream, samples, frames);
}
class AudioStreamBuilder {
    Direction direction = Direction::Output;
    AudioStreamDataCallback* callback = nullptr;
public:
    AudioStreamBuilder* setDirection(Direction value) { direction = value; return this; }
    AudioStreamBuilder* setPerformanceMode(PerformanceMode) { return this; }
    AudioStreamBuilder* setSharingMode(SharingMode) { return this; }
    AudioStreamBuilder* setSampleRate(int) { return this; }
    AudioStreamBuilder* setChannelCount(int) { return this; }
    AudioStreamBuilder* setFormat(AudioFormat) { return this; }
    AudioStreamBuilder* setDataCallback(AudioStreamDataCallback* value) { callback = value; return this; }
    Result openStream(std::shared_ptr<AudioStream>& stream) {
        stream = std::make_shared<AudioStream>();
        if (direction == Direction::Input) { inputCallback = callback; inputStream = stream.get(); }
        return Result::OK;
    }
};
}
