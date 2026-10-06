# Haptics Research Report — for HapticsLab Improvement Agent

> Target app: **HapticsLab** — modern Android haptics testing + impulse benchmarking lab.
> Current features: Hardware Capability Detection (`hasVibrator`, `hasAmplitudeControl`, `CLICK/THUD/TICK` primitives), Waveform & Pattern Generator (discrete pulses, sine/square/sawtooth/triangle/chirp 5-60Hz, LFO Ramp/Sine), 2D visualizer with playhead + pinch-zoom 25x, 8 one-shot + 7 looping presets, OSS licenses.
> Goal: close gap to Apple Taptic Engine + recent flagship Android (Pixel / Samsung / OnePlus X-axis) and add rotary-motion coverage.

This report is synthesized from 5 parallel deep-research tracks: Android framework/AOSP, linear actuators/Taptic, rotary ERM, recent Android devices 2021-2026, patents/psychohaptics/UX. Feed verbatim to implementation agent.

---

## 1. Android Haptics Framework (API 1-36)

### 1.1 Evolution

| API | Year | What landed |
|---|---|---|
| 1 | 2008 | `Vibrator.vibrate(long)`, `vibrate(long[],int)`, `cancel()`, `hasVibrator()`. Requires `VIBRATE` normal permission. ERM on/off only. |
| 3 | 2009 | `HapticFeedbackConstants.LONG_PRESS, VIRTUAL_KEY, KEYBOARD_TAP`, `View.performHapticFeedback()`. No permission, honors view/system settings. |
| 26 Oreo | 2017 | `VibrationEffect.createOneShot()`, `createWaveform(timings)`, `createWaveform(timings,amplitudes)`, `DEFAULT_AMPLITUDE=-1`, `MAX=255`, `Vibrator.vibrate(VibrationEffect)`, `hasAmplitudeControl()`. Old `vibrate(long)` deprecated. |
| 28 Pie | 2018 | HAL `android.hardware.vibrator@1.0 Effect {CLICK,DOUBLE_CLICK,TICK,THUD,POP,HEAVY_CLICK,RINGTONE_1..15,TEXTURE_TICK}` + `EffectStrength {LIGHT,MEDIUM,STRONG}` via `perform()`. |
| 29 Q | 2019 | `VibrationEffect.createPredefined(EFFECT_CLICK,DOUBLE_CLICK,TICK,TEXTURE_TICK,HEAVY_CLICK,POP,THUD-hidden)`, `areEffectsSupported(), areAllEffectsSupported()` → `YES/NO/UNKNOWN`. `CONFIRM/REJECT`, `GESTURE_THRESHOLD_*`, `EDGE_SQUEEZE`. Predefineds always play (fallback waveform if `NO`). |
| 30 R | 2020 | `VibrationEffect.Composition/startComposition().addPrimitive(id,scale,delay).compose()`. Primitives: `NOOP=0,CLICK=1,THUD=2,SPIN=3,QUICK_RISE=4,SLOW_RISE=5,QUICK_FALL=6,TICK=7`. `arePrimitivesSupported(), getPrimitiveDurations()`. **No fallback — unsupported primitive = silent.** API 30 query coarse, accurate from 31. |
| 31 S | 2021 | `PRIMITIVE_LOW_TICK=8`, `VibratorManager (VIBRATOR_MANAGER_SERVICE)`, `CombinedVibration/CombinedAttributes`, `VibrationAttributes USAGE_TOUCH/PHYSICAL_EMULATION/ALARM/RINGTONE/MEDIA/COMMUNICATION`, `HapticGenerator (audio-coupled)`, `getQFactor(), getResonantFrequency()`, HAL AIDL `CAP_FREQUENCY_CONTROL, CAP_COMPOSE_PWLE, getFrequencyResolution/Minimum/BandwidthAmplitudeMap`. |
| 33 T | 2022 | `SEGMENT_TICK, SEGMENT_FREQUENT_TICK, DRAG_START=25, KEYBOARD_PRESS/RELEASE`, envelope builders hidden behind flag. |
| 34 U | 2023 | `TOGGLE_ON/OFF`, `Vibrator.getFrequencyProfile() (FOAM)`, HAL AIDL v2 `composePwleV2()`, `getFrequencyToOutputAccelerationMap()`. |
| 36 / 16 | 2025-26 | `VibrationEffect.Builder (.addPreset/.addEnvelope/.addEvents)` preferred over `Composition`, `BasicEnvelopeBuilder (intensity+sharpness, auto-fallback)`, `WaveformEnvelopeBuilder (amplitude+frequencyHz, no fallback)`, `Preset.PRESET_CLICK/TICK/LOW_TICK`, `areEnvelopeEffectsSupported(), getEnvelopeEffectInfo()`. `BasicEnvelope` must start/end at 0, min 20ms between points, >=16 points guaranteed. |

### 1.2 AOSP source locations

```
frameworks/base/core/java/android/os/Vibrator.java
frameworks/base/core/java/android/os/VibrationEffect.java # ~4000 lines: OneShot, Waveform, Prebaked, Composed, BasicPwleSegment, WaveformPwleSegment, Builder, Basic/WaveformEnvelopeBuilder, Preset
frameworks/base/core/java/android/os/VibratorManager.java
frameworks/base/core/java/android/os/VibrationAttributes.java
frameworks/base/core/java/android/os/CombinedVibration.java
frameworks/base/core/java/android/os/vibrator/VibratorEnvelopeEffectInfo.java
frameworks/base/core/java/android/view/HapticFeedbackConstants.java
frameworks/base/core/java/android/view/View.java # performHapticFeedback()
frameworks/base/core/java/android/os/HapticGenerator.java
frameworks/base/core/res/res/values/config.xml # config_virtualKeyVibePattern, longPress, keyboardTap, clockTick, etc.
frameworks/base/services/core/java/com/android/server/vibrator/VibratorManagerService.java
hardware/interfaces/vibrator/aidl/android/hardware/vibrator/IVibrator.aidl
hardware/interfaces/vibrator/aidl/android/hardware/vibrator/Effect.aidl
hardware/interfaces/vibrator/aidl/android/hardware/vibrator/CompositeEffect.aidl
hardware/interfaces/vibrator/aidl/android/hardware/vibrator/PrimitivePwle.aidl
device/google/.../vibrator/ # Pixel CS40L25/26 + FOAM calibration
```

Docs: `developer.android.com/develop/ui/views/haptics/haptics-apis`, `/custom-haptic-effects`, `/haptics-principles`, `source.android.com/docs/core/interaction/haptics/*`, `github.com/android/haptics-tools (pcm2pwle)`.

### 1.3 Primitives vs Predefined vs PWLE

**Primitives (short <20ms vs long):** `CLICK/TICK/LOW_TICK` short, scalable amplitude. `THUD(~300ms)/SPIN(~150ms)/QUICK_RISE(150ms)/SLOW_RISE(500ms)/QUICK_FALL(100ms)` long/ramp, scale envelope. `scale 0-1`, `delay` capped by `getCompositionDelayMax()`.

**Predefined:** `createPredefined()` safe without check. Log `areEffectsSupported()` for `YES=tuned / NO=fallback / UNKNOWN` badge.

**PWLE v1:** `(intensity,sharpness,duration)` via `pcm2pwle --freq_profile 50 136 174`. Deprecated.
**PWLE v2:** `(amplitude 0-1,frequencyHz,durationMs)` linear interp. Requires `CAP_FREQUENCY_CONTROL+FOAM`. `BasicEnvelope` = hardware-agnostic (use this), `WaveformEnvelope` = absolute Hz/Gs (validate `getFrequencyProfile()` range else silent).

Caps: `CAP_AMPLITUDE_CONTROL→hasAmplitudeControl()`, `CAP_FREQUENCY_CONTROL→hasFrequencyControl()/getFrequencyProfile()`, `CAP_COMPOSE_EFFECTS→primitives`, `CAP_COMPOSE_PWLE_EFFECTS_V2→envelopes`.

### 1.4 HapticFeedbackConstants full list

`NO_HAPTICS=-1, LONG_PRESS=0, VIRTUAL_KEY=1, KEYBOARD_TAP=3, CLOCK_TICK=4, CONTEXT_CLICK=6, KEYBOARD_PRESS/RELEASE, VIRTUAL_KEY_RELEASE, TEXT_HANDLE_MOVE, GESTURE_START/END, CONFIRM=16, REJECT=17, GESTURE_THRESHOLD_ACTIVATE/DEACTIVATE, DRAG_START=25, SEGMENT_TICK, SEGMENT_FREQUENT_TICK, TOGGLE_ON/OFF, EDGE_SQUEEZE/RELEASE, FLAG_IGNORE_GLOBAL_SETTING, FLAG_IGNORE_VIEW_SETTING`.

Rule: if action covered by constant, use `view.performHapticFeedback()` not raw `Vibrator`. Honors `hapticFeedbackEnabled` + `Settings.System.HAPTIC_FEEDBACK_ENABLED`. Throttle `SEGMENT_*/CLOCK_TICK` to quanta.

### 1.5 Permissions, background, latency

* `<uses-permission android:name="android.permission.VIBRATE"/>` for all `Vibrator/Manager` paths. Not needed for `performHapticFeedback`. Normal level.
* Background: only `USAGE_ALARM/RINGTONE/COMMUNICATION_REQUEST` allowed from background. `TOUCH/MEDIA` dropped.
* Battery Saver / `HAPTIC_FEEDBACK_ENABLED=0` suppresses touch. Always check.
* `vibrate()` = Binder IPC → never in UI tight loop. `cancel()` before retrigger. Repeating waveform runs until `cancel()` — cancel in `onPause/onDestroy`.
* Latency: LRA start ~10ms, stop braked ~10ms / unbraked 300ms ring; ERM start 20-50ms. Design clicks >=20ms apart, gaps >=80ms for ticks.

### 1.6 Kotlin templates (for HapticsLab player)

```kotlin
// One-shot + fallback
if (SDK>=26) vibrator.vibrate(VibrationEffect.createOneShot(ms, amp)) else vibrator.vibrate(ms)

// Waveform synth for visualizer
fun synthWave(freqHz:Double, seconds:Double, sampleMs:Long=10, kind:String): VibrationEffect {
  val n=(seconds*1000/sampleMs).toInt().coerceIn(2,500)
  val timings=LongArray(n){sampleMs}; timings[0]=0
  val amps=IntArray(n){ i ->
    val ph=2*Math.PI*freqHz*(i*sampleMs/1000.0); val s=when(kind){
      "square"->if(sin(ph)>=0)1.0 else 0.0
      "sawtooth"->(i.toDouble()/n*freqHz*seconds)%1.0
      "triangle"->2*abs(2*((i*sampleMs/1000.0*freqHz)%1.0)-1)-1
      "chirp"->sin(2*Math.PI*(freqHz+80*i.toDouble()/n)*(i*sampleMs/1000.0))
      else->sin(ph) }
    (abs(s)*255).toInt().coerceIn(0,255) }
  return VibrationEffect.createWaveform(timings,amps,-1)
}

// Composition with gating
if (SDK>=R && vibrator.areAllPrimitivesSupported(SPIN,TICK)) {
  vibrator.vibrate(VibrationEffect.startComposition()
    .addPrimitive(SLOW_RISE,0.5f).addPrimitive(QUICK_FALL,0.5f)
    .addPrimitive(TICK,1f,100).compose())
} else vibrator.vibrate(VibrationEffect.createPredefined(EFFECT_HEAVY_CLICK))

// API 36 envelopes
if (vibrator.areEnvelopeEffectsSupported()) {
  val env=VibrationEffect.BasicEnvelopeBuilder().setInitialSharpness(0f)
    .addControlPoint(1f,1f,500).addControlPoint(0f,1f,100).build()
  vibrator.vibrate(env)
}
// Manager multi-actuator
if (SDK>=S){ val mgr=getSystemService(VibratorManager::class.java)
  mgr.vibrate(CombinedVibration.createParallel(mgr.vibratorIds.associateWith{effect}),
    CombinedAttributes.Builder().setUsage(USAGE_MEDIA).build()) }
```

---

## 2. Linear Actuators + Apple Taptic Engine

### 2.1 Physics

`f0=1/(2π)*sqrt(k/m)`. Phone LRA `f0 150-235Hz`, Q 10-40 (4Hz off = collapse). Drive AC sine 0.1-2Vrms (X needs 5-10Vpp boost). DC does nothing.

* Natural rise 40-60ms, fall 150-300ms. With overdrive+braking (180° phase cancel via Back-EMF): 10/10ms.
* Back-EMF = velocity proxy → SmartLoop auto-resonance tracking (temp/aging/grip drift), auto brake, calibration.

|  | ERM | LRA X/Z | Piezo PowerHap |
|---|---|---|---|
| Drive | DC 1.5-5V | AC @f0 | 12-120V arbitrary |
| Range | 80-200Hz coupled | 150-235Hz narrow | 1-1000Hz flat |
| Rise/Fall | 50-100/100-200ms | 40-60/200-300 nat, 10/10 braked | <1.5ms/instant |
| Accel | 0.8-1.5G | 0.6-1.4Grms, 1.2-4.2Gpp transient | 3-35G pk |
| Power/click | 124mA | 51mA | 62mA but 0.34uAh |
| Noise | 50dB | 30dB X / 40dB Z | silent |
| Life | brushes wear | brushless | solid-state |
| Cost | $0.3-0.8 | $1.5-5 + $0.6-1.8 driver | $5-10 + HV |

### 2.2 X vs Z

**Z coin (Ø8-10mm):** vertical to screen, F0 170-235Hz, 0.6-0.86Grms, 1.2-2.1Gpp, BW 210-280Hz, noisy, poor palm coupling.
**X bar (8x15,10x16mm):** lateral, F0 130-170Hz, 1.0-1.4Grms, 1.85-4.2Gpp (CSA0916 4.2Gpp), BW 50-500Hz with RichTap, 1-5ms start/stop, 30dB, whole-body coherent, covers Pacinian 200Hz + Meissner 30-50Hz.

Parts: AAC `ELA0809→SLA0815→ESA1016(10x16,130Hz,3.69Gpp)→CSA0916(9x16.3,130Hz,4.2Gpp)→CyberEngine 50-500Hz`. Drivers: AW8697, DRV2605/2624, Cirrus.

### 2.3 Taptic Engine generations

* 2014 Watch + 2015 MacBook Force Touch (trackpad: 4x coils + strain gauges, no oscillator).
* 2015 6s: first iPhone Taptic, X linear mass, 10ms pulses, 3D Touch.
* 2016 7: larger (jack removed), solid-state Home, iOS10 `UIFeedbackGenerator`.
* 2017 X/8: refined gestures. 2019: Haptic Touch replaces 3D Touch, Core Haptics iOS13.
* 2021-23 13/14/15 Bongo solid-state buttons (cancelled 15 Pro). 2025 shock-resistant patent (non-linear spring diverts drop energy).

Design: in-house F0/tuning per product, flexures + ferrofluid (Taction lawsuit US10,659,885/10,820,117, $5.7B verdict 2026 under appeal), suppliers Luxshare/AAC build to spec.

**Core Haptics for parity:** `CHHapticEngine→Pattern(dict/events/AHAP)→Player`. `Transient` vs `Continuous` + `AudioCustom`. Params 0-1: `Intensity` (log), `Sharpness` (continuous: 80Hz@0→230Hz@1, peak ~160Hz@0.73; transient: low-pass brightness), `Attack/Decay/Release/Sustained`. Dynamic: `IntensityControl`, `SharpnessControl`, `ParameterCurve`. **AHAP JSON:** `Pattern:[{Event:{Type,Time,EventDuration,EventParameters:[{ParameterID,Value}]}}]`.

**HapticsLab takeaway:** abstract as Intensity+Sharpness not Hz/G. Map sharpness→freq on wideband, intensity→voltage log. Transient vs continuous distinction + curves + AHAP import.

---

## 3. Rotary / ERM Haptics

### 3.1 ERM law

`F0=m*r*ω²`, `f=RPM/60`. Ex: 10mm coin 14kRPM→233Hz, 0.5g*2mm→2.1N→2.1G on 100g phone.

Coupling: `f∝V`, `F∝V²` → cannot set independently. Low rumble always weak, strong always high pitch. <80Hz stalls, >300Hz overheats.

Latency: lag 10-30ms, rise 30-140ms, stop 20-100ms. Overdrive 3.6-5V cuts rise 30-50%, H-bridge reverse cuts stop to 10-25ms. Without: 100ms buzz = 50ms ramp+tail → muddy, no gap <80ms. Two-axis elliptical + brush harmonics 500Hz-4kHz → buzzy.

Coin vs bar: coin 8x3.4mm 0.6-1.3G 200-233Hz 60-90mA faster; bar 7x25mm 0.25-7G 92-230Hz slower, needs clearance. Both still ERM if DC. Coin≠LRA. PWM 20kHz+ via FET+diode, duty=intensity but coupled.

### 3.2 Why Android used ERM

BOM: ERM $0.3-0.8+ $0.05 FET vs LRA $1.5-3+$0.6-1.2 vs wideband $3-5+$1-1.8. 10M units = $20-50M delta. Timeline: 2007-14 all ERM, 2015 Taptic sets bar, 2017 Switch HD Rumble (Alps dual-resonance), 2018 Pixel3 Z-LRA + RichTap, 2019 OnePlus7Pro X-axis + Android Q predefineds, 2020 S20/Pixel4 wideband + Cirrus, 2021-22 all flagship LRA + Android12 Composition, 2023-25 mid follows, 2025 ERM only <$150.

### 3.3 Rotary HD (no spinning mass — emulation)

* **Switch HD Rumble:** Alps Haptic Reactor AFT 9x10x22.6mm, M-springs → 160Hz horiz + 320Hz vert, ~3G, <10ms, to 1kHz. Immersion TouchSense, dual-band `HF 10-1252Hz + LF 40-320Hz` log-encoded.
* **PS5 DualSense:** dual voice-coils (Foster/Alps), speaker without cone, flat 20Hz-1kHz, 5-10G transient, <5ms, 1-2W, plays PCM (sand/rain).
* **Hybrids:** dual-LRA orthogonal in quadrature `sin/cos` → circular force orbit (rotation illusion). Knobs: BLDC + encoder + `τ=K*I`, `τ(θ)=A*sin(Nθ)`.
* **Samsung/AAC:** circular 1006/1234 → X 0809/0916 → ESA 50-400Hz. RichTap full-stack 1-5ms.

### 3.4 Simulate rotary on LRA (for RotaryLab screen)

* Low sine 35-80Hz carrier (chug) + AM 4-12Hz (spin rate): `y=A*[1+m*sin(2πf_rot t)]*sin(2πf_carrier t)`, carrier near F0 for efficiency, `f_rot 4-6 churn, 8-12 engine, 15-25 grind`.
* Chirp spin-up 40→180Hz 150-400ms + brake CLICK, spin-down reverse.
* Dual-tone beat: `170+182Hz→12Hz beat` (HD style, pick both near F0).
* Knob: track finger angle, `TICK` per detent + `SPIN` background scaled by velocity.

| Feel | LRA | ERM fallback |
|---|---|---|
| Idle chug | 45Hz+9Hz AM loop | 120ms on/80ms off |
| Spin-up | 40→180Hz chirp+SPIN | duty 30→100% 300ms |
| Spin-down | 180→50Hz+brake CLICK | 100→0% + reverse |
| Wobble | 160Hz 5Hz 100% AM | 200/200ms |
| Detents | 12ms CLICK per 30° | 20ms blip gap 80ms |

Envelopes: LRA attack >=12ms, release 15-30ms; ERM attack >=40ms, gap >=80ms.

### 3.5 Drivers

* **DRV2605/L:** 2.5-5.5V, ERM+LRA, SmartLoop BEMF, 123 Immersion effects x6 libs (select by rise/stop), I2C+RTP/PWM/audio-to-vibe.
* **DRV2706:** 105V boost for piezo/big LRA.
* **Cirrus CS40L25/26/B:** 11Vpk, DSP, <5ms trigger→force, audio-to-haptics PCM, Pixel/Samsung standard.
* **AAC AW8697/RT6010/6612:** 11V, 1-5ms, 50-500Hz, RichTap content.

---

## 4. Recent Android Devices 2021-26

| Device | Motor | Driver | Note |
|---|---|---|---|
| Pixel 6/6Pro | wideband ~0815-1010 bottom-left | CS40L25 | first wideband |
| Pixel 7/7Pro | same footprint | CS40L26 (F0/Redc/Q cal, OWT, ALSA AoH) | keyboard + predictive-back ticks |
| Pixel 8/8Pro | same | CS40L26/B 11V 130MHz DSP | best keyboard |
| Pixel 9Pro/XL | same | CS40L26/B Tensor G4 | closest to iPhone |
| S22/Ultra | speaker-integrated linear bottom | CS35L40 amp + Samsung HAL | first Samsung wideband |
| S23U/S24U/S25U | same module refined, 7.2mm thin on S25 | Samsung mVibrator | strong but less crisp |
| OnePlus 10Pro/11 | AAC CSA0916 9x16.3 130Hz 1.4Grms 4.2Gpp | RichTap RT6010 | top-rated |
| OnePlus 12/13 | CSA0916/ESA1016 10x16 1.1Grms 3.69Gpp | RichTap | 600+ tunings |
| Xiaomi 13/14 | 0809/0815 170Hz → 1010 CyberEngine | AAC ELA/SLA/ESA | good not class-leading |
| Nothing 1/2/3 | X linear + Glyph 900 LEDs 5seg/12zone → 11seg/33zone → Matrix | DRV-class + Glyph SDK | 2 superb per GSMArena |
| ROG 7/8 | X + AirTriggers + Vibration Mapping | RichTap | best gaming |
| iQOO 11/12 | CSA/ESA + Monster Engine | AAC | near OnePlus |

Suppliers: AAC ESA/CSA/SLA/ELA, NFP ELV081530 8x15 1G, ELV0832B coin 205/235Hz, Jinlong LV101040A 10x10 170Hz 2.75G, Luxshare integrator.

Software: Samsung OneUI mVibrator (ball-bounce, shutter, directional sweep, TouchSense license), OnePlus O-Haptics Crisp/Gentle + RichTap SDK `.he`/JSON 50 lite + Unity/Unreal, Xiaomi HyperOS refined, Pixel Haptic Generator (F0 cal, PWLE, AoH 5ms wake, contextual scale), Nothing Glyph Composer sync light+audio+vibe.

Ecosystem: RichTap Creative Suite + Engine + SDK, Immersion TouchSense (license required, 100+ lib), Cirrus Studio + SVC, TI DRV2625/2605 fallback.

Benchmarks: **No DXOMARK haptics.** Lofelt 2021: smallest iPhone 11Pro > largest Android Xperia1, Apple F0 110-130Hz vs Android 160-300Hz. Hapticlabs 2025: gap narrowed, Android still 35% smaller avg, same dual-spring principle. iFixit: Apple volume+tuning vs Android under-invest software. Software Mansion 2026: iOS ~5ms predictable + fallback, Android ~50ms OEM-varying pre-16.

Android 13-16 for lab: 13 `KEYBOARD_PRESS/CLOCK_TICK/SEGMENT_TICK` + predictive-back hook + per-app keyboard; 14 `getResonantFrequency/getFrequencyProfile` + PWLE + `get(Uri ringtone)+USAGE_RINGTONE` + channel `setVibrationEffect`; 15 predictive-back enforced + channel rich + DND; 16 `Builder+Basic/WaveformEnvelope+areEnvelopeEffectsSupported` + predictive callbacks.

---

## 5. Patents, Psychohaptics, UX

### 5.1 Patents (technical, not legal)

* Apple: US8,666,133 (force/actuator), US9,818,484 (LRA drive/braking), US2014/0265650 (patterned drive), Core families US10,732,721/US11,068,098. Taction US10,659,885/US10,820,117 (flexures+ferrofluid 15-120Hz, $5.7B vs Apple 2026 appeal). Do not clone AHAP field names verbatim — converter layer.
* Immersion 2600+: US6,088,017 root, US7,639,232/US7,791,588/US8,098,234/US8,619,051 (vs Apple/Samsung), US8,581,710/US9,318,006/US10,466,791 touch surfaces, US2014/0167941 wideband off-resonance. **DRV2605 123 ROM cannot be redistributed — trigger by ID, label playback-only.**
* AAC RichTap: wideband spring/magnetic + driver, JSON interchange (~`{effects:[{type,freq,envelope,duration}],audioSync}`). Support import/export per `richtap-haptics.com/community/doc`.
* Samsung KR10-2184288 (input+haptic), case vs screen-mounted 175-185Hz, 2025 personalization.
* Google: 2012 Motorola settle, LRA-as-sensor back-EMF buttons, EP2023 force-scaled waveforms.
* TI DRV2605/2700/8662 (ERM/LRA/piezo to 200V), Adafruit breakout ref.
* Meta/Ultraleap: 16x16 40kHz array AM vs STM, Tasbi wrist squeeze+vibro CHI22, gloves microfluidic, Haptics Studio `github.com/facebook/haptics-studio`.

Links: `patents.google.com/patent/US...`, `research.google/blog/haptics-with-input...`, `docs.ultraleap.com/haptics`, `ti.com/product/DRV2605`.

### 5.2 UX guidelines

Android (`developer.android.com/.../haptics-principles`): **Clear** (crisp <60ms: VIRTUAL_KEY, TICK, CLICK) for buttons/toggles/ticks, **Rich** (composed SPIN/THUD/RISE) for success/error/drag/game, **Buzzy** (>500ms monotonic) only alarms/calls. Prefer `performHapticFeedback()` (device-optimized + respects setting). Importance↔strength, frequent=subtle. Fallback to nothing not buzzy. No surprise. Intensity slider + off. Audio+haptics match rhythm not melody.

Apple HIG Playing Haptics: `UIImpact light/medium/heavy/soft/rigid` collision/snap, `UISelection` micro-tick picker/slider, `UINotification success(rising)/warning(flat)/error(descending)` outcome only. Build without haptics first, `prepare()` just before, complement not duplicate.

Combined table for preset labels: keyboard `KEYBOARD_TAP/TICK 10-20ms` = iOS Selection, toggle `TOGGLE_ON/CLICK` = Impact light, long-press `LONG_PRESS/HEAVY 30-60ms`, drag snap `SEGMENT_TICK throttled 80-150ms`, success `QUICK_RISE+CLICK 100-250ms rising`, warning flat double, error `THUD descending`, call continuous loop stoppable, collision velocity-scaled `pow(v,0.65)`, scrub `TEXT_HANDLE_MOVE`.

A11y/battery: distinct rhythms (blind), haptics as audio substitute (deaf, independent toggle), Off/Reduced (strip rich keep clear), never every frame, release engine, cap loops, log mA.

### 5.3 Psychohaptics

Receptors: Merkel 0-10Hz pressure, Meissner 10-50Hz flutter, **Pacinian 40-500Hz peak 200-250Hz fingertip /160Hz wrist** main HD, Ruffini stretch.

Thresholds wrist: 25Hz 20.7um, 40Hz 8.7um, 80Hz 2.3um, 160Hz 0.7um, 250Hz 1.6um, 320Hz 2.2um, 640Hz 5.4um. Design 150-300Hz least power.

JND: freq 15-30% (use 20% rule 180→220Hz), amplitude 10-20% (Stevens γ0.65), duration -100ms noticed / +100-500ms needed. Click <20ms tick, 20-60ms click (Taptic 30-50ms+10ms brake), 60-150ms thud, >200ms buzz needs envelope, >1000ms non-haptic (`MAX_HAPTIC_FEEDBACK_DURATION`).

Masking: strong masker raises threshold 50-200ms after, same-freq strongest. Throttle ticks >=80ms. Adaptation >2s raises threshold, cold skin worse.

### 5.4 Open libs

Lofelt Nice Vibrations `github.com/Lofelt/NiceVibrations` (80+ clips pattern ref, Studio sunset Meta 2022), Meta Haptics Studio (audio→haptics `.haptic`), bHaptics `github.com/bhaptics/haptic-guide` (`.tact` game presets), Adafruit DRV2605, AOSP `VibrationEffect.java SCALE_GAMMA=0.65`. No stable Haptics4Android — implement thin `HapticsPlayer` wrapper.

---

## 6. Consolidated Improvement Plan for HapticsLab Agent

**A. Capability probe (extend current detection):**
Show `hasVibrator/hasAmplitudeControl/areEnvelopeEffectsSupported/getFrequencyProfile(fmin/max)/getQFactor/getResonantFrequency/arePrimitivesSupported[1..8]/areEffectsSupported[*]/getPrimitiveDurations/getEnvelopeEffectInfo(min20ms,maxSize,maxDuration)` + `MANUFACTURER/MODEL/SDK`. Cache startup. Badge PWLE→primitives→predefined→one-shot→HFC→nothing chain. Branch: `!hasAmplitudeControl` quantize to 0/255 + warn.

**B. Waveform generator upgrades:**
Keep sine/square/duty/saw/triangle/chirp 5-60Hz + LFO, add `carrier 30-300Hz sweep 60→300Hz 500ms resonance finder + F0 marker persisted`, `AM depth/rate 2-20Hz`, `chirp spin-up/down time`, `dual-tone beat Δ5-15Hz`, `overdrive 5-10ms boost + 180° brake toggle`, `ERM-ify toggle (50ms slew + couple f/A)`. Emit `createWaveform` (API26-35), `BasicEnvelope` (36), PCM `HapticGenerator` for beats.

**C. Presets (22, grouped Clear/Rich/Buzzy + Needs LRA + API):**
Diagnostics: Tick12ms, Click30ms, Heavy60ms, Double-click, Buzz400ms reference. Taptic-like: Tap35ms@180Hz+brake, iOS Light 20ms, Keyboard 15ms x5 @120ms, Snap25ms brake A/B. Rich: Success Rise, Warning Flat 2x60ms, Error Fall THUD, Spin, Texture 8x @100ms velocity slider, Collision velocity `pow(v,0.65)`, Heartbeat lub-dub 180ms. Wideband: Sweep, Envelope swell (WHC Pixel example), Music→haptics, ERM ramp 0-100% 80ms+80ms fall, Off-resonance 120 vs 200Hz test. Existing 8+7 map into this; add velocity slider, throttle demo 40 vs 120ms masking.

**D. Visualizer:**
Plot `amplitudes[] vs cum timings[]` + freq overlay + Stevens-corrected intensity + est G (ERM0.7, LRA0.8-1.5pk, piezo3-13) + rise/fall + battery bar (green<100ms amber<400ms red>=1s) + JND snap (amber if Δf<20%/ΔA<15%/Δt<100ms) + accelerometer trace if `TYPE_ACCELEROMETER_UNCALIBRATED` + playhead sync already present + pinch/pan/double-tap keep.

**E. RotaryLab new screen:**
Sliders carrier/rate/depth/chirp, 6 rotary recipes, quadrature dial view (TICK per 30° + SPIN background), ERM fallback column.

**F. UX/a11y/safety:**
Global intensity + Off/Reduced/Full (Reduced strips rich), per-preset importance label, audio+haptics sync toggle + ±50ms calibrator, `HAPTIC_FEEDBACK_ENABLED` + DND respect, haptic+visual flash pairing, cap 1000ms soft /5s hard + Alarm-mode confirm + foreground notif, cancel onPause, rest reminder 50 plays, no skin-taped looping warning, ISO5349 footer.

**G. Share/interop:**
Canonical JSON v1 `{name,version,class,actuator,segments:[{t_ms,dur_ms,type,freq_hz,amp,sharpness/phase}],audioSync_ms,meta:{F0,est_G}}` single truth → converters to AHAP (CHHaptic dict), RichTap/O-Haptics JSON, Kotlin snippet copy (`createPredefined/Composition/Envelope.Builder`), QR <2KB + FileProvider, schema validation, DRV2605 ID crosswalk playback-only, patent/trademark footer.

**H. Device DB + QA:**
Model→actuator ERM/Z/X/piezo,F0,G crowdsourced opt-in, play history CSV, automated test iterate presets assert capabilities, tap-to-buzz latency ms via accelerometer, onboarding ERM vs LRA vs Taptic 30s bars (rise ERM50/LRA10/piezo0.3ms, stop ERM50-100/LRA10 braked else300).

**I. Tech debt:**
Vibrate off main (`Dispatchers.Default`), `cancel()` before replay, pre-resolve primitives once cache effect, `VibrationAttributes.USAGE_TOUCH` foreground / `USAGE_RINGTONE` for Glyph-style sync demo + predictive-back tick demo (Android13-16 showcase), per-device profiles Pixel(F0 130-150 PWLE) / OnePlus CSA130 strongest / Samsung soften low / Xiaomi170 cap.

---

### Key links for implementation agent
`developer.android.com/reference/android/os/VibrationEffect`, `/Vibrator`, `/VibratorManager`, `/VibrationAttributes`, `/view/HapticFeedbackConstants`, `/develop/ui/views/haptics/*`, `source.android.com/docs/core/interaction/haptics/*`, `cs.android.com`, `github.com/android/haptics-tools`, `richtap-haptics.com/en/community/doc`, `github.com/richtap-haptics`, `cirrus.com/products/cs40l26-26b`, `ti.com/product/DRV2605`, `developer.apple.com/design/human-interface-guidelines/playing-haptics`, `developer.apple.com/documentation/corehaptics`, `hapticlabs.io/showcase/haptics-in-our-smartphones`, `medium.com/lofelt/an-evaluation...`, `swmansion.com/blog/what-is-the-difference-between-i-os-and-android-haptics`.
