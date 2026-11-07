package com.fl0w.speye.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Pure-Kotlin Animated WebP parser (`RIFF....WEBP` with `VP8X` + `ANIM` + `ANMF` chunks)
 * and `GIF89a` animated GIF encoder (with `NETSCAPE2.0` looping, Graphic Control Extension
 * for frame timing + transparency, 256-color palette quantization, and LZW compression).
 *
 * Used by [ImageUtils.exportToGallery] so animated WebP stickers exported to `Pictures/Speye`
 * are saved as universal `.gif` (`image/gif`) files that animate natively in all Android OEM
 * Gallery apps (Samsung Gallery, Google Photos, MIUI Gallery, AOSP Gallery) across API 24–37.
 */
object AnimatedWebpConverter {

    data class WebpFrameInfo(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val durationMs: Int,
        val disposeToBackground: Boolean,
        val doNotBlend: Boolean,
        val singleFrameWebpBytes: ByteArray
    )

    data class ParsedAnimatedWebp(
        val canvasWidth: Int,
        val canvasHeight: Int,
        val backgroundColorArgb: Int,
        val loopCount: Int,
        val frames: List<WebpFrameInfo>
    )

    data class GifFrameData(
        val width: Int,
        val height: Int,
        val delayMs: Int,
        val argbPixels: IntArray
    )

    fun isAnimatedWebp(bytes: ByteArray): Boolean {
        if (bytes.size < 16) return false
        if (bytes[0] != 0x52.toByte() || bytes[1] != 0x49.toByte() ||
            bytes[2] != 0x46.toByte() || bytes[3] != 0x46.toByte()
        ) return false
        if (bytes[8] != 0x57.toByte() || bytes[9] != 0x45.toByte() ||
            bytes[10] != 0x42.toByte() || bytes[11] != 0x50.toByte()
        ) return false

        // Check VP8X animation flag at offset 20 (bit 1 = 0x02)
        if (bytes.size >= 21 && bytes[12] == 0x56.toByte() && bytes[13] == 0x50.toByte() &&
            bytes[14] == 0x38.toByte() && bytes[15] == 0x58.toByte()
        ) {
            val flags = bytes[20].toInt() and 0xFF
            if ((flags and 0x02) != 0) return true
        }

        // Fallback scan for ANIM or ANMF chunk
        val limit = minOf(bytes.size - 4, 2048)
        for (i in 12..limit) {
            val b0 = bytes[i]
            val b1 = bytes[i + 1]
            val b2 = bytes[i + 2]
            val b3 = bytes[i + 3]
            if (b0 == 0x41.toByte() && b1 == 0x4E.toByte() &&
                ((b2 == 0x49.toByte() && b3 == 0x4D.toByte()) || (b2 == 0x4D.toByte() && b3 == 0x46.toByte()))
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Parses an animated WebP container into canvas metadata and individual single-frame WebP byte arrays.
     */
    fun parseAnimatedWebp(bytes: ByteArray): ParsedAnimatedWebp? {
        if (!isAnimatedWebp(bytes)) return null
        var canvasWidth = 0
        var canvasHeight = 0
        var bgColor = 0
        var loopCount = 0
        val frames = mutableListOf<WebpFrameInfo>()

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val fourCC = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = readLeInt32(bytes, offset + 4)
            val dataStart = offset + 8
            if (chunkSize < 0 || dataStart + chunkSize > bytes.size) break

            when (fourCC) {
                "VP8X" -> {
                    if (chunkSize >= 10) {
                        canvasWidth = readLeInt24(bytes, dataStart + 4) + 1
                        canvasHeight = readLeInt24(bytes, dataStart + 7) + 1
                    }
                }
                "ANIM" -> {
                    if (chunkSize >= 6) {
                        // BGRA in WebP spec -> ARGB
                        val b = bytes[dataStart].toInt() and 0xFF
                        val g = bytes[dataStart + 1].toInt() and 0xFF
                        val r = bytes[dataStart + 2].toInt() and 0xFF
                        val a = bytes[dataStart + 3].toInt() and 0xFF
                        bgColor = (a shl 24) or (r shl 16) or (g shl 8) or b
                        loopCount = readLeInt16(bytes, dataStart + 4)
                    }
                }
                "ANMF" -> {
                    if (chunkSize > 16) {
                        val frameX = readLeInt24(bytes, dataStart) * 2
                        val frameY = readLeInt24(bytes, dataStart + 3) * 2
                        val frameW = readLeInt24(bytes, dataStart + 6) + 1
                        val frameH = readLeInt24(bytes, dataStart + 9) + 1
                        val durationMs = readLeInt24(bytes, dataStart + 12)
                        val flags = bytes[dataStart + 15].toInt() and 0xFF
                        val disposeToBg = (flags and 0x01) != 0
                        val doNotBlend = (flags and 0x02) != 0

                        val payloadStart = dataStart + 16
                        val payloadLen = chunkSize - 16
                        val singleWebp = buildStandaloneWebpFrame(
                            bytes = bytes,
                            payloadStart = payloadStart,
                            payloadLen = payloadLen,
                            frameW = frameW,
                            frameH = frameH
                        )
                        if (singleWebp != null) {
                            frames.add(
                                WebpFrameInfo(
                                    x = frameX,
                                    y = frameY,
                                    width = frameW,
                                    height = frameH,
                                    durationMs = if (durationMs <= 10) 80 else durationMs,
                                    disposeToBackground = disposeToBg,
                                    doNotBlend = doNotBlend,
                                    singleFrameWebpBytes = singleWebp
                                )
                            )
                        }
                    }
                }
            }

            // Chunks are padded to even byte boundaries in RIFF
            val paddedSize = chunkSize + (chunkSize and 1)
            offset = dataStart + paddedSize
        }

        if (frames.isEmpty()) return null
        if (canvasWidth <= 0) canvasWidth = frames.maxOf { it.x + it.width }
        if (canvasHeight <= 0) canvasHeight = frames.maxOf { it.y + it.height }

        return ParsedAnimatedWebp(
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            backgroundColorArgb = bgColor,
            loopCount = loopCount,
            frames = frames
        )
    }

    /**
     * Wraps an `ANMF` frame sub-chunk payload (`ALPH` + `VP8 ` or `VP8L`) into a valid standalone
     * single-frame `RIFF....WEBP` byte array decodable by `BitmapFactory.decodeByteArray` on API 24+.
     */
    private fun buildStandaloneWebpFrame(
        bytes: ByteArray,
        payloadStart: Int,
        payloadLen: Int,
        frameW: Int,
        frameH: Int
    ): ByteArray? {
        if (payloadLen < 8 || payloadStart + payloadLen > bytes.size) return null
        val firstChunk = String(bytes, payloadStart, 4, Charsets.US_ASCII)
        val hasAlph = firstChunk == "ALPH"

        val out = ByteArrayOutputStream(payloadLen + 36)
        // Placeholder RIFF header
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeLeInt32(out, 0)
        out.write("WEBP".toByteArray(Charsets.US_ASCII))

        if (hasAlph) {
            // VP8X chunk required when ALPH chunk is present
            out.write("VP8X".toByteArray(Charsets.US_ASCII))
            writeLeInt32(out, 10)
            // Flags: bit 4 (0x10) = Alpha
            out.write(0x10)
            out.write(0)
            out.write(0)
            out.write(0)
            writeLeInt24(out, (frameW - 1).coerceAtLeast(0))
            writeLeInt24(out, (frameH - 1).coerceAtLeast(0))
        }

        out.write(bytes, payloadStart, payloadLen)
        if ((payloadLen and 1) == 1) {
            out.write(0)
        }

        val result = out.toByteArray()
        val riffSize = result.size - 8
        result[4] = (riffSize and 0xFF).toByte()
        result[5] = ((riffSize ushr 8) and 0xFF).toByte()
        result[6] = ((riffSize ushr 16) and 0xFF).toByte()
        result[7] = ((riffSize ushr 24) and 0xFF).toByte()
        return result
    }

    /**
     * Converts animated WebP bytes into animated GIF (`GIF89a`) bytes on Android.
     * Returns null if input is not a multi-frame animated WebP or decoding fails.
     */
    fun convertAnimatedWebpToGif(webpBytes: ByteArray): ByteArray? {
        val parsed = parseAnimatedWebp(webpBytes) ?: return null
        if (parsed.frames.isEmpty()) return null

        val maxDim = 512
        val rawW = parsed.canvasWidth.coerceAtLeast(1)
        val rawH = parsed.canvasHeight.coerceAtLeast(1)
        val scale = if (rawW > maxDim || rawH > maxDim) {
            maxDim.toFloat() / maxOf(rawW, rawH).toFloat()
        } else 1f
        val targetW = (rawW * scale).toInt().coerceAtLeast(1)
        val targetH = (rawH * scale).toInt().coerceAtLeast(1)

        val canvasBitmap = Bitmap.createBitmap(rawW, rawH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        val blendPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        val srcCopyPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
        val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        val gifFrames = mutableListOf<GifFrameData>()
        try {
            for (frame in parsed.frames) {
                val frameBmp = BitmapFactory.decodeByteArray(
                    frame.singleFrameWebpBytes,
                    0,
                    frame.singleFrameWebpBytes.size
                ) ?: continue

                try {
                    if (frame.doNotBlend) {
                        canvas.drawRect(
                            frame.x.toFloat(),
                            frame.y.toFloat(),
                            (frame.x + frame.width).toFloat(),
                            (frame.y + frame.height).toFloat(),
                            clearPaint
                        )
                        canvas.drawBitmap(frameBmp, frame.x.toFloat(), frame.y.toFloat(), srcCopyPaint)
                    } else {
                        canvas.drawBitmap(frameBmp, frame.x.toFloat(), frame.y.toFloat(), blendPaint)
                    }
                } finally {
                    frameBmp.recycle()
                }

                val scaledBmp = if (targetW != rawW || targetH != rawH) {
                    Bitmap.createScaledBitmap(canvasBitmap, targetW, targetH, true)
                } else {
                    canvasBitmap
                }
                val pixels = IntArray(targetW * targetH)
                scaledBmp.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
                if (scaledBmp !== canvasBitmap) {
                    scaledBmp.recycle()
                }

                gifFrames.add(
                    GifFrameData(
                        width = targetW,
                        height = targetH,
                        delayMs = frame.durationMs,
                        argbPixels = pixels
                    )
                )

                if (frame.disposeToBackground) {
                    canvas.drawRect(
                        frame.x.toFloat(),
                        frame.y.toFloat(),
                        (frame.x + frame.width).toFloat(),
                        (frame.y + frame.height).toFloat(),
                        clearPaint
                    )
                }
            }
        } finally {
            canvasBitmap.recycle()
        }

        if (gifFrames.isEmpty()) return null
        val out = ByteArrayOutputStream()
        encodeGif(gifFrames, parsed.loopCount, out)
        return out.toByteArray()
    }

    /**
     * Pure-Kotlin GIF89a encoder supporting transparency, variable frame delays, and infinite loop.
     * Accessible directly in host JVM unit tests without requiring Android `Bitmap`.
     */
    fun encodeGif(frames: List<GifFrameData>, loopCount: Int = 0, out: OutputStream) {
        if (frames.isEmpty()) return
        val width = frames[0].width
        val height = frames[0].height

        // Build 256-color palette:
        // Index 0..251: 6x7x6 uniform RGB cube (252 colors)
        // Index 252..254: grayscale / white / black extras
        // Index 255: reserved for transparent pixels (alpha < 128)
        val palette = ByteArray(256 * 3)
        var idx = 0
        for (r in 0 until 6) {
            for (g in 0 until 7) {
                for (b in 0 until 6) {
                    palette[idx * 3] = ((r * 255) / 5).toByte()
                    palette[idx * 3 + 1] = ((g * 255) / 6).toByte()
                    palette[idx * 3 + 2] = ((b * 255) / 5).toByte()
                    idx++
                }
            }
        }
        // Fill remaining up to 254 with mid-grays
        while (idx < 255) {
            val gray = ((idx - 251) * 64).coerceIn(0, 255).toByte()
            palette[idx * 3] = gray
            palette[idx * 3 + 1] = gray
            palette[idx * 3 + 2] = gray
            idx++
        }
        val transparentIndex = 255

        // 1. Header: GIF89a
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))

        // 2. Logical Screen Descriptor
        writeLeInt16(out, width)
        writeLeInt16(out, height)
        // Packed: Global Color Table Flag (1) | Color Resolution (7 -> 8 bits) | Sort (0) | Size of GCT (7 -> 256 entries) = 0xF7
        out.write(0xF7)
        out.write(transparentIndex) // Background color index
        out.write(0) // Pixel aspect ratio

        // 3. Global Color Table (768 bytes)
        out.write(palette)

        // 4. NETSCAPE2.0 Application Extension for looping
        out.write(0x21) // Extension Introducer
        out.write(0xFF) // Application Extension Label
        out.write(11)   // Block Size
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)    // Sub-block size
        out.write(1)    // Sub-block ID
        writeLeInt16(out, loopCount) // 0 = infinite loop
        out.write(0)    // Block Terminator

        val indexedPixels = ByteArray(width * height)

        for (frame in frames) {
            // 5. Graphic Control Extension
            out.write(0x21) // Extension Introducer
            out.write(0xF9) // Graphic Control Label
            out.write(4)    // Byte Size
            // Packed: Disposal Method = 2 (Restore to background color -> 0x08) | Transparent Color Flag = 1 (0x01) => 0x09
            out.write(0x09)
            val delayCentiseconds = ((frame.delayMs + 5) / 10).coerceAtLeast(2)
            writeLeInt16(out, delayCentiseconds)
            out.write(transparentIndex)
            out.write(0) // Block Terminator

            // 6. Image Descriptor
            out.write(0x2C) // Image Separator
            writeLeInt16(out, 0) // Left
            writeLeInt16(out, 0) // Top
            writeLeInt16(out, width)
            writeLeInt16(out, height)
            out.write(0x00) // No Local Color Table, Not Interlaced

            // Map ARGB pixels to palette indices
            val argb = frame.argbPixels
            val count = minOf(argb.size, indexedPixels.size)
            for (i in 0 until count) {
                val c = argb[i]
                val a = (c ushr 24) and 0xFF
                if (a < 128) {
                    indexedPixels[i] = transparentIndex.toByte()
                } else {
                    val r = (c ushr 16) and 0xFF
                    val g = (c ushr 8) and 0xFF
                    val b = c and 0xFF
                    val rIdx = (r * 5 + 127) / 255
                    val gIdx = (g * 6 + 127) / 255
                    val bIdx = (b * 5 + 127) / 255
                    val colorIndex = (rIdx * 42) + (gIdx * 6) + bIdx
                    indexedPixels[i] = colorIndex.toByte()
                }
            }

            // 7. LZW Compressed Image Data
            lzwEncode(indexedPixels, count, 8, out)
        }

        // 8. GIF Trailer
        out.write(0x3B)
    }

    private fun lzwEncode(pixels: ByteArray, length: Int, minCodeSize: Int, out: OutputStream) {
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize // 256
        val endOfInfoCode = clearCode + 1 // 257
        var nextCode = clearCode + 2      // 258
        var codeSize = minCodeSize + 1    // 9
        var maxCode = (1 shl codeSize) - 1

        // Hash table for LZW (prefix, suffix) -> code
        val tableSize = 8192
        val hashKeys = IntArray(tableSize) { -1 }
        val hashValues = IntArray(tableSize)

        var bitBuffer = 0
        var bitCount = 0
        val subBlock = ByteArray(255)
        var subBlockLen = 0

        fun flushByte(b: Int) {
            subBlock[subBlockLen++] = b.toByte()
            if (subBlockLen == 255) {
                out.write(255)
                out.write(subBlock, 0, 255)
                subBlockLen = 0
            }
        }

        fun emitCode(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                flushByte(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        fun resetTable() {
            java.util.Arrays.fill(hashKeys, -1)
            codeSize = minCodeSize + 1
            maxCode = (1 shl codeSize) - 1
            nextCode = clearCode + 2
        }

        emitCode(clearCode)
        if (length == 0) {
            emitCode(endOfInfoCode)
            if (bitCount > 0) flushByte(bitBuffer and 0xFF)
            if (subBlockLen > 0) {
                out.write(subBlockLen)
                out.write(subBlock, 0, subBlockLen)
            }
            out.write(0)
            return
        }

        var prefix = pixels[0].toInt() and 0xFF
        for (i in 1 until length) {
            val suffix = pixels[i].toInt() and 0xFF
            val key = (prefix shl 8) or suffix
            var h = ((key * 0x1F1F1F1F) ushr 16) and (tableSize - 1)
            var foundCode = -1
            while (true) {
                val existingKey = hashKeys[h]
                if (existingKey == -1) break
                if (existingKey == key) {
                    foundCode = hashValues[h]
                    break
                }
                h = (h + 1) and (tableSize - 1)
            }

            if (foundCode != -1) {
                prefix = foundCode
            } else {
                emitCode(prefix)
                if (nextCode < 4096) {
                    hashKeys[h] = key
                    hashValues[h] = nextCode
                    if (nextCode > maxCode && codeSize < 12) {
                        codeSize++
                        maxCode = (1 shl codeSize) - 1
                    }
                    nextCode++
                } else {
                    emitCode(clearCode)
                    resetTable()
                }
                prefix = suffix
            }
        }

        emitCode(prefix)
        emitCode(endOfInfoCode)
        if (bitCount > 0) {
            flushByte(bitBuffer and 0xFF)
        }
        if (subBlockLen > 0) {
            out.write(subBlockLen)
            out.write(subBlock, 0, subBlockLen)
        }
        out.write(0) // Block terminator
    }

    private fun readLeInt16(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readLeInt24(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16)
    }

    private fun readLeInt32(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeLeInt16(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun writeLeInt24(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
    }

    private fun writeLeInt32(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }
}
