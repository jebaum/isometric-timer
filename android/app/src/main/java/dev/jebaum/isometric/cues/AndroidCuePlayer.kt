package dev.jebaum.isometric.cues

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.jebaum.isometric.timer.Kind

/**
 * Sound and vibration for phase boundaries.
 *
 * Every platform handle here is treated as optional: a missing vibrator or a
 * `ToneGenerator` that fails to allocate degrades this to silence rather than
 * taking down a timer whose actual job is counting.
 */
class AndroidCuePlayer(context: Context) : CuePlayer {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val vibrator: Vibrator? =
        context.getSystemService(VibratorManager::class.java)
            ?.defaultVibrator
            ?.takeIf { it.hasVibrator() }

    // STREAM_MUSIC so the volume rocker adjusts cues the same way it adjusts
    // whatever is already playing, instead of hiding them behind alarm volume.
    private val tones = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
    }.getOrNull()

    // Alarm usage so a cue still reaches you under a silent ringer profile —
    // the whole point is that you feel it while not looking at the screen.
    private val vibrationAttributes =
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)

    // Short transient duck, so a beep cuts through music rather than mixing
    // underneath it at whatever the media volume happens to be.
    private val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .build()

    private val handler = Handler(Looper.getMainLooper())
    private val abandonFocus = Runnable {
        runCatching { audioManager?.abandonAudioFocusRequest(focusRequest) }
    }

    private var released = false

    override fun play(cue: Cue) {
        if (released) return
        when (cue) {
            is Cue.Enter -> when (cue.kind) {
                Kind.HOLD -> {
                    tone(ToneGenerator.TONE_PROP_BEEP2, 300)
                    vibrate(VibrationEffect.createWaveform(longArrayOf(0, 90, 80, 90), -1))
                }
                Kind.SWITCH -> {
                    tone(ToneGenerator.TONE_PROP_BEEP, 150)
                    vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                Kind.REST -> {
                    tone(ToneGenerator.TONE_PROP_ACK, 250)
                    vibrate(VibrationEffect.createOneShot(140, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
            Cue.Warn -> {
                tone(ToneGenerator.TONE_PROP_BEEP, 90)
                vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            Cue.Done -> {
                tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
                vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200, 120, 400), -1))
            }
        }
    }

    private fun tone(type: Int, durationMillis: Int) {
        val generator = tones ?: return
        runCatching {
            audioManager?.requestAudioFocus(focusRequest)
            generator.startTone(type, durationMillis)
        }
        handler.removeCallbacks(abandonFocus)
        handler.postDelayed(abandonFocus, durationMillis + FOCUS_TAIL_MILLIS)
    }

    private fun vibrate(effect: VibrationEffect) {
        val device = vibrator ?: return
        runCatching { device.vibrate(effect, vibrationAttributes) }
    }

    override fun release() {
        if (released) return
        released = true
        handler.removeCallbacks(abandonFocus)
        abandonFocus.run()
        runCatching { tones?.release() }
    }

    private companion object {
        const val TONE_VOLUME = 80

        /**
         * Held well past the tone, and deliberately longer than the ~1s gap
         * between the closing warnings of a hold. At a short tail the focus is
         * abandoned between each warning, so anything already playing un-ducks
         * and re-ducks four times a hold — thirty-two times over the default
         * routine. This coalesces a phase cue and its warnings into one duck.
         */
        const val FOCUS_TAIL_MILLIS = 1_500L
    }
}
