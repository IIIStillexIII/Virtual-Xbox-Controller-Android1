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
        private const val MAGIC = 0x56584331
        private const val OP_PING = 1
        private const val OP_CAN_OPEN = 2
        private const val OP_CREATE = 3
        private const val OP_SEND_FRAME = 4
        private const val OP_SEND_MOUSE = 5
        private const val OP_POLL_FF = 6
        private const val OP_DESTROY = 7

        private fun shellQuote(value: String): String =
            "'" + value.replace("'", "'\\''") + "'"
    }

    private var process: Process? = null
    private var socket: LocalSocket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

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
            repeat(50) {
                if (process?.isAlive != true) return@repeat
                try {
                    val candidate = LocalSocket()
                    candidate.connect(
                        LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT),
                        300
                    )
                    connected = candidate
                    return@repeat
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
            requestBoolean(OP_PING)
        } catch (t: Throwable) {
            Log.w(TAG, "Root backend start failed: ${t.message}")
            closeConnection()
            false
        }
    }

    val isReady: Boolean
        @Synchronized get() = socket?.isConnected == true && process?.isAlive == true

    @Synchronized
    fun canOpen(): Boolean = requestBoolean(OP_CAN_OPEN)

    @Synchronized
    fun createDevice(profileId: Int): Boolean = try {
        output?.writeInt(OP_CREATE) ?: return false
        output?.writeInt(profileId) ?: return false
        output?.flush()
        input?.readBoolean() ?: false
    } catch (t: Throwable) {
        Log.w(TAG, "createDevice failed: ${t.message}")
        false
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
    fun pollForceFeedback(): IntArray? = try {
        output?.writeInt(OP_POLL_FF) ?: return null
        output?.flush()
        val size = input?.readInt() ?: return null
        if (size < 0) null else IntArray(size) { input!!.readInt() }
    } catch (t: Throwable) {
        Log.w(TAG, "pollForceFeedback failed: ${t.message}")
        null
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

    private fun requestBoolean(op: Int): Boolean = try {
        output?.writeInt(op) ?: return false
        output?.flush()
        input?.readBoolean() ?: false
    } catch (t: Throwable) {
        Log.w(TAG, "request $op failed: ${t.message}")
        false
    }

    private fun closeConnection() {
        try { socket?.close() } catch (_: Throwable) {}
        socket = null
        input = null
        output = null
        try { process?.destroy() } catch (_: Throwable) {}
        process = null
    }
}
