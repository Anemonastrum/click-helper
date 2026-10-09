package com.example.flashsale

import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Small SNTP client. Network samples are diagnostics and an estimate, not proof of
 * the third-party merchant's wall clock or end-to-end checkout latency.
 * Call from a background thread only.
 */
object NtpSync {
    private const val UNIX_DELTA_SECONDS = 2_208_988_800L

    fun collect(host: String = "time.cloudflare.com", attempts: Int = 4): SyncReading {
        val samples = mutableListOf<NtpSample>()
        repeat(attempts) {
            try { samples += query(host) } catch (_: Exception) { /* Try next sample */ }
            if (it < attempts - 1) Thread.sleep(80)
        }
        if (samples.isEmpty()) throw IllegalStateException("NTP unavailable (UDP 123 may be blocked)")
        val sorted = samples.sortedBy { it.roundTripMs }
        val best = sorted.take(minOf(3, sorted.size))
        val offsets = best.map { it.offsetMs }.sorted()
        val rtts = best.map { it.roundTripMs }.sorted()
        val medianOffset = offsets[offsets.size / 2]
        val medianRtt = rtts[rtts.size / 2]
        val deviation = best.map { abs(it.offsetMs - medianOffset) }.sorted()
        return SyncReading(medianOffset, medianRtt, deviation[deviation.size / 2], SystemClock.elapsedRealtimeNanos())
    }

    private fun query(host: String): NtpSample {
        val request = ByteArray(48)
        request[0] = 0x23 // NTPv4 client
        DatagramSocket().use { socket ->
            socket.soTimeout = 1300
            socket.connect(InetAddress.getByName(host), 123)
            val t1Wall = System.currentTimeMillis()
            putTimestamp(request, 40, t1Wall)
            val t1Elapsed = SystemClock.elapsedRealtimeNanos()
            socket.send(DatagramPacket(request, request.size))
            val reply = ByteArray(48)
            val packet = DatagramPacket(reply, reply.size)
            socket.receive(packet)
            val t4Elapsed = SystemClock.elapsedRealtimeNanos()
            if (packet.length < 48) throw IllegalStateException("Short NTP reply")
            val mode = reply[0].toInt() and 7
            val stratum = reply[1].toInt() and 0xff
            if (mode != 4 || stratum !in 1..15) throw IllegalStateException("Invalid NTP reply")
            if (!(24 until 32).all { reply[it] == request[it + 16] })
                throw IllegalStateException("Mismatched NTP request")
            val t2 = readTimestamp(reply, 32)
            val t3 = readTimestamp(reply, 40)
            val transitMs = (t4Elapsed - t1Elapsed) / 1_000_000.0
            val t4Wall = t1Wall + transitMs
            val rtt = (transitMs - (t3 - t2)).coerceAtLeast(0.0)
            val offset = ((t2 - t1Wall) + (t3 - t4Wall)) / 2.0
            if (rtt > 3000 || abs(offset) > 86_400_000) throw IllegalStateException("Outlier NTP reply")
            return NtpSample(offset.roundToLong(), rtt.roundToLong())
        }
    }

    private fun putTimestamp(buffer: ByteArray, offset: Int, unixMs: Long) {
        val seconds = unixMs / 1000 + UNIX_DELTA_SECONDS
        val fraction = ((unixMs % 1000).toDouble() / 1000 * 4_294_967_296.0).toLong()
        for (i in 0..3) buffer[offset + i] = (seconds ushr (24 - 8 * i)).toByte()
        for (i in 0..3) buffer[offset + 4 + i] = (fraction ushr (24 - 8 * i)).toByte()
    }

    private fun readTimestamp(buffer: ByteArray, offset: Int): Double {
        var seconds = 0L
        var fraction = 0L
        for (i in 0..3) seconds = (seconds shl 8) or (buffer[offset + i].toLong() and 0xff)
        for (i in 0..3) fraction = (fraction shl 8) or (buffer[offset + 4 + i].toLong() and 0xff)
        return (seconds - UNIX_DELTA_SECONDS) * 1000.0 + fraction.toDouble() * 1000.0 / 4_294_967_296.0
    }
}
