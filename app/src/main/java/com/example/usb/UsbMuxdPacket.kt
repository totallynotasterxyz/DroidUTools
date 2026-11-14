// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.usb

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Usbmuxd Packet representation conforming to the binary Usbmuxd protocol.
 * Packet format:
 *   uint32 length   (total length including this 16-byte header)
 *   uint32 version  (always 1)
 *   uint32 type     (message type)
 *   uint32 tag      (sequence / request tag)
 *   payload bytes
 */
data class UsbMuxdPacket(
    val length: Int,
    val version: Int = VERSION_1,
    val type: Int,
    val tag: Int,
    val payload: ByteArray
) {
    companion object {
        const val HEADER_SIZE = 16
        const val VERSION_1 = 1

        // Usbmuxd message types
        const val TYPE_RESULT = 1
        const val TYPE_CONNECT = 2
        const val TYPE_LISTEN = 3
        const val TYPE_DEVICE_ADD = 4
        const val TYPE_DEVICE_REMOVE = 5
        const val TYPE_PLIST = 8

        fun createPlistPacket(tag: Int, plistXml: String): UsbMuxdPacket {
            val payload = plistXml.toByteArray(Charsets.UTF_8)
            val length = HEADER_SIZE + payload.size
            return UsbMuxdPacket(
                length = length,
                version = VERSION_1,
                type = TYPE_PLIST,
                tag = tag,
                payload = payload
            )
        }

        fun readFromStream(inputStream: InputStream): UsbMuxdPacket? {
            val headerBytes = ByteArray(HEADER_SIZE)
            var read = 0
            while (read < HEADER_SIZE) {
                val r = inputStream.read(headerBytes, read, HEADER_SIZE - read)
                if (r == -1) return null
                read += r
            }

            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val length = buffer.int
            val version = buffer.int
            val type = buffer.int
            val tag = buffer.int

            val payloadLength = length - HEADER_SIZE
            val payload = if (payloadLength > 0) {
                val payloadBytes = ByteArray(payloadLength)
                var payloadRead = 0
                while (payloadRead < payloadLength) {
                    val r = inputStream.read(payloadBytes, payloadRead, payloadLength - payloadRead)
                    if (r == -1) break
                    payloadRead += r
                }
                payloadBytes
            } else {
                ByteArray(0)
            }

            return UsbMuxdPacket(length, version, type, tag, payload)
        }
    }

    fun writeToStream(outputStream: OutputStream) {
        val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(length)
        buffer.putInt(version)
        buffer.putInt(type)
        buffer.putInt(tag)
        outputStream.write(buffer.array())
        if (payload.isNotEmpty()) {
            outputStream.write(payload)
        }
        outputStream.flush()
    }

    fun getPayloadString(): String {
        return String(payload, Charsets.UTF_8)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UsbMuxdPacket
        if (length != other.length) return false
        if (version != other.version) return false
        if (type != other.type) return false
        if (tag != other.tag) return false
        return payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = length
        result = 31 * result + version
        result = 31 * result + type
        result = 31 * result + tag
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
