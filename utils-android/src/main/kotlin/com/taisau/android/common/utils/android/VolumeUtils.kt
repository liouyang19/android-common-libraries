package com.taisau.android.common.utils.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VolumeUtils {

    private fun getAudioManager(context: Context): AudioManager {
        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun getMusicVolume(context: Context): Int {
        return getAudioManager(context).getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun setMusicVolume(context: Context, volume: Int) {
        val am = getAudioManager(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volume.coerceIn(0, max), 0)
    }

    fun getMaxMusicVolume(context: Context): Int {
        return getAudioManager(context).getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    fun getAlarmVolume(context: Context): Int {
        return getAudioManager(context).getStreamVolume(AudioManager.STREAM_ALARM)
    }

    fun setAlarmVolume(context: Context, volume: Int) {
        val am = getAudioManager(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, volume.coerceIn(0, max), 0)
    }

    fun getRingVolume(context: Context): Int {
        return getAudioManager(context).getStreamVolume(AudioManager.STREAM_RING)
    }

    fun setRingVolume(context: Context, volume: Int) {
        val am = getAudioManager(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        am.setStreamVolume(AudioManager.STREAM_RING, volume.coerceIn(0, max), 0)
    }

    fun getNotificationVolume(context: Context): Int {
        return getAudioManager(context).getStreamVolume(AudioManager.STREAM_NOTIFICATION)
    }

    fun setNotificationVolume(context: Context, volume: Int) {
        val am = getAudioManager(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
        am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, volume.coerceIn(0, max), 0)
    }

    fun getCallVolume(context: Context): Int {
        return getAudioManager(context).getStreamVolume(AudioManager.STREAM_VOICE_CALL)
    }

    fun setCallVolume(context: Context, volume: Int) {
        val am = getAudioManager(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, volume.coerceIn(0, max), 0)
    }

    fun getSystemVolume(context: Context): Int {
        return getAudioManager(context).getStreamVolume(AudioManager.STREAM_SYSTEM)
    }

    fun isMusicMuted(context: Context): Boolean {
        return getMusicVolume(context) == 0
    }

    fun isVibrateMode(context: Context): Boolean {
        val am = getAudioManager(context)
        return am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE
    }

    fun isSilentMode(context: Context): Boolean {
        val am = getAudioManager(context)
        return am.getRingerMode() == AudioManager.RINGER_MODE_SILENT
    }

    fun isNormalMode(context: Context): Boolean {
        val am = getAudioManager(context)
        return am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL
    }

    fun setRingerModeNormal(context: Context) {
        getAudioManager(context).ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    fun setRingerModeSilent(context: Context) {
        getAudioManager(context).ringerMode = AudioManager.RINGER_MODE_SILENT
    }

    fun setRingerModeVibrate(context: Context) {
        getAudioManager(context).ringerMode = AudioManager.RINGER_MODE_VIBRATE
    }

    fun vibrate(context: Context, durationMillis: Long = 300) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            vibrator.vibrate(durationMillis)
        }
    }

    fun vibratePattern(context: Context, pattern: LongArray, repeat: Int = -1) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, repeat)
            )
        } else {
            vibrator.vibrate(pattern, repeat)
        }
    }

    fun vibrate(context: Context, amplitude: Int, durationMillis: Long = 300) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(
                VibrationEffect.createOneShot(durationMillis, amplitude.coerceIn(1, 255))
            )
        } else {
            vibrate(context, durationMillis)
        }
    }

    fun cancelVibration(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.cancel()
    }

    fun playNotificationSound(context: Context, uri: Uri? = null) {
        val soundUri = uri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, soundUri)
                setOnCompletionListener { it.release() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playAlarmSound(context: Context, uri: Uri? = null) {
        val soundUri = uri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try {
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, soundUri)
                isLooping = true
                setOnCompletionListener { it.release() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopSound(mediaPlayer: MediaPlayer?) {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
