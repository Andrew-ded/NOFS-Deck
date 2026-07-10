package com.nofs.desk.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * UDP-автопоиск агента в локальной сети.
 * Планшет шлёт broadcast "NOFS_DISCOVER" на порт 48485,
 * агент отвечает "NOFS_HERE {\"name\":\"DESKTOP-X\",\"port\":48484}".
 */
data class DiscoveredAgent(val host: String, val port: Int, val name: String)

object Discovery {
    const val DISCOVERY_PORT = 48485
    private const val REQUEST = "NOFS_DISCOVER"
    private const val RESPONSE_PREFIX = "NOFS_HERE "

    suspend fun discover(timeoutMs: Long = 2500): DiscoveredAgent? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = timeoutMs.toInt()
                    val payload = REQUEST.toByteArray()
                    val broadcast = InetAddress.getByName("255.255.255.255")
                    socket.send(DatagramPacket(payload, payload.size, broadcast, DISCOVERY_PORT))

                    val buf = ByteArray(1024)
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: Exception) {
                        return@withTimeoutOrNull null
                    }
                    val text = String(packet.data, 0, packet.length)
                    if (!text.startsWith(RESPONSE_PREFIX)) return@withTimeoutOrNull null
                    val obj = runCatching {
                        ProtocolJson.parseToJsonElement(text.removePrefix(RESPONSE_PREFIX)) as JsonObject
                    }.getOrNull() ?: return@withTimeoutOrNull null

                    DiscoveredAgent(
                        host = packet.address.hostAddress ?: return@withTimeoutOrNull null,
                        port = obj["port"]?.jsonPrimitive?.int ?: 48484,
                        name = obj["name"]?.jsonPrimitive?.content ?: "PC"
                    )
                }
            }
        }
}
