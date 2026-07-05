package com.example.androidautosound

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.androidautosound.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Guards listeners while [render] sets view state programmatically. */
    private var suppress = false

    private var onBluetoothGranted: (() -> Unit)? = null
    private var onBluetoothDenied: (() -> Unit)? = null

    private val pickConnect = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> onPicked(uri, connect = true) }
    }
    private val pickDisconnect = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> onPicked(uri, connect = false) }
    }
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { enableNow() } // proceed regardless — a denied notification only hides the banner

    private val requestBluetooth = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val granted1 = onBluetoothGranted
        val denied = onBluetoothDenied
        onBluetoothGranted = null
        onBluetoothDenied = null
        if (granted) granted1?.invoke() else {
            toast(R.string.bt_permission_needed)
            denied?.invoke()
        }
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        attachListeners()
        render()
    }

    private fun attachListeners() {
        binding.pickButton.setOnClickListener { pickConnect.launch(AUDIO) }
        binding.testButton.setOnClickListener { preview(SoundPrefs.connectUri(this)) }

        binding.disconnectSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppress) return@setOnCheckedChangeListener
            SoundPrefs.setDisconnectEnabled(this, checked)
            render()
        }
        binding.pickDisconnectButton.setOnClickListener { pickDisconnect.launch(AUDIO) }
        binding.testDisconnectButton.setOnClickListener { preview(SoundPrefs.disconnectUri(this)) }

        binding.volumeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) { SoundPrefs.setVolume(this, value.toInt()); updateVolumeLabel() }
        }
        binding.delaySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) { SoundPrefs.setDelayMs(this, (value * 1000).toInt()); updateDelayLabel() }
        }

        binding.modeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppress) return@setOnCheckedChangeListener
            SoundPrefs.setTriggerMode(
                this,
                if (checkedId == R.id.modeReliable) SoundPrefs.MODE_CARCONNECTION else SoundPrefs.MODE_BLUETOOTH
            )
            if (SoundPrefs.isEnabled(this)) reapplyServiceState()
            render()
        }
        binding.chooseCarButton.setOnClickListener { ensureBluetooth(onGranted = ::showCarPicker) }

        binding.enableSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppress) return@setOnCheckedChangeListener
            if (checked) onEnable() else disable()
        }
        binding.reliabilityButton.setOnClickListener { requestBatteryExemption() }
    }

    private fun onPicked(uri: Uri, connect: Boolean) {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = displayName(uri) ?: uri.lastPathSegment ?: getString(R.string.default_sound_name)
        if (connect) SoundPrefs.setConnectSound(this, uri.toString(), name)
        else SoundPrefs.setDisconnectSound(this, uri.toString(), name)
        render()
    }

    // --- Enable / disable ---------------------------------------------------

    private fun onEnable() {
        if (SoundPrefs.connectUri(this) == null) return abortEnable(R.string.pick_first)

        if (SoundPrefs.triggerMode(this) == SoundPrefs.MODE_BLUETOOTH) {
            if (SoundPrefs.carAddress(this) == null) return abortEnable(R.string.choose_car_first)
            ensureBluetooth(onGranted = ::enableNow, onDenied = ::revertEnableSwitch)
        } else if (needsNotificationPermission()) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableNow()
        }
    }

    private fun enableNow() {
        SoundPrefs.setEnabled(this, true)
        reapplyServiceState()
        render()
    }

    private fun disable() {
        SoundPrefs.setEnabled(this, false)
        CarConnectionService.stop(this)
        render()
    }

    private fun abortEnable(messageRes: Int) {
        toast(messageRes)
        revertEnableSwitch()
    }

    private fun revertEnableSwitch() {
        suppress = true
        binding.enableSwitch.isChecked = false
        suppress = false
    }

    /** The reliable-mode service should run only when enabled in that mode. */
    private fun reapplyServiceState() {
        if (SoundPrefs.isEnabled(this) && SoundPrefs.triggerMode(this) == SoundPrefs.MODE_CARCONNECTION) {
            CarConnectionService.start(this)
        } else {
            CarConnectionService.stop(this)
        }
    }

    // --- Bluetooth ----------------------------------------------------------

    private fun ensureBluetooth(onGranted: () -> Unit, onDenied: () -> Unit = {}) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            onGranted()
        } else {
            onBluetoothGranted = onGranted
            onBluetoothDenied = onDenied
            requestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    @SuppressLint("MissingPermission") // reached only after ensureBluetooth grants the permission
    private fun showCarPicker() {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return toast(R.string.bt_off)

        val devices = adapter.bondedDevices?.toList().orEmpty()
        if (devices.isEmpty()) return toast(R.string.no_paired)

        val labels = devices.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_car)
            .setItems(labels) { _, which ->
                val device = devices[which]
                SoundPrefs.setCar(this, device.address, device.name ?: device.address)
                render()
            }
            .show()
    }

    // --- Reliability --------------------------------------------------------

    private fun requestBatteryExemption() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) return toast(R.string.reliability_done)
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    // --- Rendering ----------------------------------------------------------

    private fun render() {
        suppress = true

        binding.soundName.text = SoundPrefs.connectName(this) ?: getString(R.string.no_sound)
        binding.testButton.isEnabled = SoundPrefs.connectUri(this) != null

        val disconnectOn = SoundPrefs.disconnectEnabled(this)
        binding.disconnectSwitch.isChecked = disconnectOn
        binding.disconnectRow.visibility = if (disconnectOn) View.VISIBLE else View.GONE
        binding.disconnectName.text = SoundPrefs.disconnectName(this) ?: getString(R.string.no_sound)
        binding.testDisconnectButton.isEnabled = SoundPrefs.disconnectUri(this) != null

        binding.volumeSlider.value = SoundPrefs.volume(this).toFloat()
        binding.delaySlider.value = SoundPrefs.delayMs(this) / 1000f
        updateVolumeLabel()
        updateDelayLabel()

        val bluetooth = SoundPrefs.triggerMode(this) == SoundPrefs.MODE_BLUETOOTH
        binding.modeGroup.check(if (bluetooth) R.id.modeBluetooth else R.id.modeReliable)
        binding.carRow.visibility = if (bluetooth) View.VISIBLE else View.GONE
        binding.reliableHint.visibility = if (bluetooth) View.GONE else View.VISIBLE
        binding.carName.text = SoundPrefs.carName(this) ?: getString(R.string.no_car)

        binding.enableSwitch.isChecked = SoundPrefs.isEnabled(this)

        suppress = false
    }

    private fun updateVolumeLabel() {
        binding.volumeValue.text = getString(R.string.percent_format, SoundPrefs.volume(this))
    }

    private fun updateDelayLabel() {
        binding.delayValue.text = getString(R.string.seconds_format, SoundPrefs.delayMs(this) / 1000f)
    }

    private fun preview(uri: String?) {
        uri?.let { SoundPlayer.play(this, it.toUri(), volumeFraction()) }
    }

    private fun volumeFraction() = SoundPrefs.volume(this) / 100f

    private fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private fun toast(messageRes: Int) = Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()

    companion object {
        private val AUDIO = arrayOf("audio/*")
    }
}
