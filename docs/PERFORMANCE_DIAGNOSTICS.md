# STT Performance Diagnostics Guide

## Overview

The app now collects detailed performance metrics during audio recording and speech-to-text processing. These metrics help identify device-specific issues that affect transcription accuracy.

## How to Use

1. **Start a recording session** on your device
2. **Speak for at least 30-60 seconds** to collect meaningful data
3. **Stop the recording**
4. **Check logcat** for the diagnostic report (filter by tag: `AudioMetrics`)

## Reading the Report

The diagnostic report is automatically printed to logcat when you stop recording. Here's what each section means:

### 📱 DEVICE INFO
```
Manufacturer: OnePlus
Model: CPH2671
Android: 35
CPU Cores: 8
CPU Arch: arm64-v8a
```

**What to look for:**
- Device model and Android version
- Number of CPU cores (affects threading strategy)
- CPU architecture (arm64 is standard for modern devices)

---

### 🎙️ AUDIO CAPTURE
```
Total Reads: 523
Failed Reads: 0
Total Samples: 836800 (52.3s)
Read Latency: min=8ms avg=12.4ms max=45ms
⚠️ WARNING: Audio read issues detected!
```

**What to look for:**
- **Failed Reads > 0**: Audio recording is failing intermittently
- **Max Read Latency > 150ms**: Audio reads are blocking too long (potential buffer underrun)
- **Avg Read Latency > 50ms**: Consistently slow audio capture (device issue)

**Good values:**
- Failed Reads: 0
- Avg Read Latency: 10-30ms
- Max Read Latency: < 100ms

**Problematic values:**
- Failed Reads > 0 → Audio driver/hardware issue
- Avg Read Latency > 50ms → Device is too slow or busy
- Max Read Latency > 150ms → CPU scheduling issues (big.LITTLE)

---

### 📦 BUFFER HEALTH
```
Channel Max Size: 15 chunks
Channel Timeouts: 0
⚠️ WARNING: Buffer backlog detected - processing too slow!
```

**What to look for:**
- **Max Channel Size > 10**: Processing can't keep up with audio capture
- **Channel Timeouts > 0**: Audio samples being dropped

**Good values:**
- Max Channel Size: 1-5 chunks
- Channel Timeouts: 0

**Problematic values:**
- Max Channel Size > 10 → Inference too slow, audio being buffered
- Channel Timeouts > 0 → Audio loss (degraded accuracy)

---

### 🔍 VAD PROCESSING
```
Windows Processed: 1024
VAD Failures: 0
Speech Segments: 12
```

**What to look for:**
- **VAD Failures > 0**: Voice Activity Detection is crashing
- **Speech Segments**: Number of times speech was detected (should correlate with how much you spoke)

**Good values:**
- VAD Failures: 0
- Windows Processed: ~(recording_seconds * 31) → 1 window every 32ms

**Problematic values:**
- VAD Failures > 0 → Native library issue or corrupted audio
- Windows Processed much lower than expected → Audio capture failing silently

---

### 🧠 INFERENCE
```
Total Calls: 245
Success: 243
Failures: 2
Inference Time: min=45ms avg=87.3ms max=340ms
⚠️ WARNING: Inference performance issues detected!
```

**What to look for:**
- **Failures > 0**: ASR inference is crashing
- **Avg Inference Time > 150ms**: Model processing is too slow
- **Max Inference Time > 500ms**: CPU throttling or thread starvation

**Good values:**
- Failures: 0
- Avg Inference Time: 50-150ms
- Max Inference Time: < 300ms

**Problematic values:**
- Failures > 0 → Model corruption or memory issues
- Avg Inference Time > 200ms → Device too slow, running on little cores
- Max Inference Time > 500ms → Thermal throttling or thread scheduling issues

---

### 🧵 THREAD INFO
```
Recording Thread: DefaultDispatcher-worker-2 (ID: 12345)
Processing Thread: DefaultDispatcher-worker-3 (ID: 12346)
```

**What this tells you:**
- Both threads are using `DefaultDispatcher` which means they share CPU time
- Thread IDs can help identify if threads are being recreated (shouldn't happen)
- If recording and processing threads have very different IDs, it suggests high thread pool contention

---

### 📊 HEALTH ASSESSMENT
```
⚠️ Issues detected in: Audio capture, Inference performance
```

**Quick summary of all detected issues:**
- **No issues detected** = Everything is working well
- **Audio capture** = Failed reads or high latency
- **Buffer backlog** = Processing too slow
- **Inference performance** = Slow or failing ASR
- **VAD failures** = Voice detection crashing

---

## Common Issues and Causes

### Issue 1: Buffer Backlog + Slow Inference
**Symptoms:**
- Max Channel Size > 10
- Avg Inference Time > 150ms

**Likely Cause:**
- Threads running on efficiency cores (little cores)
- Too few threads allocated (numThreads = 2 might be insufficient)

**Solution:**
- Increase `numThreads` based on device CPU count
- Add thread affinity to pin inference to big cores

---

### Issue 2: High Audio Read Latency
**Symptoms:**
- Max Read Latency > 150ms
- Sporadic failed reads

**Likely Cause:**
- Android scheduler putting recording thread on slow core
- Device under heavy load
- Audio driver issues on newer devices

**Solution:**
- Increase audio buffer size (from `numBytes * 2` to `numBytes * 4`)
- Try using SCHED_FIFO priority for recording thread
- Switch audio source from MIC to VOICE_RECOGNITION

---

### Issue 3: Inference Spikes (High Max, Normal Avg)
**Symptoms:**
- Avg Inference Time: 80ms (good)
- Max Inference Time: 450ms (bad)

**Likely Cause:**
- Thread switching between big and little cores mid-inference
- Thermal throttling kicking in
- Background processes competing for CPU

**Solution:**
- Pin inference threads to specific big cores
- Reduce thread count to prevent over-subscription
- Monitor device temperature

---

### Issue 4: Audio Sample Loss
**Symptoms:**
- Audio drop rate > 1%
- Total Samples much less than expected

**Likely Cause:**
- Audio buffer too small for device latency
- Recording thread not getting scheduled in time
- Channel send blocking (shouldn't happen with UNLIMITED)

**Solution:**
- Increase audio buffer multiplier
- Increase recording interval from 100ms to 150ms
- Use SCHED_FIFO for recording thread

---

## Comparing Devices

When testing on multiple devices, compare these key metrics:

| Metric | OnePlus 8 Pro (Good) | OnePlus 15R (Poor) | Issue |
|--------|---------------------|-------------------|-------|
| Avg Read Latency | 15ms | 35ms | 15R slower audio capture |
| Max Channel Size | 3 | 18 | 15R can't keep up with processing |
| Avg Inference | 95ms | 195ms | 15R inference 2x slower |
| Max Inference | 180ms | 520ms | 15R has massive spikes |

**What this tells you:**
- OnePlus 15R has slower inference despite newer CPU (thread scheduling issue)
- Large channel backlog means audio samples are waiting too long
- High inference spikes suggest core switching or thermal throttling

---

## Advanced Analysis

### CPU Core Detection (in logs)

Look for this pattern in the full device logs:
```
processor : 0
processor : 1
CPU part : 0xd0d (Cortex-A510 - little)
...
processor : 4
CPU part : 0xd44 (Cortex-X1 - big)
```

This helps identify the big.LITTLE configuration. Modern Snapdragon chips have:
- **Snapdragon 865** (OnePlus 8 Pro): 1x A77 (2.84GHz) + 3x A77 (2.4GHz) + 4x A55 (1.8GHz)
- **Snapdragon 8 Elite** (OnePlus 15R): 2x Oryon (4.32GHz) + 6x Oryon (3.53GHz) - no little cores!

### Why Newer Devices Can Be Worse

1. **More aggressive noise cancellation** in HAL audio layer
2. **More complex scheduler** with 8+ heterogeneous cores
3. **Higher thermal throttling** (more powerful = more heat)
4. **Different ONNX Runtime optimization** for newer instruction sets

---

## Next Steps

Once you've identified issues from the metrics:

1. **For audio capture issues** → Try different AudioSource, increase buffer
2. **For inference issues** → Adjust numThreads, add thread affinity
3. **For buffer backlog** → Optimize processing interval, reduce overhead
4. **For device-specific issues** → Add device-specific configurations

See the code in:
- `AudioMetrics.kt` - Metrics collection
- `SherpaOnnxDataSource.kt` - Recording pipeline
- `AppModule.kt` - STT configuration
