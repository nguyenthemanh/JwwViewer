package com.jwwviewer.app.parser

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * JWW binary reader (LibreCAD jwwlib + JwwMeasure MFC indexing).
 * Best-effort: keeps entities parsed before the first hard failure.
 */
object JwwParser {

    /** Soft cap for zip member / in-memory payload (zip-bomb / OOM guard). */
    const val MAX_PAYLOAD_BYTES = 80 * 1024 * 1024

    fun parse(bytes: ByteArray): JwwDrawing {
        val data = unwrap(bytes)
        val r = MfcReader(data)
        val header = readHeader(r)
        val elements = ArrayList<JwwElement>(4096)
        val blockDefs = LinkedHashMap<Int, List<JwwElement>>()
        var truncated = false
        try {
            val count = r.readCount()
            repeat(count) {
                val name = r.readObjectTag()
                if (name.isEmpty()) return@repeat
                readNamed(r, name, header.version, elements, store = true)
            }
            if (r.remaining >= 2) {
                val blockCount = r.readCount()
                repeat(blockCount.coerceAtMost(100_000)) {
                    val name = r.readObjectTag()
                    if (name != "CDataList") return@repeat
                    val (num, members) = readDataList(r, header.version)
                    blockDefs[num] = members
                }
            }
        } catch (_: Exception) {
            // Unknown entity / desync: cannot skip payload size → stop, keep prior entities
            truncated = true
        }
        val flat = BlockExpand.flatten(elements, blockDefs)
        return JwwDrawing(
            version = header.version,
            memo = header.memo,
            scales = header.scales,
            writeGroup = header.writeGroup,
            layerStates = header.layerStates,
            layerNames = header.layerNames,
            elements = flat,
            bounds = JwwBounds.from(flat),
            blockDefs = blockDefs,
            truncated = truncated,
        )
    }

    fun readCapped(stream: InputStream, maxBytes: Int = MAX_PAYLOAD_BYTES): ByteArray {
        val out = ByteArrayOutputStream(minOf(maxBytes, 256 * 1024))
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) error("File too large (max ${maxBytes / (1024 * 1024)} MB)")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun unwrap(data: ByteArray): ByteArray {
        if (data.size >= 2 && data[0] == 0x50.toByte() && data[1] == 0x4B.toByte()) {
            ZipInputStream(ByteArrayInputStream(data)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        return readCapped(zip, MAX_PAYLOAD_BYTES)
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return data
    }

    private data class Header(
        val version: Int,
        val memo: String,
        val scales: DoubleArray,
        val writeGroup: Int,
        val layerStates: IntArray,
        val layerNames: Array<String>,
    )

    private fun readHeader(r: MfcReader): Header {
        val sig = String(r.readBytes(8), Charsets.US_ASCII)
        require(sig == "JwwData.") { "Not a JWW file ($sig)" }
        val ver = r.readDword()
        require(ver >= 230) { "Unsupported JWW version $ver" }
        val memo = r.readCString()
        r.readDword() // paper
        val writeGroup = r.readDword().coerceIn(0, 15)
        val scales = DoubleArray(16)
        val layerStates = IntArray(256)
        val layerNames = Array(256) { "" }
        var li = 0
        for (g in 0 until 16) {
            r.readDword()
            r.readDword()
            scales[g] = r.readDouble()
            r.readDword()
            for (l in 0 until 16) {
                layerStates[li] = r.readDword()
                r.readDword()
                li++
            }
        }
        repeat(14) { r.readDword() }
        repeat(5) { r.readDword() }
        r.readDword()
        r.readDword()
        r.readDouble(); r.readDouble(); r.readDouble(); r.readDword()
        r.readDword(); r.readDouble(); r.readDouble(); r.readDouble(); r.readDouble(); r.readDouble()
        li = 0
        for (g in 0 until 16) {
            for (l in 0 until 16) {
                layerNames[li++] = r.readCString()
            }
        }
        repeat(16) { r.readCString() }
        r.readDouble(); r.readDouble(); r.readDword(); r.readDouble()
        if (ver >= 300) {
            r.readDouble(); r.readDouble()
        }
        r.readDword(); r.readDouble(); r.readDouble(); r.readDouble()
        r.readDouble(); r.readDouble(); r.readDouble()
        if (ver >= 300) {
            repeat(8) {
                r.readDouble(); r.readDouble(); r.readDouble(); r.readDword()
            }
            r.readDouble(); r.readDouble(); r.readDouble(); r.readDword()
            r.readDouble(); r.readDouble(); r.readDouble(); r.readDword()
        }
        repeat(10) { r.readDouble() }
        r.readDouble()
        repeat(10) { r.readDword(); r.readDword() }
        repeat(10) { r.readDword(); r.readDword(); r.readDouble() }
        for (i in 2..9) repeat(4) { r.readDword() }
        for (i in 11..15) repeat(5) { r.readDword() }
        for (i in 16..19) repeat(4) { r.readDword() }
        repeat(11) { r.readDword() }
        r.readDword(); r.readDword()
        r.readDword(); r.readDword(); r.readDword()
        r.readDouble(); r.readDouble(); r.readDouble(); r.readDouble(); r.readDouble()
        r.readDouble(); r.readDouble(); r.readDouble(); r.readDouble()
        r.readDword(); r.readDword()
        if (ver >= 420) {
            repeat(257) { r.readDword(); r.readDword() }
            repeat(257) {
                r.readCString(); r.readDword(); r.readDword(); r.readDouble()
            }
            repeat(33) { repeat(4) { r.readDword() } }
            repeat(33) {
                r.readCString(); r.readDword()
                repeat(10) { r.readDouble() }
            }
        }
        repeat(10) {
            r.readDouble(); r.readDouble(); r.readDouble(); r.readDword()
        }
        r.readDouble(); r.readDouble(); r.readDouble(); r.readDword(); r.readDword()
        r.readDouble(); r.readDouble(); r.readDword()
        r.readDouble(); r.readDouble(); r.readDouble()
        r.readDouble(); r.readDouble(); r.readDouble()
        return Header(ver, memo, scales, writeGroup, layerStates, layerNames)
    }

    private fun readAttr(r: MfcReader, ver: Int): JwwAttr {
        val group = r.readDword()
        val pen = r.readByte()
        val color = r.readWord()
        val width = if (ver >= 351) r.readWord() else 1
        val layer = r.readWord()
        val gLayer = r.readWord()
        val flags = r.readWord()
        return JwwAttr(group, pen, color, width, layer, gLayer, flags)
    }

    private fun readTenExtra(r: MfcReader, attr: JwwAttr) {
        r.readDword()
        if (attr.penStyle == 100) {
            r.readDword()
            r.readDouble()
            r.readDouble()
        }
    }

    private fun readSunpou(r: MfcReader, ver: Int, out: MutableList<JwwElement>, store: Boolean) {
        readAttr(r, ver)
        val a1 = readAttr(r, ver)
        val line = JwwLine(a1, r.readDouble(), r.readDouble(), r.readDouble(), r.readDouble())
        if (store) out.add(line)
        val a2 = readAttr(r, ver)
        val x1 = r.readDouble(); val y1 = r.readDouble()
        val x2 = r.readDouble(); val y2 = r.readDouble()
        val style = r.readDword()
        val sx = r.readDouble(); val sy = r.readDouble()
        val gap = r.readDouble(); val ang = r.readDouble()
        val font = r.readCString(); val text = r.readCString()
        if (store) {
            out.add(JwwText(a2, x1, y1, x2, y2, style, sx, sy, gap, ang, font, text))
        }
        if (ver >= 420) {
            r.readWord()
            repeat(2) {
                readAttr(r, ver)
                repeat(4) { r.readDouble() }
            }
            repeat(4) {
                val a3 = readAttr(r, ver)
                r.readDouble(); r.readDouble()
                readTenExtra(r, a3)
            }
        }
    }

    private fun readNamed(
        r: MfcReader,
        name: String,
        ver: Int,
        out: MutableList<JwwElement>,
        store: Boolean,
    ) {
        when (name) {
            "CDataSen" -> {
                val a = readAttr(r, ver)
                val e = JwwLine(a, r.readDouble(), r.readDouble(), r.readDouble(), r.readDouble())
                if (store) out.add(e)
            }
            "CDataEnko" -> {
                val a = readAttr(r, ver)
                val e = JwwCircle(
                    a,
                    r.readDouble(), r.readDouble(), r.readDouble(),
                    r.readDouble(), r.readDouble(), r.readDouble(), r.readDouble(),
                    r.readDword() != 0,
                )
                if (store) out.add(e)
            }
            "CDataTen" -> {
                val a = readAttr(r, ver)
                val x = r.readDouble(); val y = r.readDouble()
                readTenExtra(r, a)
                if (store) out.add(JwwPoint(a, x, y))
            }
            "CDataMoji" -> {
                val a = readAttr(r, ver)
                val x1 = r.readDouble(); val y1 = r.readDouble()
                val x2 = r.readDouble(); val y2 = r.readDouble()
                val style = r.readDword()
                val sx = r.readDouble(); val sy = r.readDouble()
                val gap = r.readDouble(); val ang = r.readDouble()
                val font = r.readCString(); val text = r.readCString()
                if (store) out.add(JwwText(a, x1, y1, x2, y2, style, sx, sy, gap, ang, font, text))
            }
            "CDataSolid" -> {
                val a = readAttr(r, ver)
                val pts = DoubleArray(8) { r.readDouble() }
                val rgb = if (a.color == 10) r.readDword() else null
                if (store) {
                    out.add(
                        JwwSolid(
                            a, pts[0], pts[1], pts[2], pts[3],
                            pts[4], pts[5], pts[6], pts[7], rgb,
                        ),
                    )
                }
            }
            "CDataBlock" -> {
                val a = readAttr(r, ver)
                val e = JwwBlockRef(
                    a, r.readDouble(), r.readDouble(),
                    r.readDouble(), r.readDouble(), r.readDouble(), r.readDword(),
                )
                if (store) out.add(e)
            }
            "CDataSunpou" -> readSunpou(r, ver, out, store)
            else -> error("Unsupported entity $name")
        }
    }

    /** CDataList: definition serial number + member entities. */
    private fun readDataList(r: MfcReader, ver: Int): Pair<Int, List<JwwElement>> {
        readAttr(r, ver)
        val number = r.readDword()
        r.readDword() // referred
        r.readDword() // CTime as DWORD (LibreCAD)
        r.readCString()
        val members = r.readCount()
        val sink = ArrayList<JwwElement>(members.coerceAtMost(4096))
        repeat(members) {
            val name = r.readObjectTag()
            if (name.isEmpty()) return@repeat
            readNamed(r, name, ver, sink, store = true)
        }
        return number to sink
    }
}
