package com.virtualcontroller.android.uinput

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * App-side client for RootUInputDaemon. The root process owns native uinput descriptors;
 * this class only sends compact frame messages over an abstract local socket.
 */
class RootUInputBackend(private val context: Context) {
    companion object {
        private const val TAG = "RootUInputBackend"
        private const val SOCKET_NAME = "virtual-controller-root-uinput"
        private const val INPUT_SOCKET_NAME = "virtual-controller-root-input"
        private const val MAGIC = 0x56584331
        private const val OP_PING = 1
        private const val OP_CAN_OPEN = 2
        private const val OP_CREATE = 3
        private const val OP_SEND_FRAME = 4
        private const val OP_SEND_MOUSE = 5
        private const val OP_POLL_FF = 6
        private const val OP_DESTROY = 7
        private const val OP_START_EVDEV = 8
        private const val OP_STOP_EVDEV = 9
        private const val OP_RUN_SHELL = 10
        private const val OP_RUN_SHELL_OUTPUT = 11

        private fun shellQuote(value: String): String =
            "'" + value.replace("'", "'\\''") + "'"
    }

    private var process: Process? = null
    private var socket: LocalSocket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var inputSocket: LocalSocket? = null
    @Volatile private var inputListener: ((ByteArray) -> Unit)? = null
    @Volatile private var inputReaderRunning = false
    @Volatile private var inputReaderFailed = false

    @Synchronized
    fun start(): Boolean {
        if (isReady) return true
        closeConnection()
        return try {
            val app = context.applicationInfo
            val command = "CLASSPATH=${shellQuote(app.sourceDir)} " +
                "LD_LIBRARY_PATH=${shellQuote(app.nativeLibraryDir)} " +
                "app_process /system/bin com.virtualcontroller.android.uinput.RootUInputDaemon"
            process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()

            // Drain daemon logs so the su pipe cannot fill and block the process.
            Thread({
                try {
                    process?.inputStream?.bufferedReader()?.useLines { lines ->
                        lines.forEach { Log.i(TAG, "root: $it") }
                    }
                } catch (_: Throwable) {}
            }, "root-uinput-log").apply { isDaemon = true; start() }

            var connected: LocalSocket? = null
            for (attempt in 0 until 50) {
                if (process?.isAlive != true) break
                try {
                    val candidate = LocalSocket()
                    candidate.connect(
                        LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT),
                        300
                    )
                    connected = candidate
                    break
                } catch (_: Throwable) {
                    Thread.sleep(100)
                }
            }
            val client = connected ?: run {
                Log.w(TAG, "Root daemon did not open its local socket")
                closeConnection()
                return false
            }
            val dataIn = DataInputStream(client.inputStream)
            val dataOut = DataOutputStream(client.outputStream)
            if (dataIn.readInt() != MAGIC) {
                client.close()
                closeConnection()
                return false
            }
            socket = client
            input = dataIn
            output = dataOut
            if (!requestBoolean(OP_PING)) return false
            val inputClient = LocalSocket()
            inputClient.connect(LocalSocketAddress(INPUT_SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT), 1000)
            val inputStream = DataInputStream(inputClient.inputStream)
            if (inputStream.readInt() != MAGIC) {
                inputClient.close()
                closeConnection()
                return false
            }
            inputSocket = inputClient
            inputReaderFailed = false
            inputReaderRunning = true
            Thread({
                try {
                    while (inputReaderRunning) {
                        val size = inputStream.readInt()
                        if (size !in 1..256) break
                        val report = ByteArray(size)
                        inputStream.readFully(report)
                        inputListener?.invoke(report)
                    }
                } catch (t: Throwable) {
                    if (inputReaderRunning) Log.w(TAG, "Root input stream ended: " + t.message)
                } finally {
                    if (inputReaderRunning) {
                        inputReaderFailed = true
                        Log.e(TAG, "Root input socket closed unexpectedly")
                    }
                }
            }, "root-evdev-input").apply { isDaemon = true; start() }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Root backend start failed: ${t.message}")
            closeConnection()
            false
        }
    }

    val isReady: Boolean
        @Synchronized get() = socket?.isConnected == true && process?.isAlive == true &&
            inputSocket?.isConnected == true && !inputReaderFailed

    @Synchronized
    fun canOpen(): Boolean = requestBoolean(OP_CAN_OPEN)

    @Synchronized
    fun createDevice(profileId: Int): Boolean {
        return try {
            val out = output ?: return false
            val inp = input ?: return false
            out.writeInt(OP_CREATE)
            out.writeInt(profileId)
            out.flush()
            inp.readBoolean()
        } catch (t: Throwable) {
            Log.w(TAG, "createDevice failed: ${t.message}")
            false
        }
    }

    @Synchronized
    fun sendFrame(
        buttons: Int, leftX: Int, leftY: Int, rightX: Int, rightY: Int,
        leftTrigger: Int, rightTrigger: Int, dpadX: Int, dpadY: Int
    ) {
        try {
            val out = output ?: return
            out.writeInt(OP_SEND_FRAME)
            intArrayOf(buttons, leftX, leftY, rightX, rightY, leftTrigger, rightTrigger, dpadX, dpadY)
                .forEach { out.writeInt(it) }
            out.flush()
            input?.readBoolean()
        } catch (t: Throwable) {
            Log.w(TAG, "sendFrame failed: ${t.message}")
        }
    }

    @Synchronized
    fun sendMouseFrame(relX: Int, relY: Int, scrollY: Int, keys: Int) {
        try {
            val out = output ?: return
            out.writeInt(OP_SEND_MOUSE)
            out.writeInt(relX)
            out.writeInt(relY)
            out.writeInt(scrollY)
            out.writeInt(keys)
            out.flush()
            input?.readBoolean()
        } catch (t: Throwable) {
            Log.w(TAG, "sendMouseFrame failed: ${t.message}")
        }
    }

    @Synchronized
    fun pollForceFeedback(): IntArray? {
        return try {
            val out = output ?: return null
            val inp = input ?: return null
            out.writeInt(OP_POLL_FF)
            out.flush()
            val size = inp.readInt()
            if (size < 0) null else IntArray(size) { inp.readInt() }
        } catch (t: Throwable) {
            Log.w(TAG, "pollForceFeedback failed: ${t.message}")
            null
        }
    }

    @Synchronized
    fun startEvdevReader(deviceFilter: String?, listener: (ByteArray) -> Unit): Boolean {
        return try {
            inputListener = listener
            val out = output ?: return false
            val inp = input ?: return false
            out.writeInt(OP_START_EVDEV)
            out.writeUTF(deviceFilter ?: "ANY")
            out.flush()
            inp.readBoolean()
        } catch (t: Throwable) {
            Log.w(TAG, "startEvdevReader failed: " + t.message)
            false
        }
    }

    @Synchronized
    fun stopEvdevReader() {
        try {
            val out = output ?: return
            out.writeInt(OP_STOP_EVDEV)
            out.flush()
            input?.readBoolean()
        } catch (t: Throwable) {
            Log.w(TAG, "stopEvdevReader failed: " + t.message)
        } finally {
            inputListener = null
        }
    }
    @Synchronized
    fun runShellCommand(command: Array<String>): Int {
        return try {
            val out = output ?: return -1
            val inp = input ?: return -1
            require(command.isNotEmpty() && command.size <= 32)
            out.writeInt(OP_RUN_SHELL)
            out.writeInt(command.size)
            command.forEach { out.writeUTF(it) }
            out.flush()
            inp.readInt()
        } catch (t: Throwable) {
            Log.w(TAG, "runShellCommand failed: ${t.message}")
            -1
        }
    }

    @Synchronized
    fun runShellCommandForOutput(command: Array<String>): String? {
        return try {
            val out = output ?: return null
            val inp = input ?: return null
            require(command.isNotEmpty() && command.size <= 32)
            out.writeInt(OP_RUN_SHELL_OUTPUT)
            out.writeInt(command.size)
            command.forEach { out.writeUTF(it) }
            out.flush()
            inp.readUTF()
        } catch (t: Throwable) {
            Log.w(TAG, "runShellCommandForOutput failed: ${t.message}")
            null
        }
    }

    @Synchronized
    fun close() {
        try {
            output?.writeInt(OP_DESTROY)
            output?.flush()
            input?.readBoolean()
        } catch (_: Throwable) {}
        closeConnection()
    }

    private fun requestBoolean(op: Int): Boolean {
        return try {
            val out = output ?: return false
            val inp = input ?: return false
            out.writeInt(op)
            out.flush()
            inp.readBoolean()
        } catch (t: Throwable) {
            Log.w(TAG, "request $op failed: ${t.message}")
            false
        }
    }

    private fun closeConnection() {
        inputReaderRunning = false
        inputListener = null
        try { inputSocket?.close() } catch (_: Throwable) {}
        inputSocket = null
        try { socket?.close() } catch (_: Throwable) {}
        socket = null
        input = null
        output = null
        try { process?.destroy() } catch (_: Throwable) {}
        process = null
    }
}
