package com.asdk.tools.vibrationlab

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.asdk.tools.vibrationlab.databinding.ActivityVibrationTestBinding
import com.asdk.tools.vibrationlab.databinding.ItemVibrationPointBinding
import com.asdk.tools.vibrationlab.databinding.ItemVibrationPresetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import de.psdev.licensesdialog.LicensesDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), PresetHost {

    private lateinit var binding: ActivityVibrationTestBinding
    override var vibrator: Vibrator? = null
        private set
    override var hasAmplitude: Boolean = false
        private set
    override var hasPrimitives: Boolean = false
        private set

    private var activePlaybackJob: Job? = null
    private var isPlaying: Boolean = false
    private var isLoopingMode: Boolean = false
    private var activePresetTitleRes: Int? = null
    private val presetBindings = mutableListOf<Pair<PresetItem, ItemVibrationPresetBinding>>()

    private var currentMode: WaveformMode = WaveformMode.DISCRETE
    private val discretePoints = mutableListOf<EditablePoint>()

    private var isLfoEnabled: Boolean = false
    private var lfoShape: LfoShape = LfoShape.RAMP_UP
    private var currentActiveWaveform: GeneratedWaveform? = null
    private var lastHapticDispatchMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingHapticDispatch: Runnable? = null
    private val scheduledTasks = mutableListOf<Runnable>()
    private var lastPlaybackRunner: ((looping: Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVibrationTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdgeWithPadding(binding.layoutMainContent)

        setSupportActionBar(binding.toolbar)

        setupBottomSheet()
        setupHardwareCapabilities()
        initDefaultDiscretePoints()
        wireModeSelector()
        wireContinuousSliders()
        wireLfoControls()
        wireDiscreteControls()
        wirePlaybackButtons()
        wireSinglePulse()
        wirePresets()

        setPlaybackUiState(playing = false, looping = false)
        updateWaveformGraphAndPreview()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_diagnostics -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Hardware Diagnostics")
                    .setMessage(Haptics.getHardwareDiagnostics(this))
                    .setPositiveButton(R.string.about_dialog_ok, null)
                    .show()
                true
            }
            R.id.menu_view_source -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/theonlyasdk/android-haptics-lab"))
                startActivity(intent)
                true
            }
            R.id.menu_licenses -> {
                LicensesDialog.Builder(this)
                    .setNotices(R.raw.notices)
                    .setIncludeOwnLicense(true)
                    .setTitle(R.string.menu_licenses)
                    .build()
                    .show()
                true
            }
            R.id.menu_about -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.about_dialog_title)
                    .setMessage(R.string.about_dialog_message)
                    .setPositiveButton(R.string.about_dialog_ok, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupBottomSheet() {
        val bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheetVisualizer)
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        fun updateScrollContentPadding(sheet: View) {
            val rootH = binding.root.height
            if (rootH <= 0) return
            val coveredH = (rootH - sheet.top).coerceAtLeast(0)
            val basePadding = 16.dp()
            binding.containerScrollContent.setPadding(
                binding.containerScrollContent.paddingLeft,
                binding.containerScrollContent.paddingTop,
                binding.containerScrollContent.paddingRight,
                coveredH + basePadding
            )
        }

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                updateScrollContentPadding(bottomSheet)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                updateScrollContentPadding(bottomSheet)
            }
        })
    }

    private fun setupHardwareCapabilities() {
        vibrator = Haptics.vibrator(this)
        val present = Haptics.hasVibrator(vibrator)
        hasAmplitude = Haptics.supportsAmplitude(vibrator)
        hasPrimitives = Haptics.supportsPrimitives(vibrator)

        val colorPrimary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary, Color.parseColor("#00BCD4"))
        val colorError = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorError, Color.parseColor("#BA1A1A"))

        binding.textMotorStatus.text = getString(
            if (present) R.string.vibration_status_available else R.string.vibration_status_unavailable
        )
        binding.iconMotorStatus.setImageResource(
            if (present) R.drawable.ic_check_circle_rounded else R.drawable.ic_cancel_circle_rounded
        )
        binding.iconMotorStatus.imageTintList = ColorStateList.valueOf(if (present) colorPrimary else colorError)

        binding.textAmplitudeStatus.text = getString(
            if (hasAmplitude) R.string.vibration_supported else R.string.vibration_unsupported
        )
        binding.iconAmplitudeStatus.setImageResource(
            if (hasAmplitude) R.drawable.ic_check_circle_rounded else R.drawable.ic_cancel_circle_rounded
        )
        binding.iconAmplitudeStatus.imageTintList = ColorStateList.valueOf(if (hasAmplitude) colorPrimary else colorError)

        binding.textPrimitivesStatus.text = getString(
            if (hasPrimitives) R.string.vibration_supported else R.string.vibration_unsupported
        )
        binding.iconPrimitivesStatus.setImageResource(
            if (hasPrimitives) R.drawable.ic_check_circle_rounded else R.drawable.ic_cancel_circle_rounded
        )
        binding.iconPrimitivesStatus.imageTintList = ColorStateList.valueOf(if (hasPrimitives) colorPrimary else colorError)

        if (!hasAmplitude) {
            binding.sliderSingleAmplitude.isEnabled = false
            binding.labelSingleAmplitude.setText(R.string.vibration_amplitude_off)
            binding.sliderWaveAmp.isEnabled = false
            binding.labelWaveAmp.setText(R.string.vibration_amplitude_off)
        }
    }

    private fun initDefaultDiscretePoints() {
        discretePoints.clear()
        discretePoints += EditablePoint(80L, 220)
        discretePoints += EditablePoint(80L, 200)
        discretePoints += EditablePoint(120L, 255)
    }

    private fun wireModeSelector() {
        binding.toggleWaveformMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentMode = when (checkedId) {
                    R.id.btn_mode_sine -> WaveformMode.SINE
                    R.id.btn_mode_square -> WaveformMode.SQUARE
                    R.id.btn_mode_sawtooth -> WaveformMode.SAWTOOTH
                    R.id.btn_mode_triangle -> WaveformMode.TRIANGLE
                    R.id.btn_mode_chirp -> WaveformMode.CHIRP
                    else -> WaveformMode.DISCRETE
                }

                binding.layoutDiscreteMode.isVisible = currentMode == WaveformMode.DISCRETE
                binding.layoutContinuousMode.isVisible = currentMode != WaveformMode.DISCRETE
                binding.layoutDutyCycle.isVisible = currentMode == WaveformMode.SQUARE
                binding.layoutChirpEnd.isVisible = currentMode == WaveformMode.CHIRP

                updateWaveformGraphAndPreview()
            }
        }
    }

    private fun formatDuration(durationMs: Float): String {
        return if (durationMs >= 1000f) {
            getString(R.string.vibration_total_duration_sec, durationMs / 1000f)
        } else {
            getString(R.string.vibration_total_duration, durationMs.toInt())
        }
    }

    private fun wireContinuousSliders() {
        binding.sliderWaveAmp.addOnChangeListener { _, value, _ ->
            if (hasAmplitude) {
                binding.labelWaveAmp.text = getString(R.string.vibration_amplitude, value.toInt())
            }
            updateWaveformGraphAndPreview()
        }
        if (hasAmplitude) {
            binding.labelWaveAmp.text = getString(R.string.vibration_amplitude, binding.sliderWaveAmp.value.toInt())
        }

        binding.sliderWaveFreq.addOnChangeListener { _, value, _ ->
            binding.labelWaveFreq.text = getString(R.string.vibration_frequency, value.toInt())
            updateWaveformGraphAndPreview()
        }
        binding.labelWaveFreq.text = getString(R.string.vibration_frequency, binding.sliderWaveFreq.value.toInt())

        binding.sliderWaveDuration.addOnChangeListener { _, value, _ ->
            binding.labelWaveDuration.text = formatDuration(value)
            updateWaveformGraphAndPreview()
        }
        binding.labelWaveDuration.text = formatDuration(binding.sliderWaveDuration.value)

        binding.sliderWaveDuty.addOnChangeListener { _, value, _ ->
            binding.labelWaveDuty.text = getString(R.string.vibration_duty_cycle, value.toInt())
            updateWaveformGraphAndPreview()
        }
        binding.labelWaveDuty.text = getString(R.string.vibration_duty_cycle, binding.sliderWaveDuty.value.toInt())

        binding.sliderChirpEnd.addOnChangeListener { _, value, _ ->
            binding.labelChirpEnd.text = getString(R.string.vibration_chirp_end_freq, value.toInt())
            updateWaveformGraphAndPreview()
        }
        binding.labelChirpEnd.text = getString(R.string.vibration_chirp_end_freq, binding.sliderChirpEnd.value.toInt())
    }

    private fun wireLfoControls() {
        binding.switchLfo.setOnCheckedChangeListener { _, isChecked ->
            isLfoEnabled = isChecked
            binding.layoutLfoControls.isVisible = isChecked
            updateWaveformGraphAndPreview()
        }

        binding.toggleLfoShape.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                lfoShape = if (checkedId == R.id.btn_lfo_sine) LfoShape.SINE else LfoShape.RAMP_UP
                updateWaveformGraphAndPreview()
            }
        }

        binding.sliderLfoDepth.addOnChangeListener { _, value, _ ->
            binding.labelLfoDepth.text = getString(R.string.vibration_lfo_depth, value.toInt())
            updateWaveformGraphAndPreview()
        }
        binding.labelLfoDepth.text = getString(R.string.vibration_lfo_depth, binding.sliderLfoDepth.value.toInt())

        binding.sliderLfoRate.addOnChangeListener { _, value, _ ->
            binding.labelLfoRate.text = getString(R.string.vibration_lfo_rate, value)
            updateWaveformGraphAndPreview()
        }
        binding.labelLfoRate.text = getString(R.string.vibration_lfo_rate, binding.sliderLfoRate.value)
    }

    private fun createPointTransition(): TransitionSet = TransitionSet().apply {
        ordering = TransitionSet.ORDERING_TOGETHER
        addTransition(Fade())
        addTransition(ChangeBounds())
        duration = 200L
    }

    private fun wireDiscreteControls() {
        binding.btnAddPoint.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.containerPoints, createPointTransition())
            addDiscretePoint(EditablePoint(100L, 200))
        }
        renderDiscretePointViews()
    }

    private fun addDiscretePoint(point: EditablePoint) {
        val index = discretePoints.size
        discretePoints += point
        val itemBinding = ItemVibrationPointBinding.inflate(layoutInflater, binding.containerPoints, false)
        val view = itemBinding.root

        itemBinding.textPointTitle.text = getString(R.string.vibration_step_title, index + 1)
        itemBinding.labelPointDuration.text = getString(R.string.vibration_duration, point.durationMs.toInt())
        itemBinding.sliderPointDuration.value = point.durationMs.toFloat().coerceIn(10f, 1000f)
        itemBinding.sliderPointDuration.addOnChangeListener { _, value, _ ->
            point.durationMs = value.toLong()
            itemBinding.labelPointDuration.text = getString(R.string.vibration_duration, value.toInt())
            updateWaveformGraphAndPreview()
        }

        if (hasAmplitude) {
            itemBinding.labelPointAmplitude.text = getString(R.string.vibration_amplitude, point.amplitude)
            itemBinding.sliderPointAmplitude.value = point.amplitude.toFloat().coerceIn(0f, 255f)
            itemBinding.sliderPointAmplitude.addOnChangeListener { _, value, _ ->
                point.amplitude = value.toInt()
                itemBinding.labelPointAmplitude.text = getString(R.string.vibration_amplitude, value.toInt())
                updateWaveformGraphAndPreview()
            }
        } else {
            itemBinding.sliderPointAmplitude.isEnabled = false
            itemBinding.labelPointAmplitude.setText(R.string.vibration_amplitude_off)
        }

        itemBinding.btnDeletePoint.setOnClickListener {
            val currentIndex = binding.containerPoints.indexOfChild(view)
            if (currentIndex != -1) {
                removeDiscretePoint(currentIndex)
            }
        }

        binding.containerPoints.addView(view)
        updatePointIndices()
        updateWaveformGraphAndPreview()
    }

    private fun removeDiscretePoint(index: Int) {
        if (discretePoints.size <= 1 || index !in discretePoints.indices) return
        TransitionManager.beginDelayedTransition(binding.containerPoints, createPointTransition())
        discretePoints.removeAt(index)
        binding.containerPoints.removeViewAt(index)
        updatePointIndices()
        updateWaveformGraphAndPreview()
    }

    private fun updatePointIndices() {
        binding.textPointsCount.text = getString(R.string.vibration_points_count, discretePoints.size)
        for (i in 0 until binding.containerPoints.childCount) {
            val child = binding.containerPoints.getChildAt(i)
            val titleView = child.findViewById<TextView>(R.id.text_point_title)
            titleView?.text = getString(R.string.vibration_step_title, i + 1)
            val deleteBtn = child.findViewById<View>(R.id.btn_delete_point)
            deleteBtn?.isEnabled = discretePoints.size > 1
        }
    }

    private fun renderDiscretePointViews() {
        binding.containerPoints.removeAllViews()
        val currentList = discretePoints.toList()
        discretePoints.clear()
        currentList.forEach { addDiscretePoint(it) }
    }

    private fun generateCurrentWaveform(): GeneratedWaveform {
        return if (currentMode == WaveformMode.DISCRETE) {
            WaveformEngine.buildDiscreteWaveform(discretePoints)
        } else {
            WaveformEngine.buildContinuousWaveform(
                mode = currentMode,
                freq = binding.sliderWaveFreq.value,
                amp = binding.sliderWaveAmp.value.toInt(),
                durationMs = binding.sliderWaveDuration.value.toLong(),
                dutyFraction = (binding.sliderWaveDuty.value / 100.0).coerceIn(0.1, 0.9),
                startFreq = binding.sliderWaveFreq.value,
                endFreq = binding.sliderChirpEnd.value,
                isLfoEnabled = isLfoEnabled,
                lfoShape = lfoShape,
                lfoDepth = binding.sliderLfoDepth.value,
                lfoRate = binding.sliderLfoRate.value
            )
        }
    }

    private fun updateWaveformGraphAndPreview() {
        lastPlaybackRunner = { looping -> runWaveform(looping) }
        val waveform = generateCurrentWaveform()
        binding.graphView.setPattern(waveform.graphPoints, waveform.isContinuous)

        val repeat = if (isLoopingMode) 0 else -1
        binding.textPatternPreview.text = if (waveform.timings.isEmpty()) {
            ""
        } else {
            getString(
                R.string.vibration_preview,
                waveform.timings.take(8).toString() + if (waveform.timings.size > 8) "…" else "",
                waveform.amplitudes.take(8).toString() + if (waveform.amplitudes.size > 8) "…" else "",
                repeat
            )
        }

        if (isPlaying && waveform.timings.isNotEmpty()) {
            currentActiveWaveform = waveform
            val now = SystemClock.uptimeMillis()
            pendingHapticDispatch?.let { mainHandler.removeCallbacks(it) }
            val timeSinceLast = now - lastHapticDispatchMs
            if (timeSinceLast >= 75L) {
                lastHapticDispatchMs = now
                Haptics.playWaveform(vibrator, waveform.timings, waveform.amplitudes, repeat)
            } else {
                val runnable = Runnable {
                    if (isPlaying && currentActiveWaveform != null) {
                        lastHapticDispatchMs = SystemClock.uptimeMillis()
                        val wave = currentActiveWaveform ?: return@Runnable
                        val rep = if (isLoopingMode) 0 else -1
                        Haptics.playWaveform(vibrator, wave.timings, wave.amplitudes, rep)
                    }
                }
                pendingHapticDispatch = runnable
                mainHandler.postDelayed(runnable, 75L - timeSinceLast)
            }
        }
    }

    private fun setPlaybackUiState(playing: Boolean, looping: Boolean) {
        isPlaying = playing
        isLoopingMode = looping

        val colorPrimary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary, Color.parseColor("#00BCD4"))
        val colorOnPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
        val colorError = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorError, Color.parseColor("#BA1A1A"))
        val colorOnError = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnError, Color.WHITE)

        if (playing) {
            if (looping) {
                binding.btnLoopWaveform.setText(R.string.vibration_stop)
                binding.btnLoopWaveform.backgroundTintList = ColorStateList.valueOf(colorError)
                binding.btnLoopWaveform.setTextColor(colorOnError)

                binding.btnPlayWaveform.setText(R.string.vibration_play)
                binding.btnPlayWaveform.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                binding.btnPlayWaveform.setTextColor(colorOnPrimary)
            } else {
                binding.btnPlayWaveform.setText(R.string.vibration_stop)
                binding.btnPlayWaveform.backgroundTintList = ColorStateList.valueOf(colorError)
                binding.btnPlayWaveform.setTextColor(colorOnError)

                binding.btnLoopWaveform.setText(R.string.vibration_loop)
                binding.btnLoopWaveform.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                binding.btnLoopWaveform.setTextColor(colorOnPrimary)
            }
        } else {
            activePresetTitleRes = null
            currentActiveWaveform = null
            binding.graphView.setPlaybackProgress(-1f)

            binding.btnPlayWaveform.setText(R.string.vibration_play)
            binding.btnPlayWaveform.backgroundTintList = ColorStateList.valueOf(colorPrimary)
            binding.btnPlayWaveform.setTextColor(colorOnPrimary)

            binding.btnLoopWaveform.setText(R.string.vibration_loop)
            binding.btnLoopWaveform.backgroundTintList = ColorStateList.valueOf(colorPrimary)
            binding.btnLoopWaveform.setTextColor(colorOnPrimary)
        }
        updatePresetHighlights()
    }

    private fun wirePlaybackButtons() {
        binding.btnPlayWaveform.setOnClickListener {
            if (isPlaying) {
                stopAll()
            } else {
                val runner = lastPlaybackRunner ?: { runWaveform(looping = false) }
                runner.invoke(false)
            }
        }

        binding.btnLoopWaveform.setOnClickListener {
            if (isPlaying) {
                stopAll()
            } else {
                val runner = lastPlaybackRunner ?: { runWaveform(looping = true) }
                runner.invoke(true)
            }
        }
    }

    private fun runWaveform(looping: Boolean) {
        lastPlaybackRunner = { forceLooping -> runWaveform(forceLooping) }
        stopAll()
        val waveform = generateCurrentWaveform()
        if (waveform.timings.isEmpty()) return
        currentActiveWaveform = waveform
        binding.graphView.setPattern(waveform.graphPoints, waveform.isContinuous)

        val repeat = if (looping) 0 else -1
        Haptics.playWaveform(vibrator, waveform.timings, waveform.amplitudes, repeat)

        setPlaybackUiState(playing = true, looping = looping)
        activePlaybackJob = lifecycleScope.launch(Dispatchers.Main) {
            var startTime = System.currentTimeMillis()
            while (isActive) {
                val activeWave = currentActiveWaveform ?: break
                val totalTime = activeWave.totalTimeMs.coerceAtLeast(100L)
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > totalTime) {
                    if (isLoopingMode) {
                        startTime = System.currentTimeMillis()
                    } else {
                        break
                    }
                }
                binding.graphView.setPlaybackProgress((elapsed % totalTime).toFloat())
                delay(16)
            }
            setPlaybackUiState(playing = false, looping = false)
        }
    }

    private fun wireSinglePulse() {
        binding.sliderSingleDuration.addOnChangeListener { _, value, _ ->
            binding.labelSingleDuration.text = getString(R.string.vibration_duration, value.toInt())
        }
        binding.labelSingleDuration.text = getString(R.string.vibration_duration, binding.sliderSingleDuration.value.toInt())

        binding.sliderSingleAmplitude.addOnChangeListener { _, value, _ ->
            binding.labelSingleAmplitude.text = getString(R.string.vibration_amplitude, value.toInt())
        }
        if (hasAmplitude) {
            binding.labelSingleAmplitude.text = getString(R.string.vibration_amplitude, binding.sliderSingleAmplitude.value.toInt())
        }

        binding.btnVibrate.setOnClickListener {
            if (isPlaying) {
                stopAll()
                return@setOnClickListener
            }
            val runner: (Boolean) -> Unit = { looping ->
                val dur = binding.sliderSingleDuration.value.toLong()
                val amp = binding.sliderSingleAmplitude.value.toInt()
                if (looping) {
                    playSinglePulseLooping(dur, amp)
                } else {
                    val point = VibrationGraphView.VibrationPoint(0, 0f, amp.toFloat(), dur.toFloat())
                    binding.graphView.setPattern(listOf(point), false)
                    Haptics.playOneShot(vibrator, dur, amp)
                    animateGraphProgress(dur)
                }
            }
            lastPlaybackRunner = runner
            runner.invoke(false)
        }
    }

    private fun playSinglePulseLooping(dur: Long, amp: Int) {
        stopAll()
        val point = VibrationGraphView.VibrationPoint(0, 0f, amp.toFloat(), dur.toFloat())
        binding.graphView.setPattern(listOf(point), false)
        setPlaybackUiState(playing = true, looping = true)
        val loopInterval = (dur + 60L).coerceAtLeast(100L)
        activePlaybackJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                Haptics.playOneShot(vibrator, dur, amp)
                val startTime = System.currentTimeMillis()
                while (isActive && System.currentTimeMillis() - startTime < loopInterval) {
                    val elapsed = System.currentTimeMillis() - startTime
                    binding.graphView.setPlaybackProgress(elapsed.coerceAtMost(dur).toFloat())
                    delay(16)
                }
            }
            setPlaybackUiState(playing = false, looping = false)
        }
    }

    private fun applyExpressiveCorners(card: MaterialCardView, index: Int, totalCount: Int) {
        val rLarge = 18f.dp()
        val rSmall = 4f.dp()

        val shapeBuilder = ShapeAppearanceModel.builder()
        when {
            totalCount == 1 -> shapeBuilder.setAllCornerSizes(rLarge)
            index == 0 -> shapeBuilder.setTopLeftCorner(CornerFamily.ROUNDED, rLarge)
                .setTopRightCorner(CornerFamily.ROUNDED, rLarge)
                .setBottomLeftCorner(CornerFamily.ROUNDED, rSmall)
                .setBottomRightCorner(CornerFamily.ROUNDED, rSmall)
            index == totalCount - 1 -> shapeBuilder.setTopLeftCorner(CornerFamily.ROUNDED, rSmall)
                .setTopRightCorner(CornerFamily.ROUNDED, rSmall)
                .setBottomLeftCorner(CornerFamily.ROUNDED, rLarge)
                .setBottomRightCorner(CornerFamily.ROUNDED, rLarge)
            else -> shapeBuilder.setAllCornerSizes(rSmall)
        }
        card.shapeAppearanceModel = shapeBuilder.build()
    }

    private fun updatePresetHighlights() {
        val colorPrimary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary, Color.parseColor("#00BCD4"))
        val colorSurfaceContainerHigh = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.LTGRAY)
        val colorSecondaryContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer, Color.DKGRAY)
        val colorOnSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val colorOnSurfaceVariant = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
        val colorOnSecondaryContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSecondaryContainer, Color.WHITE)

        for ((item, itemBinding) in presetBindings) {
            val isActive = isPlaying && activePresetTitleRes == item.titleRes
            if (isActive) {
                itemBinding.cardPreset.setCardBackgroundColor(colorSecondaryContainer)
                itemBinding.textPresetTitle.setTextColor(colorOnSecondaryContainer)
                itemBinding.textPresetDesc.setTextColor(colorOnSecondaryContainer)
                itemBinding.iconPreset.setImageResource(R.drawable.ic_check)
                itemBinding.iconPreset.imageTintList = ColorStateList.valueOf(colorOnSecondaryContainer)
            } else {
                itemBinding.cardPreset.setCardBackgroundColor(colorSurfaceContainerHigh)
                itemBinding.textPresetTitle.setTextColor(colorOnSurface)
                itemBinding.textPresetDesc.setTextColor(colorOnSurfaceVariant)
                itemBinding.iconPreset.setImageResource(R.drawable.ic_tool_vibrate)
                itemBinding.iconPreset.imageTintList = ColorStateList.valueOf(colorPrimary)
            }
        }
    }

    private fun populatePresetGroup(container: ViewGroup, presets: List<PresetItem>) {
        container.removeAllViews()
        presets.forEachIndexed { index, item ->
            val itemBinding = ItemVibrationPresetBinding.inflate(layoutInflater, container, false)
            itemBinding.textPresetTitle.setText(item.titleRes)
            itemBinding.textPresetDesc.setText(item.descRes)
            applyExpressiveCorners(itemBinding.cardPreset, index, presets.size)

            itemBinding.cardPreset.setOnClickListener {
                item.action(this)
                activePresetTitleRes = item.titleRes
                updatePresetHighlights()
            }
            container.addView(itemBinding.root)
            presetBindings.add(item to itemBinding)
        }
    }

    private fun wirePresets() {
        presetBindings.clear()
        populatePresetGroup(binding.containerOneshotPresets, PresetCatalog.ONE_SHOTS)
        populatePresetGroup(binding.containerLoopingPresets, PresetCatalog.LOOPING_RHYTHMS)
    }

    private fun animateGraphProgress(durationMs: Long) {
        activePlaybackJob?.cancel()
        setPlaybackUiState(playing = true, looping = false)
        activePlaybackJob = lifecycleScope.launch(Dispatchers.Main) {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > durationMs + 40) break
                binding.graphView.setPlaybackProgress(elapsed.toFloat())
                delay(16)
            }
            setPlaybackUiState(playing = false, looping = false)
        }
    }

    override fun stopAll() {
        activePlaybackJob?.cancel()
        activePlaybackJob = null
        currentActiveWaveform = null
        pendingHapticDispatch?.let { mainHandler.removeCallbacks(it) }
        pendingHapticDispatch = null
        scheduledTasks.forEach { mainHandler.removeCallbacks(it) }
        scheduledTasks.clear()
        Haptics.cancel(vibrator)
        setPlaybackUiState(playing = false, looping = false)
    }

    override fun playWaveformDirect(waveform: GeneratedWaveform, looping: Boolean, presetTitleRes: Int) {
        lastPlaybackRunner = { forceLooping ->
            playWaveformDirectInternal(waveform, forceLooping, presetTitleRes)
        }
        playWaveformDirectInternal(waveform, looping, presetTitleRes)
    }

    private fun playWaveformDirectInternal(waveform: GeneratedWaveform, looping: Boolean, presetTitleRes: Int) {
        stopAll()
        if (waveform.timings.isEmpty()) return
        currentActiveWaveform = waveform
        activePresetTitleRes = presetTitleRes

        binding.graphView.setPattern(waveform.graphPoints, waveform.isContinuous)
        val repeat = if (looping) 0 else -1
        Haptics.playWaveform(vibrator, waveform.timings, waveform.amplitudes, repeat)

        setPlaybackUiState(playing = true, looping = looping)
        activePlaybackJob = lifecycleScope.launch(Dispatchers.Main) {
            var startTime = System.currentTimeMillis()
            while (isActive) {
                val activeWave = currentActiveWaveform ?: break
                val totalTime = activeWave.totalTimeMs.coerceAtLeast(100L)
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > totalTime) {
                    if (isLoopingMode) {
                        startTime = System.currentTimeMillis()
                    } else {
                        break
                    }
                }
                binding.graphView.setPlaybackProgress((elapsed % totalTime).toFloat())
                delay(16)
            }
            setPlaybackUiState(playing = false, looping = false)
        }
    }

    override fun playOneShotPreset(
        points: List<VibrationGraphView.VibrationPoint>,
        durationMs: Long,
        presetTitleRes: Int,
        action: () -> Unit
    ) {
        lastPlaybackRunner = { looping ->
            if (looping) {
                runOneShotPresetLooping(points, durationMs, presetTitleRes, action)
            } else {
                stopAll()
                activePresetTitleRes = presetTitleRes
                binding.graphView.setPattern(points, false)
                action()
                animateGraphProgress(durationMs)
            }
        }
        stopAll()
        activePresetTitleRes = presetTitleRes
        binding.graphView.setPattern(points, false)
        action()
        animateGraphProgress(durationMs)
    }

    private fun runOneShotPresetLooping(
        points: List<VibrationGraphView.VibrationPoint>,
        durationMs: Long,
        presetTitleRes: Int,
        action: () -> Unit
    ) {
        stopAll()
        activePresetTitleRes = presetTitleRes
        binding.graphView.setPattern(points, false)
        setPlaybackUiState(playing = true, looping = true)

        val cyclePeriod = (durationMs + 60L).coerceAtLeast(120L)
        activePlaybackJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                action()
                val startTime = System.currentTimeMillis()
                while (isActive && System.currentTimeMillis() - startTime < cyclePeriod) {
                    val elapsed = System.currentTimeMillis() - startTime
                    binding.graphView.setPlaybackProgress(elapsed.coerceAtMost(durationMs).toFloat())
                    delay(16)
                }
            }
            setPlaybackUiState(playing = false, looping = false)
        }
    }

    override fun postDelayed(delayMs: Long, action: () -> Unit) {
        val runnable = Runnable { action() }
        scheduledTasks.add(runnable)
        mainHandler.postDelayed(runnable, delayMs)
    }

    override fun onStop() {
        super.onStop()
        stopAll()
    }
}
