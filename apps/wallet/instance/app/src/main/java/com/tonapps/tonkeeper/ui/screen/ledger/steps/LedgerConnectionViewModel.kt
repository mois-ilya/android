package com.tonapps.tonkeeper.ui.screen.ledger.steps

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.live.ble.BleManager
import com.ledger.live.ble.BleManagerFactory
import com.tonapps.ledger.ble.BleTransport
import com.tonapps.ledger.devices.Devices
import com.tonapps.ledger.ton.TonTransport
import com.tonapps.tonkeeper.ui.screen.ledger.steps.list.Item
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uikit.extensions.context

class LedgerConnectionViewModel(app: Application) : AndroidViewModel(app) {
    private val bleManager: BleManager = BleManagerFactory.newInstance(context)
    private var pollTonAppJob: Job? = null
    private var disconnectTimeoutJob: Job? = null

    private var targetDeviceId: String? = null

    val permissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
    )

    private val _connectionState: MutableSharedFlow<ConnectionState> =
        MutableSharedFlow(replay = 1, extraBufferCapacity = 1)

    private val _connectedDevice = MutableStateFlow<ConnectedDevice?>(null)
    private val _tonTransport = MutableStateFlow<TonTransport?>(null)
    val tonTransport = _tonTransport.asSharedFlow()
    val connectionData = combine(_connectedDevice, _tonTransport) { device, transport ->
        device to transport
    }

    val currentStepFlow = _connectionState.map { state ->
        when (state) {
            ConnectionState.Idle -> LedgerStep.CONNECT
            ConnectionState.Scanning -> LedgerStep.CONNECT
            is ConnectionState.Connected -> LedgerStep.OPEN_TON_APP
            is ConnectionState.TonAppOpened -> if (targetDeviceId != null) LedgerStep.CONFIRM_TX else LedgerStep.DONE
            is ConnectionState.Disconnected -> LedgerStep.CONNECT
        }
    }

    val uiItemsFlow = currentStepFlow.map { step ->
        createList(step)
    }

    val displayTextFlow = currentStepFlow.map { state ->
        when {
            targetDeviceId != null && (state == LedgerStep.CONFIRM_TX || state == LedgerStep.DONE) -> "Review"
            else -> "TON ready"
        }
    }

    private val _bluetoothState = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 1)
    val bluetoothState = _bluetoothState.asSharedFlow()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _bluetoothState.tryEmit(state)
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothReceiver, filter)

        // Get BluetoothManager and BluetoothAdapter
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        // Emit initial state
        val initialState = bluetoothAdapter?.state ?: BluetoothAdapter.ERROR
        _bluetoothState.tryEmit(initialState)
        _connectionState.tryEmit(ConnectionState.Idle)
    }

    override fun onCleared() {
        super.onCleared()
        context.unregisterReceiver(bluetoothReceiver)
    }

    fun setTargetDeviceId(deviceId: String) {
        targetDeviceId = deviceId
    }

    fun isPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PermissionChecker.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PermissionChecker.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun scanOrConnect() {
        if (targetDeviceId != null) {
            connect(targetDeviceId!!)
        } else {
            scan()
        }
    }

    private fun scan() {
        _connectionState.tryEmit(ConnectionState.Scanning)
        bleManager.startScanning {
            val device = it.first()

            bleManager.stopScanning()
            connect(device.id, true)
        }
    }

    private fun connect(deviceId: String, scanOnFail: Boolean = false) {
        bleManager.connect(address = deviceId, onConnectError = {
            pollTonAppJob?.cancel()

            viewModelScope.launch {
                delay(1000)
                Log.d("LEDGER", "Reconnecting to device $deviceId")
                connect(deviceId)
            }

            disconnectTimeoutJob?.cancel()
            disconnectTimeoutJob = viewModelScope.launch {
                delay(4000)
                _connectionState.tryEmit(ConnectionState.Disconnected())
                _connectedDevice.tryEmit(null)
                _tonTransport.tryEmit(null)
            }
        }, onConnectSuccess = {
            disconnectTimeoutJob?.cancel()
            _connectionState.tryEmit(ConnectionState.Connected)
            _connectedDevice.tryEmit(
                ConnectedDevice(
                    deviceId, Devices.fromServiceUuid(it.serviceId!!)
                )
            )

            waitForTonAppOpen()
        })
    }

    fun disconnect() {
        disconnectTimeoutJob?.cancel()
        pollTonAppJob?.cancel()
        bleManager.disconnect()
        _connectionState.tryEmit(ConnectionState.Disconnected())
        _connectedDevice.tryEmit(null)
        _tonTransport.tryEmit(null)
    }

    private fun waitForTonAppOpen() {
        pollTonAppJob?.cancel()
        pollTonAppJob = viewModelScope.launch {
            try {
                val tonTransport = TonTransport(BleTransport(bleManager))
                while (!tonTransport.isAppOpen()) {
                    Log.d("LEDGER", "Waiting for app to open")
                    delay(1000)
                }

                _connectionState.tryEmit(ConnectionState.TonAppOpened)
                _tonTransport.tryEmit(tonTransport)
            } catch (_: Exception) {
            }
        }
    }

    private fun createList(currentStep: LedgerStep): List<Item> {
        val uiItems = mutableListOf<Item>()

        uiItems.add(
            Item.Step(
                context.getString(Localization.ledger_connect),
                currentStep !== LedgerStep.CONNECT,
                currentStep == LedgerStep.CONNECT
            )
        )

        uiItems.add(
            Item.Step(
                context.getString(Localization.ledger_open_ton_app),
                currentStep == LedgerStep.DONE || currentStep == LedgerStep.CONFIRM_TX,
                currentStep == LedgerStep.OPEN_TON_APP
            )
        )

        if (targetDeviceId != null) {
            uiItems.add(
                Item.Step(
                    context.getString(Localization.ledger_confirm_tx),
                    currentStep == LedgerStep.DONE,
                    currentStep == LedgerStep.CONFIRM_TX
                )
            )
        }

        return uiItems
    }
}