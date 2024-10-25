package com.skz.microphone

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouter.RouteInfo


class MicrophoneActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener,
    View.OnClickListener {


    companion object {
        const val APP_TAG: String = "MICROPHONE"
        const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    private var isActive = false
    private lateinit var sharedPreferences: SharedPreferences

    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
    private lateinit var mediaRouter: MediaRouter
    private lateinit var audioRouteAdapter: AudioRouteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mediaRouter = MediaRouter.getInstance(this)
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .build()
        mediaRouter.addCallback(selector, mediaRouterCallback)


        Log.d(APP_TAG, "Opening mic activity");
        this.sharedPreferences = getSharedPreferences(APP_TAG, MODE_PRIVATE);
        this.sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        this.sharedPreferences = getSharedPreferences(APP_TAG, MODE_PRIVATE);
        this.sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        this.isActive = this.sharedPreferences.getBoolean("active", false);

        val parentLayout: RelativeLayout = findViewById(R.id.main)
        val spinnerAudioDevices: Spinner = findViewById(R.id.spinnerAudioDevices)

        parentLayout.post {
            val parentWidth = parentLayout.width
            val spinnerWidth = (parentWidth * 0.7).toInt()
            val layoutParams = LinearLayout.LayoutParams(spinnerWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL
            spinnerAudioDevices.layoutParams = layoutParams
        }

        audioRouteAdapter = AudioRouteAdapter(this, getAudioRoutes())
        spinnerAudioDevices.adapter = audioRouteAdapter

        spinnerAudioDevices.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedRoute = parent.getItemAtPosition(position) as RouteInfo
                changeAudioRoute(selectedRoute)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // No hacer nada
            }
        }



        if (this.isActive) {
            checkAudioPermission()
        }

        val recordButton = findViewById<ImageButton>(R.id.record_button)
        recordButton.setOnClickListener(this)
        recordButton.setImageBitmap(BitmapFactory.decodeResource(resources, if (this.isActive) R.drawable.ic_mic_off else R.drawable.ic_mic))

    }

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter?, route: RouteInfo?) {
            super.onRouteAdded(router, route)
            updateAudioRoutes()
        }

        override fun onRouteRemoved(router: MediaRouter?, route: RouteInfo?) {
            super.onRouteRemoved(router, route)
            updateAudioRoutes()
        }

        override fun onRouteChanged(router: MediaRouter?, route: RouteInfo?) {
            super.onRouteChanged(router, route)
            updateAudioRoutes()
        }
    }

    private fun updateAudioRoutes() {
        val audioDevices = getAudioRoutes()
        audioRouteAdapter.updateRoutes(audioDevices)
    }


    private fun changeAudioRoute(route: RouteInfo) {
        mediaRouter.selectRoute(route)
    }

    private fun getAudioRoutes(): List<RouteInfo> {
        return mediaRouter.routes.filter { route ->
            route.supportsControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO) ||
                    route.supportsControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
        }
    }

    private var attemptCount = 0
    private val maxAttempts = 3

    private fun checkAudioPermission() {
        attemptCount = sharedPreferences.getInt("attemptCount", 0)

        val statusPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (statusPermission == PackageManager.PERMISSION_GRANTED) {
            startService(Intent(this, MicrophoneService::class.java))
        } else {
            if (attemptCount < maxAttempts) {
                attemptCount++
                sharedPreferences.edit().putInt("attemptCount", attemptCount).apply()

                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                    showPermissionRationale()
                } else {
                    requestMicrophonePermission()
                }
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        this.sharedPreferences.edit().putBoolean("active", false).apply()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }


    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Permiso Necesario")
            .setMessage("Esta aplicación necesita acceso al micrófono para grabar audio.")
            .setPositiveButton("OK") { _, _ ->
                requestMicrophonePermission()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun requestMicrophonePermission() {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
    }


    public override fun onDestroy() {
        super.onDestroy()
        Log.d(APP_TAG, "Closing mic activity")
        this.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }


    override fun onClick(v: View) {
        if (v.id == R.id.record_button) {
            val e: SharedPreferences.Editor = this.sharedPreferences.edit()
            e.putBoolean("active", !this.isActive)
            e.apply()
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                permissionToRecordAccepted = true
                checkAudioPermission()
            } else {
                permissionToRecordAccepted = false
                checkAudioPermission()
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "active") {
            val isActive = this.sharedPreferences.getBoolean("active", false)
            Log.d(APP_TAG, "Mic state changing (from ${this.isActive} to $isActive)")
            if (isActive != this.isActive) {
                this.isActive = isActive
                checkAudioPermission()
                runOnUiThread {
                    val b = findViewById<ImageButton>(R.id.record_button)
                    b.setImageBitmap(BitmapFactory.decodeResource(resources, if (this.isActive) R.drawable.ic_mic_off else R.drawable.ic_mic))
                }
            }
        }
    }
}