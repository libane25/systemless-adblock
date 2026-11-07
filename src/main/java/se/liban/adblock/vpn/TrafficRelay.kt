package se.liban.adblock.vpn

import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local VPN relay. Ad domains get a DNS answer of 0.0.0.0. Every other
 * TCP and UDP flow is forwarded from the phone itself. Nothing is sent to
 * a remote VPN provider.
 */
internal class TrafficRelay(
    private val blocked: Set<String>,
    private val protectSocket: (Socket) -> Boolean,
    private val protectDatagram: (DatagramSocket) -> Boolean,
    private val output: OutputStream
) {
    private val running = AtomicBoolean(true)
    private val writeLock = Any()
    private val tcp = ConcurrentHashMap<FlowKey, TcpFlow>()
    private val udp = ConcurrentHashMap<FlowKey, UdpFlow>()
    private val connectPool = Executors.newCachedThreadPool()

    fun onPacket(packet: ByteArray, length: Int) {
        if (!running.get() || length < 20 || Packets.version(packet) != 4) return
        val headerLength = Packets.ipHeaderLength(packet)
        if (headerLength < 20 || length < headerLength) return
        when (Packets.protocol(packet)) {
            Packets.PROTO_UDP -> onUdp(packet, length, headerLength)
            Packets.PROTO_TCP -> onTcp(packet, length, headerLength)
        }
    }

    fun shutdown() {
        running.set(false)
        tcp.values.forEach { it.close() }
        udp.values.forEach { it.close() }
        tcp.clear()
        udp.clear()
        connectPool.shutdownNow()
    }

    private fun onUdp(packet: ByteArray, length: Int, ipHeader: Int) {
        if (length < ipHeader + 8) return
        val srcIp = Packets.srcIp(packet)
        val dstIp = Packets.dstIp(packet)
        val srcPort = Packets.readU16(packet, ipHeader)
        val dstPort = Packets.readU16(packet, ipHeader + 2)
        val payloadStart = ipHeader + 8
        val payload = packet.copyOfRange(payloadStart, length)
        if (dstPort == 53 && answerBlockedDns(srcIp, dstIp, srcPort, dstPort, payload)) return
        val key = FlowKey(srcIp, srcPort, dstIp, dstPort)
        val flow = udp.computeIfAbsent(key) { UdpFlow(it) }
        if (!flow.isOpen) udp.remove(key, flow) else flow.send(payload)
    }

    private fun answerBlockedDns(
        srcIp: Int,
        dstIp: Int,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): Boolean {
        val name = Packets.queryName(payload) ?: return false
        if (!isBlocked(name)) return false
        val dns = Packets.blockedDnsResponse(payload) ?: return false
        write(
            Packets.udpPacket(
                Packets.ipBytes(dstIp),
                Packets.ipBytes(srcIp),
                dstPort,
                srcPort,
                dns
            )
        )
        return true
    }

    private fun isBlocked(name: String): Boolean {
        var host = name.trim('.').lowercase()
        while (host.isNotEmpty()) {
            if (host in blocked) return true
            val dot = host.indexOf('.')
            if (dot < 0) return false
            host = host.substring(dot + 1)
        }
        return false
    }

    private fun onTcp(packet: ByteArray, length: Int, ipHeader: Int) {
        if (length < ipHeader + 20) return
        val srcIp = Packets.srcIp(packet)
        val dstIp = Packets.dstIp(packet)
        val srcPort = Packets.readU16(packet, ipHeader)
        val dstPort = Packets.readU16(packet, ipHeader + 2)
        val dataOffset = ((packet[ipHeader + 12].toInt() ushr 4) and 0xF) * 4
        if (dataOffset < 20 || ipHeader + dataOffset > length) return
        val flags = packet[ipHeader + 13].toInt() and 0xFF
        val seq = Packets.readU32(packet, ipHeader + 4)
        val key = FlowKey(srcIp, srcPort, dstIp, dstPort)
        if (dstPort == 853) {
            sendTcp(key, seq, seq + 1, Packets.RST or Packets.ACK, ByteArray(0))
            return
        }
        val existing = tcp[key]
        if (existing == null) {
            if (flags and Packets.SYN == 0 || flags and Packets.ACK != 0) return
            if (tcp.size >= MAX_TCP) {
                sendTcp(key, 0, seq + 1, Packets.RST or Packets.ACK, ByteArray(0))
                return
            }
            val flow = TcpFlow(key, seq)
            tcp[key] = flow
            connectPool.execute { flow.connect() }
            return
        }
        existing.fromClient(flags, seq, packet, ipHeader + dataOffset, length)
    }

    private fun sendTcp(
        key: FlowKey,
        seq: Long,
        ack: Long,
        flags: Int,
        payload: ByteArray,
        synOptions: Boolean = false
    ) {
        write(
            Packets.tcpPacket(
                srcIp = Packets.ipBytes(key.dstIp),
                dstIp = Packets.ipBytes(key.srcIp),
                srcPort = key.dstPort,
                dstPort = key.srcPort,
                seq = seq,
                ack = ack,
                flags = flags,
                payload = payload,
                synOptions = synOptions
            )
        )
    }

    private fun write(packet: ByteArray) {
        if (!running.get()) return
        synchronized(writeLock) {
            output.write(packet)
            output.flush()
        }
    }

    private inner class TcpFlow(
        val key: FlowKey,
        synSeq: Long
    ) {
        var expectedClientSeq: Long = (synSeq + 1) and 0xFFFFFFFFL
        var ourSeq: Long = (System.nanoTime() and 0xFFFFFFFFL)
        var state = State.CONNECTING
        var socket: Socket? = null
        private val queue = ArrayDeque<ByteArray>()
        private val lock = Any()

        fun connect() {
            val opened = Socket()
            try {
                if (!protectSocket(opened)) throw IllegalStateException("protect failed")
                opened.tcpNoDelay = true
                opened.connect(InetSocketAddress(Packets.ipBytes(key.dstIp).asInet(), key.dstPort), 10_000)
                val synSeq = synchronized(lock) {
                    if (!running.get() || state == State.CLOSED) {
                        opened.close()
                        return
                    }
                    socket = opened
                    state = State.WAIT_ACK
                    val seq = ourSeq
                    ourSeq = (ourSeq + 1) and 0xFFFFFFFFL
                    seq
                }
                send(synSeq, expectedClientSeq, Packets.SYN or Packets.ACK, ByteArray(0), synOptions = true)
            } catch (_: Exception) {
                opened.close()
                send(ourSeq, expectedClientSeq, Packets.RST or Packets.ACK, ByteArray(0))
                tcp.remove(key, this)
            }
        }

        fun fromClient(flags: Int, seq: Long, packet: ByteArray, payloadStart: Int, length: Int) {
            if (flags and Packets.RST != 0) {
                close()
                tcp.remove(key, this)
                return
            }
            val payloadLength = length - payloadStart
            synchronized(lock) {
                if (state == State.CONNECTING) {
                    if (payloadLength > 0 && seq == expectedClientSeq && queue.size < 32) {
                        queue.add(packet.copyOfRange(payloadStart, length))
                        expectedClientSeq = (expectedClientSeq + payloadLength) and 0xFFFFFFFFL
                    }
                    return
                }
                if (state == State.WAIT_ACK && flags and Packets.ACK != 0) {
                    state = State.ESTABLISHED
                    flushLocked()
                    startReader()
                }
                if (payloadLength > 0 && seq == expectedClientSeq) {
                    val data = packet.copyOfRange(payloadStart, length)
                    expectedClientSeq = (expectedClientSeq + payloadLength) and 0xFFFFFFFFL
                    if (state == State.ESTABLISHED) {
                        writeToServer(data)
                    } else if (queue.size < 32) {
                        queue.add(data)
                    }
                    send(ourSeq, expectedClientSeq, Packets.ACK, ByteArray(0))
                } else if (flags and Packets.FIN != 0 && seq == expectedClientSeq) {
                    expectedClientSeq = (expectedClientSeq + 1) and 0xFFFFFFFFL
                    send(ourSeq, expectedClientSeq, Packets.FIN or Packets.ACK, ByteArray(0))
                    ourSeq = (ourSeq + 1) and 0xFFFFFFFFL
                    close()
                    tcp.remove(key, this)
                }
            }
        }

        private fun flushLocked() {
            val out = socket ?: return
            queue.forEach { data -> out.getOutputStream().write(data) }
            out.getOutputStream().flush()
            queue.clear()
        }

        private fun writeToServer(data: ByteArray) {
            val out = socket ?: return
            out.getOutputStream().write(data)
            out.getOutputStream().flush()
        }

        private fun startReader() {
            val current = socket ?: return
            connectPool.execute {
                val buffer = ByteArray(1400)
                try {
                    val input = current.getInputStream()
                    while (running.get() && state == State.ESTABLISHED) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        val data = buffer.copyOf(count)
                        synchronized(lock) {
                            send(ourSeq, expectedClientSeq, Packets.PSH or Packets.ACK, data)
                            ourSeq = (ourSeq + count) and 0xFFFFFFFFL
                        }
                    }
                    synchronized(lock) {
                        send(ourSeq, expectedClientSeq, Packets.FIN or Packets.ACK, ByteArray(0))
                        ourSeq = (ourSeq + 1) and 0xFFFFFFFFL
                    }
                } catch (_: Exception) {
                    if (running.get()) send(ourSeq, expectedClientSeq, Packets.RST or Packets.ACK, ByteArray(0))
                } finally {
                    close()
                    tcp.remove(key, this@TcpFlow)
                }
            }
        }

        private fun send(seq: Long, ack: Long, flags: Int, payload: ByteArray, synOptions: Boolean = false) {
            sendTcp(key, seq, ack, flags, payload, synOptions)
        }

        fun close() {
            synchronized(lock) {
                state = State.CLOSED
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
                socket = null
            }
        }
    }

    private inner class UdpFlow(val key: FlowKey) {
        private val socket = DatagramSocket()
        private val open = AtomicBoolean(true)
        val isOpen: Boolean get() = open.get()

        init {
            if (!protectDatagram(socket)) {
                open.set(false)
                socket.close()
            } else {
                socket.soTimeout = 60_000
                connectPool.execute {
                    val buffer = ByteArray(32_767)
                    try {
                        while (running.get() && open.get()) {
                            val incoming = DatagramPacket(buffer, buffer.size)
                            socket.receive(incoming)
                            val payload = incoming.data.copyOf(incoming.length)
                            write(
                                Packets.udpPacket(
                                    Packets.ipBytes(key.dstIp),
                                    Packets.ipBytes(key.srcIp),
                                    key.dstPort,
                                    key.srcPort,
                                    payload
                                )
                            )
                        }
                    } catch (_: Exception) {
                    } finally {
                        close()
                        udp.remove(key, this)
                    }
                }
            }
        }

        fun send(payload: ByteArray) {
            if (!open.get()) return
            val dest = InetSocketAddress(Packets.ipBytes(key.dstIp).asInet(), key.dstPort)
            socket.send(DatagramPacket(payload, payload.size, dest.address, dest.port))
        }

        fun close() {
            open.set(false)
            socket.close()
        }
    }

    private enum class State { CONNECTING, WAIT_ACK, ESTABLISHED, CLOSED }

    private data class FlowKey(val srcIp: Int, val srcPort: Int, val dstIp: Int, val dstPort: Int)

    private companion object {
        const val MAX_TCP = 256
    }
}

private fun ByteArray.asInet(): InetAddress = InetAddress.getByAddress(this)
