package se.liban.adblock.vpn

internal object Packets {
    const val PROTO_TCP: Int = 6
    const val PROTO_UDP: Int = 17
    const val FIN: Int = 1
    const val SYN: Int = 2
    const val RST: Int = 4
    const val PSH: Int = 8
    const val ACK: Int = 16

    fun version(packet: ByteArray): Int = (packet[0].toInt() ushr 4) and 0xF

    fun ipHeaderLength(packet: ByteArray): Int = (packet[0].toInt() and 0xF) * 4

    fun protocol(packet: ByteArray): Int = packet[9].toInt() and 0xFF

    fun srcIp(packet: ByteArray): Int = readU32(packet, 12).toInt()

    fun dstIp(packet: ByteArray): Int = readU32(packet, 16).toInt()

    fun ipBytes(ip: Int): ByteArray = byteArrayOf(
        (ip ushr 24).toByte(),
        (ip ushr 16).toByte(),
        (ip ushr 8).toByte(),
        ip.toByte()
    )

    fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    fun readU32(data: ByteArray, offset: Int): Long =
        ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)

    fun writeU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    fun writeU32(data: ByteArray, offset: Int, value: Long) {
        val bits = value.toInt()
        data[offset] = (bits ushr 24).toByte()
        data[offset + 1] = (bits ushr 16).toByte()
        data[offset + 2] = (bits ushr 8).toByte()
        data[offset + 3] = bits.toByte()
    }

    fun checksum(data: ByteArray, offset: Int, length: Int, initial: Int = 0): Int {
        var sum = initial
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += readU16(data, i)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }

    fun queryName(dns: ByteArray, offset: Int = 12): String? {
        if (dns.size < 12 || readU16(dns, 4) == 0) return null
        val labels = ArrayList<String>(4)
        var index = offset
        var hops = 0
        while (index < dns.size && hops < 16) {
            val length = dns[index].toInt() and 0xFF
            when {
                length == 0 -> return labels.joinToString(".").lowercase()
                length and 0xC0 == 0xC0 -> return null
                index + 1 + length > dns.size -> return null
                else -> {
                    labels += String(dns, index + 1, length, Charsets.US_ASCII)
                    index += length + 1
                    hops++
                }
            }
        }
        return null
    }

    fun questionEnd(dns: ByteArray): Int {
        var index = 12
        while (index < dns.size) {
            val length = dns[index].toInt() and 0xFF
            if (length == 0) return index + 1 + 4
            if (length and 0xC0 == 0xC0) return -1
            index += length + 1
        }
        return -1
    }

    fun blockedDnsResponse(query: ByteArray): ByteArray? {
        if (query.size < 17) return null
        val qEnd = questionEnd(query)
        if (qEnd < 0 || qEnd > query.size) return null
        val qtype = readU16(query, qEnd - 4)
        val question = query.copyOfRange(12, qEnd)
        val answer = when (qtype) {
            1 -> byteArrayOf(
                0xC0.toByte(), 0x0C, 0, 1, 0, 1,
                0, 0, 0, 60, 0, 4,
                0, 0, 0, 0
            )
            28 -> byteArrayOf(
                0xC0.toByte(), 0x0C, 0, 28, 0, 1,
                0, 0, 0, 60, 0, 16,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            )
            else -> ByteArray(0)
        }
        val response = ByteArray(12 + question.size + answer.size)
        response[0] = query[0]
        response[1] = query[1]
        response[2] = 0x81.toByte()
        response[3] = 0x80.toByte()
        writeU16(response, 4, 1)
        writeU16(response, 6, if (answer.isEmpty()) 0 else 1)
        question.copyInto(response, 12)
        answer.copyInto(response, 12 + question.size)
        return response
    }

    fun udpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen = 8 + payload.size
        val total = 20 + udpLen
        val packet = ByteArray(total)
        fillIpv4(packet, total, PROTO_UDP, srcIp, dstIp)
        writeU16(packet, 20, srcPort)
        writeU16(packet, 22, dstPort)
        writeU16(packet, 24, udpLen)
        payload.copyInto(packet, 28)
        return packet
    }

    fun tcpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        payload: ByteArray = ByteArray(0),
        synOptions: Boolean = false
    ): ByteArray {
        val tcpHeader = if (synOptions) 24 else 20
        val tcpLen = tcpHeader + payload.size
        val total = 20 + tcpLen
        val packet = ByteArray(total)
        fillIpv4(packet, total, PROTO_TCP, srcIp, dstIp)
        writeU16(packet, 20, srcPort)
        writeU16(packet, 22, dstPort)
        writeU32(packet, 24, seq)
        writeU32(packet, 28, ack)
        packet[32] = ((tcpHeader / 4) shl 4).toByte()
        packet[33] = flags.toByte()
        writeU16(packet, 34, 65535)
        if (synOptions) {
            packet[40] = 2
            packet[41] = 4
            writeU16(packet, 42, 1460)
        }
        if (payload.isNotEmpty()) payload.copyInto(packet, 20 + tcpHeader)
        val pseudo = pseudoHeaderSum(srcIp, dstIp, PROTO_TCP, tcpLen)
        val sum = checksum(packet, 20, tcpLen, pseudo)
        writeU16(packet, 36, sum)
        return packet
    }

    private fun fillIpv4(
        packet: ByteArray,
        total: Int,
        protocol: Int,
        srcIp: ByteArray,
        dstIp: ByteArray
    ) {
        packet[0] = 0x45
        writeU16(packet, 2, total)
        packet[8] = 64
        packet[9] = protocol.toByte()
        srcIp.copyInto(packet, 12)
        dstIp.copyInto(packet, 16)
        val sum = checksum(packet, 0, 20)
        writeU16(packet, 10, sum)
    }

    private fun pseudoHeaderSum(srcIp: ByteArray, dstIp: ByteArray, protocol: Int, length: Int): Int {
        var sum = 0
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        sum += protocol
        sum += length
        return sum
    }
}
