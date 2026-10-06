# Haptics Lab

A modern Android haptics testing and impulse benchmarking lab.

## Features
- **Hardware Capability Detection**: Real-time probing of vibrator availability, amplitude control (Android 8.0+), and hardware composition primitives (`CLICK`, `THUD`, `TICK` on Android 11+).
- **Waveform & Pattern Generator**:
  - Discrete step pulses with editable duration and amplitude.
  - Continuous waveforms: Sine, Square (variable duty cycle), Sawtooth, Triangle, and Chirp frequency sweeps (5–60 Hz).
  - LFO frequency modulation (Ramp Up sweep & Sine LFO).
- **Interactive 2D Waveform Visualizer**:
  - Smooth animated playhead synchronized with hardware vibration playback.
  - Interactive pinch-to-zoom (up to 25x) and drag-to-pan across time axis.
  - Auto-scrolling playhead tracking and double-tap reset.
- **Impulse Benchmark Presets**:
  - 8 One-Shot tactile impulses (Double Tap, O-Haptics Click, Heartbeat Pulse, Heavy Thud, Soft Tick, Triple Tap Strum, Rising Knock, Success Chime).
  - 7 Looping rhythms (Arcade Rhythm, Rapid Staccato, Engine Idle, Siren Alert, Pulse Wave, Morse SOS, Breathing Ramp).
- **Open Source Licenses & View Source**: Full license attribution and repository access.

## License
Licensed under the [MIT License](LICENSE).
