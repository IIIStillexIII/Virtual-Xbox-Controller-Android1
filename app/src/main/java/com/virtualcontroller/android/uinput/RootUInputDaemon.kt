package com.virtualcontroller.android.uinput

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import com.virtualcontroller.android.ipc.IInputEventListener
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Root-side uinput owner. Started with app_process by RootUInputBackend.
 *
 * The daemon deliberately owns the native file descriptors: they remain alive while the
 * client is connected, and are released when the client disconnects or sends DESTROY.
 * Wire protocol is private to this app; every request has a fixed opcode and fixed payload.
 */
class RootUInputDaemon private constructor() {
    companion object {
        private const val TAG = "RootUInputDaemon"
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
        @Volatile private var inputOutput: DataOutputStream? = null

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                // Force JNI load before announcing that the daemon is ready.
                UInputNative.canOpen()
                LocalServerSocket(INPUT_SOCKET_NAME).use { inputServer ->
                    Thread({
                        try {
                            inputServer.accept().use { inputClient ->
                                val stream = DataOutputStream(inputClient.outputStream)
                                inputOutput = stream
                                stream.writeInt(MAGIC)
                                stream.flush()
                                while (true) Thread.sleep(1000)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Root input stream closed: " + t.message)
                        } finally {
                            inputOutput = null
                        }
                    }, "root-evdev-stream").apply { isDaemon = true; start() }
                    LocalServerSocket(SOCKET_NAME).use { server ->
                    Log.i(TAG, "Root uinput daemon listening")
                    while (true) {
                        val client = try { server.accept() } catch (t: Throwable) {
                            Log.e(TAG, "accept failed", t)
                            break
                        }
                        val keepRunning = handleClient(client)
                        if (!keepRunning) break
                    }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Root daemon stopped", t)
            } finally {
                try { UInputNative.stopEvdevReader() } catch (_: Throwable) {}
                try { UInputNative.destroy() } catch (_: Throwable) {}
            }
        }

        private class RootFrameListener : IInputEventListener.Stub() {
            override fun onInputFrame(report: ByteArray?) {
                if (report == null) return
                try {
                    synchronized(this) {
                        val stream = inputOutput ?: return
                        stream.writeInt(report.size)
                        stream.write(report)
                        stream.flush()
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not forward evdev frame: " + t.message)
                }
            }
        }

        private fun readCommand(input: DataInputStream): List<String> {
            val count = input.readInt()
            require(count in 1..32) { "Invalid shell argument count: $count" }
            return List(count) { input.readUTF() }
        }

        private fun handleClient(socket: LocalSocket): Boolean {
            socket.use { client ->
                val input = DataInputStream(client.inputStream)
                val output = DataOutputStream(client.outputStream)
                try {
                    output.writeInt(MAGIC)
                    output.flush()
                    while (true) {
                        when (val op = input.readInt()) {
                            OP_PING -> { output.writeBoolean(true); output.flush() }
                            OP_CAN_OPEN -> {
                                output.writeBoolean(UInputNative.canOpen())
                                output.flush()
                            }
                            OP_CREATE -> {
                                output.writeBoolean(UInputNative.createDevice(input.readInt()))
                                output.flush()
                            }
                            OP_SEND_FRAME -> {
                                val values = IntArray(9) { input.readInt() }
                                UInputNative.sendFrame(
                                    values[0], values[1], values[2], values[3], values[4],
                                    values[5], values[6], values[7], values[8]
                                )
                                output.writeBoolean(true)
                                output.flush()
                            }
                            OP_SEND_MOUSE -> {
                                UInputNative.sendMouseFrame(
                                    input.readInt(), input.readInt(), input.readInt(), input.readInt()
                                )
                                output.writeBoolean(true)
                                output.flush()
                            }
                            OP_POLL_FF -> {
                                val event = UInputNative.pollFFEvent()
                                if (event == null) {
                                    output.writeInt(-1)
                                } else {
                                    output.writeInt(event.size)
                                    event.forEach { output.writeInt(it) }
                                }
                                output.flush()
                            }
                            OP_START_EVDEV -> {
                                val filter = input.readUTF()
                                UInputNative.startEvdevReader(RootFrameListener(), filter)
                                output.writeBoolean(true)
                                output.flush()
                            }
                            OP_STOP_EVDEV -> {
                                UInputNative.stopEvdevReader()
                                output.writeBoolean(true)
                                output.flush()
                            }
                            OP_RUN_SHELL -> {
                                val command = readCommand(input)
                                val exitCode = ProcessBuilder(command).redirectErrorStream(true).start().let { process ->
                                    process.inputStream.bufferedReader().use { it.readText() }
                                    process.waitFor()
                                }
                                output.writeInt(exitCode)
                                output.flush()
                            }
                            OP_RUN_SHELL_OUTPUT -> {
                                val command = readCommand(input)
                                val result = ProcessBuilder(command).redirectErrorStream(true).start().let { process ->
                                    val text = process.inputStream.bufferedReader().use { it.readText() }
                                    process.waitFor()
                                    text.trim()
                                }
                                output.writeUTF(result.take(60000))
                                output.flush()
                            }
                            OP_DESTROY -> {
                                try { UInputNative.stopEvdevReader() } catch (_: Throwable) {}
                                UInputNative.destroy()
                                output.writeBoolean(true)
                                output.flush()
                                return false
                            }
                            else -> throw IllegalArgumentException("Unknown root IPC opcode: $op")
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Root client disconnected: ${t.message}")
                }
            }
            // A disconnected app must not leave a virtual controller attached.
            try { UInputNative.stopEvdevReader() } catch (_: Throwable) {}
            try { UInputNative.destroy() } catch (_: Throwable) {}
            return true
        }
    }
}
