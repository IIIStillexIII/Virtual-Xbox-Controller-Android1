package com.virtualcontroller.android.uinput

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
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
        private const val MAGIC = 0x56584331

        private const val OP_PING = 1
        private const val OP_CAN_OPEN = 2
        private const val OP_CREATE = 3
        private const val OP_SEND_FRAME = 4
        private const val OP_SEND_MOUSE = 5
        private const val OP_POLL_FF = 6
        private const val OP_DESTROY = 7

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                // Force JNI load before announcing that the daemon is ready.
                UInputNative.canOpen()
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
            } catch (t: Throwable) {
                Log.e(TAG, "Root daemon stopped", t)
            } finally {
                try { UInputNative.stopEvdevReader() } catch (_: Throwable) {}
                try { UInputNative.destroy() } catch (_: Throwable) {}
            }
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
                                    event.forEach(output::writeInt)
                                }
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
