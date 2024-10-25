package com.skz.microphone

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioTrack.STATE_INITIALIZED
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.skz.microphone.MicrophoneActivity.Companion.APP_TAG
import java.nio.ByteBuffer


class MicrophoneService: Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var isActive = false
    private val format = AudioFormat.ENCODING_PCM_16BIT
    private var sampleRate = 44100
    private var audioInput: AudioRecord? = null
    private var audioOutput: AudioTrack? = null
    private var inChannel: Int = AudioFormat.CHANNEL_IN_STEREO
    private var outChannel: Int = AudioFormat.CHANNEL_OUT_STEREO
    private var transferMode: Int = AudioTrack.MODE_STREAM
    private var inBufferSize = 0
    private var outBufferSize = 0
    private var broadcastReceiver: MicrophoneReceiver? = null
    private lateinit var sharedPreferences: SharedPreferences

    class MicrophoneReceiver private constructor() : BroadcastReceiver() {
        internal constructor(
            microphoneService: MicrophoneService?,
            microphoneReceiver: MicrophoneReceiver?
        ) : this()

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null && action == "android.media.AUDIO_BECOMING_NOISY") {
                val prefs = context.getSharedPreferences(APP_TAG, 0)
                val editor = prefs.edit()
                editor.putBoolean("active", false)
                editor.apply()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(APP_TAG, "Creating mic service")

        this.broadcastReceiver = MicrophoneReceiver(this, null)
        inBufferSize = AudioRecord.getMinBufferSize(sampleRate, inChannel, format)
        outBufferSize = AudioTrack.getMinBufferSize(sampleRate, outChannel, format)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        audioInput = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, inChannel, format, inBufferSize)
        val audioFormat = AudioFormat.Builder()
            .setEncoding(format)
            .setSampleRate(sampleRate)
            .setChannelMask(outChannel)
            .build()

        audioOutput = AudioTrack.Builder()
            .setAudioFormat(audioFormat)
            .setTransferMode(transferMode)
            .setBufferSizeInBytes(outBufferSize)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()
//        audioOutput = AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_STEREO, format, outBufferSize, transferMode)


        this.sharedPreferences = getSharedPreferences(APP_TAG, Context.MODE_PRIVATE)
        this.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        isActive = this.sharedPreferences.getBoolean("active", false)
        if (isActive) {
             record()
        }
    }

    override fun onDestroy() {
        Log.d(APP_TAG, "Stopping mic service")
        val e: SharedPreferences.Editor = this.sharedPreferences.edit()
        e.putBoolean("active", false)
        e.apply()
        this.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        this.audioInput?.release()
        this.audioOutput?.release()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(APP_TAG, "Service sent intent")
        if (intent != null && intent.action != null && intent.action == "com.skz.microphone.STOP") {
            Log.d(APP_TAG, "Cancelling recording via notification click")
            val editor: SharedPreferences.Editor = this.sharedPreferences.edit()
            editor.putBoolean("active", false)
            editor.apply()
        }
        return START_STICKY
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "active") {
            val isActive = this.sharedPreferences.getBoolean("active", false)
            Log.d(APP_TAG, "Mic state changing (from ${this.isActive} to $isActive)")
            if (isActive != this.isActive) {
                this.isActive = isActive
                if (this.isActive) {
                    record()
                }
            }
        }
    }

    private fun record() {
        val thread: Thread = object : Thread() {
            override fun run() {
                registerReceiver(
                    broadcastReceiver,
                    IntentFilter("android.media.AUDIO_BECOMING_NOISY")
                );

                Log.d(APP_TAG, "Entered record loop")
                recordLoop()
                Log.d(APP_TAG, "Record loop finished")
            }

            private fun recordLoop() {
                try {
                    if (audioOutput!!.state == STATE_INITIALIZED) {
                        try {
                        } catch (e: Exception) {
                            Log.d(APP_TAG, "Error somewhere in record loop.")
                        }
                        if (audioInput!!.state == STATE_INITIALIZED) {
                            try {
                                audioOutput?.play()
                                try {
                                    audioInput?.startRecording()
                                    try {
                                        val bytes =
                                            ByteBuffer.allocateDirect(inBufferSize)
                                        val byteArray = ByteArray(inBufferSize)
                                        while (isActive) {
                                            val o: Int =
                                                audioInput!!.read(
                                                    bytes,
                                                    inBufferSize
                                                )
                                            bytes[byteArray]
                                            bytes.rewind()
                                            audioOutput!!.write(byteArray, 0, o)
                                        }
                                        Log.d(APP_TAG, "Finished recording")
                                    } catch (e2: Exception) {
                                        Log.d(
                                            APP_TAG,
                                            "Error while recording, aborting."
                                        )
                                    }
                                    try {
                                        audioOutput?.stop()
                                        try {
                                            if (audioInput?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                                audioInput?.stop()
                                            }
                                            unregisterReceiver(broadcastReceiver)
                                            return
                                        } catch (e3: Exception) {
                                            e3.printStackTrace()
                                            Log.e(APP_TAG, "Error al detener la grabación: ${e3.message}")
                                            Log.e(APP_TAG, "Can't stop recording")
                                            return
                                        }
                                    } catch (e4: Exception) {
                                        Log.e(APP_TAG, "Can't stop playback")
                                        audioInput?.stop()
                                        return
                                    }
                                } catch (e5: Exception) {
                                    Log.e(APP_TAG, "Failed to start recording")
                                    audioOutput?.stop()
                                    return
                                }
                            } catch (e6: Exception) {
                                Log.e(APP_TAG, "Failed to start playback")
                                return
                            }
                        }
                    }
                    unregisterReceiver(broadcastReceiver)
                    return
                } catch (e7: IllegalArgumentException) {
                    Log.e(APP_TAG, "Receiver wasn't registered: $e7")
                    return
                }
            }
        }
        thread.start()
    }

}