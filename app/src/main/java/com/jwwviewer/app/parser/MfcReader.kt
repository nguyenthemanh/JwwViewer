package com.jwwviewer.app.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Little-endian reader for MFC CArchive-style JWW payloads.
 */
class MfcReader(data: ByteArray) {
    private val buf: ByteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    private val cp932: Charset = Charset.forName("MS932")

    /** Shared MFC class/object numbering (1-based). */
    private val classMap = HashMap<Int, String>()
    private var nextIndex = 1

    val position: Int get() = buf.position()
    val remaining: Int get() = buf.remaining()

    fun readByte(): Int = buf.get().toInt() and 0xFF

    fun readWord(): Int = buf.short.toInt() and 0xFFFF

    fun readDword(): Int = buf.int

    fun readUDword(): Long = buf.int.toLong() and 0xFFFFFFFFL

    fun readDouble(): Double = buf.double

    fun readBytes(n: Int): ByteArray {
        require(n >= 0 && n <= buf.remaining()) { "need $n bytes, have ${buf.remaining()} at $position" }
        val out = ByteArray(n)
        buf.get(out)
        return out
    }

    fun readCString(): String {
        val b = readByte()
        if (b == 0) return ""
        if (b != 0xFF) {
            return String(readBytes(b), cp932)
        }
        val w = readWord()
        when (w) {
            0xFFFE -> {
                val cb = readByte()
                val n = when {
                    cb < 0xFF -> cb
                    else -> {
                        val n2 = readWord()
                        if (n2 == 0xFFFF) readDword() else n2
                    }
                }
                return String(readBytes(n * 2), Charsets.UTF_16LE)
            }
            0xFFFF -> {
                val n = readDword()
                return String(readBytes(n), cp932)
            }
            else -> return String(readBytes(w), cp932)
        }
    }

    fun readCount(): Int {
        val w = readWord()
        return if (w == 0xFFFF) readDword() else w
    }

    /**
     * Reads an MFC object tag. Returns class name, or empty string for null /
     * already-serialized object references (no payload follows).
     */
    fun readObjectTag(): String {
        val w = readWord()
        if (w == 0xFFFF) {
            readWord() // schema
            val nameLen = readWord()
            val name = String(readBytes(nameLen), Charsets.US_ASCII)
            classMap[nextIndex] = name
            nextIndex += 1
            nextIndex += 1 // object also consumes a slot
            return name
        }
        val dw = if (w == 0x7FFF) {
            readDword()
        } else if ((w and 0x8000) != 0) {
            0x80000000.toInt() or (w and 0x7FFF)
        } else {
            w
        }
        if ((dw and 0x80000000.toInt()) == 0) {
            nextIndex += 1
            return ""
        }
        val cid = dw and 0x7FFFFFFF
        val name = classMap[cid]
            ?: error("Unknown MFC class id $cid at $position")
        nextIndex += 1
        return name
    }
}
