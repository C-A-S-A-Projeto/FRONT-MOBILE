package fatec.labweb.projetocasa.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.UUID

data class BleDispositivoInfo(
    val nome: String,
    val enderecoMac: String,
    val device: BluetoothDevice,
    val rssi: Int = 0,
    val isPareadoSistema: Boolean = false
)

/**
 * Gerenciador de comunicação Bluetooth Low Energy (BLE) para pareamento com o ESP32 v11.
 * Implementa escaneamento (filtro "CASA"), conexão GATT, leitura do MAC e envio de credenciais.
 */
class Esp32BleManager(private val context: Context) {

    companion object {
        private const val TAG = "Esp32BleManager"

        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CHAR_CONFIG_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8") // WRITE: {"pin","ssid","pass"}
        val CHAR_MAC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9")    // READ: MAC físico
        val CHAR_STATUS_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26aa") // READ: Status

        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeGatt: BluetoothGatt? = null
    private var isScanning = false
    private var scanCallback: ScanCallback? = null

    var macLidoDoDispositivo: String? = null
        private set

    fun temPermissoesNecessarias(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            scan && connect
        } else {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val bt = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            fine && bt
        }
    }

    fun obterPermissoesObrigatorias(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    fun pararEscaneamento() = pararScan()

    fun isBluetoothHabilitado(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Retorna a lista de dispositivos já pareados nas configurações Bluetooth do Android
     * cujo nome inicie com "CASA" ou "CASA-".
     */
    @SuppressLint("MissingPermission")
    fun obterDispositivosPareadosDoSistema(): List<BleDispositivoInfo> {
        if (!temPermissoesNecessarias() || bluetoothAdapter == null) return emptyList()

        return try {
            val bonded = bluetoothAdapter.bondedDevices ?: emptySet()
            bonded.filter { device ->
                val name = device.name ?: ""
                name.startsWith("CASA", ignoreCase = true)
            }.map { device ->
                BleDispositivoInfo(
                    nome = device.name ?: "CASA Dispositivo",
                    enderecoMac = device.address,
                    device = device,
                    rssi = 0,
                    isPareadoSistema = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao obter pareados do sistema: ${e.message}")
            emptyList()
        }
    }

    /**
     * Inicia escaneamento BLE por dispositivos ESP32 cujo nome inicie com "CASA"
     */
    @SuppressLint("MissingPermission")
    fun iniciarScan(
        timeoutMs: Long = 15000,
        onDispositivoEncontrado: (BleDispositivoInfo) -> Unit,
        onTimeout: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!temPermissoesNecessarias()) {
            onError("Permissão de Bluetooth não concedida.")
            return
        }
        if (!isBluetoothHabilitado()) {
            onError("O Bluetooth do aparelho está desativado.")
            return
        }
        val scanner = bleScanner
        if (scanner == null) {
            onError("Scanner Bluetooth BLE não disponível neste dispositivo.")
            return
        }

        pararScan()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val dispositivosVistos = mutableSetOf<String>()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                val nome = result.scanRecord?.deviceName ?: device.name ?: ""

                if (nome.startsWith("CASA", ignoreCase = true)) {
                    val address = device.address
                    if (dispositivosVistos.add(address)) {
                        val info = BleDispositivoInfo(
                            nome = nome,
                            enderecoMac = address,
                            device = device,
                            rssi = result.rssi,
                            isPareadoSistema = false
                        )
                        mainHandler.post { onDispositivoEncontrado(info) }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                mainHandler.post { onError("Falha ao iniciar escaneamento BLE: código $errorCode") }
            }
        }

        try {
            isScanning = true
            scanner.startScan(emptyList(), scanSettings, scanCallback)

            mainHandler.postDelayed({
                if (isScanning) {
                    pararScan()
                    onTimeout()
                }
            }, timeoutMs)
        } catch (e: Exception) {
            isScanning = false
            onError("Erro ao iniciar escaneamento: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun pararScan() {
        if (isScanning && scanCallback != null && temPermissoesNecessarias()) {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Aviso ao parar scan: ${e.message}")
            }
        }
        isScanning = false
        scanCallback = null
    }

    /**
     * Conecta via GATT ao ESP32, descobre serviços e lê o MAC físico (...26a9).
     */
    @SuppressLint("MissingPermission")
    fun conectarDispositivo(
        device: BluetoothDevice,
        onConectadoComSucesso: (macLido: String) -> Unit,
        onError: (String) -> Unit
    ) {
        pararScan()
        desconectar()

        if (!temPermissoesNecessarias()) {
            onError("Permissão de conexão Bluetooth não concedida.")
            return
        }

        Log.d(TAG, "Iniciando conexão GATT com ${device.address}...")

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "GATT conectado! Solicitando MTU 512...")
                    activeGatt = gatt
                    gatt?.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "GATT desconectado (status=$status).")
                    activeGatt = null
                    mainHandler.post { onError("Conexão Bluetooth perdida com o ESP32.") }
                } else if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Falha na conexão GATT: status $status")
                    activeGatt = null
                    mainHandler.post { onError("Falha na conexão com o aparelho (status $status).") }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                Log.d(TAG, "MTU alterado para: $mtu (status=$status). Descobrindo serviços...")
                gatt?.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                    mainHandler.post { onError("Não foi possível descobrir os serviços do ESP32.") }
                    return
                }

                val service = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    mainHandler.post { onError("Serviço C.A.S.A. não encontrado no dispositivo.") }
                    return
                }

                val charMac = service.getCharacteristic(CHAR_MAC_UUID)
                if (charMac != null) {
                    Log.d(TAG, "Lendo característica de MAC...")
                    gatt.readCharacteristic(charMac)
                } else {
                    mainHandler.post { onError("Característica de MAC não encontrada no aparelho.") }
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (characteristic == null || status != BluetoothGatt.GATT_SUCCESS) return

                if (characteristic.uuid == CHAR_MAC_UUID) {
                    val rawValue = characteristic.getStringValue(0) ?: String(characteristic.value ?: byteArrayOf(), Charsets.UTF_8)
                    val macFormatado = rawValue.trim().uppercase()
                    Log.d(TAG, "MAC do aparelho lido com sucesso via BLE: $macFormatado")
                    macLidoDoDispositivo = macFormatado

                    mainHandler.post {
                        onConectadoComSucesso(macFormatado)
                    }
                }
            }
        }

        try {
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }
        } catch (e: Exception) {
            onError("Erro ao conectar: ${e.message}")
        }
    }

    /**
     * Envia o JSON {"pin":"123456","ssid":"SSID","pass":"SENHA"} para a característica ...26a8
     */
    @SuppressLint("MissingPermission")
    fun enviarCredenciaisWifi(
        pin: String,
        ssid: String,
        pass: String,
        onSucesso: () -> Unit,
        onError: (String) -> Unit
    ) {
        val gatt = activeGatt
        if (gatt == null || !temPermissoesNecessarias()) {
            onError("Aparelho não conectado via Bluetooth.")
            return
        }

        val service = gatt.getService(SERVICE_UUID)
        val charCfg = service?.getCharacteristic(CHAR_CONFIG_UUID)
        if (charCfg == null) {
            onError("Característica de configuração Wi-Fi não disponível.")
            return
        }

        // Formatação rigorosa exigida pelo Firmware v11 (pin como String com aspas)
        val jsonPayload = JSONObject().apply {
            put("pin", pin.trim())
            put("ssid", ssid.trim())
            put("pass", pass)
        }.toString()

        Log.d(TAG, "Enviando payload BLE: $jsonPayload")
        val bytes = jsonPayload.toByteArray(Charsets.UTF_8)

        try {
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(charCfg, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0
            } else {
                @Suppress("DEPRECATION")
                charCfg.value = bytes
                @Suppress("DEPRECATION")
                charCfg.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(charCfg)
            }

            if (ok) {
                // Dá 500ms para o ESP32 processar o JSON, gravar na NVS e mudar o status
                mainHandler.postDelayed({
                    verificarStatusAposEnvio(gatt, onSucesso, onError)
                }, 500)
            } else {
                onError("Falha na transmissão do pacote de dados BLE.")
            }
        } catch (e: Exception) {
            onError("Erro ao gravar dados no ESP32: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun verificarStatusAposEnvio(
        gatt: BluetoothGatt,
        onSucesso: () -> Unit,
        onError: (String) -> Unit
    ) {
        val service = gatt.getService(SERVICE_UUID)
        val charStatus = service?.getCharacteristic(CHAR_STATUS_UUID)
        if (charStatus == null) {
            // Se a característica não existir, assume sucesso já que o write passou
            onSucesso()
            return
        }

        try {
            val lendo = gatt.readCharacteristic(charStatus)
            if (lendo) {
                // Aguarda leitura
                mainHandler.postDelayed({
                    val statusStr = charStatus.getStringValue(0) ?: String(charStatus.value ?: byteArrayOf(), Charsets.UTF_8)
                    Log.d(TAG, "Status do ESP32 após envio: '$statusStr'")
                    when (statusStr.trim()) {
                        "ok" -> onSucesso()
                        "pin_errado" -> onError("PIN incorreto! Verifique o número de 6 dígitos no visor OLED.")
                        "bloqueado" -> onError("Aparelho bloqueado por 10s após tentativas incorretas.")
                        "dados_invalidos" -> onError("SSID ou senha inválidos.")
                        "chave_invalida" -> onError("Chave de segurança rejeitada pelo ESP32.")
                        else -> {
                            // Se o ESP32 já reiniciou rápido, pode não responder mais via BLE.
                            // Como o envio foi completado, consideramos sucesso para a etapa Wi-Fi!
                            onSucesso()
                        }
                    }
                }, 400)
            } else {
                onSucesso()
            }
        } catch (e: Exception) {
            onSucesso()
        }
    }

    fun desconectar() {
        try {
            activeGatt?.let { gatt ->
                if (temPermissoesNecessarias()) {
                    gatt.disconnect()
                    gatt.close()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao desconectar GATT: ${e.message}")
        }
        activeGatt = null
    }
}
