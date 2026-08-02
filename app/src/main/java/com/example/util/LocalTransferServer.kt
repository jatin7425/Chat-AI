package com.example.util

import android.content.Context
import android.net.wifi.WifiManager
import com.example.data.repository.SoulRepository
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.Locale

class LocalTransferServer(private val soulRepository: SoulRepository) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    var isRunning = false
        private set

    fun startServer(port: Int = 8888, onStatusChanged: (String) -> Unit) {
        if (isRunning) return
        isRunning = true

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                withContext(Dispatchers.Main) {
                    onStatusChanged("Server active on port $port")
                }

                while (isActive && isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleConnection(socket)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onStatusChanged("Server stopped or error: ${e.localizedMessage}")
                }
            } finally {
                isRunning = false
            }
        }
    }

    private suspend fun handleConnection(socket: Socket) {
        try {
            socket.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                if (line!!.lowercase(Locale.ROOT).startsWith("content-length:")) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            if (requestLine.startsWith("GET /export") || requestLine.startsWith("GET / ")) {
                val jsonPayload = soulRepository.exportAppDataJson()
                val bytes = jsonPayload.toByteArray(Charsets.UTF_8)

                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: application/json; charset=utf-8\r\n")
                writer.print("Content-Length: ${bytes.size}\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("Connection: close\r\n\r\n")
                writer.flush()

                socket.getOutputStream().write(bytes)
                socket.getOutputStream().flush()
            } else if (requestLine.startsWith("POST /import")) {
                val charArray = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(charArray, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                val bodyJson = String(charArray)
                val importResult = soulRepository.importAppDataJson(bodyJson)

                if (importResult.isSuccess) {
                    val resp = "{\"status\":\"success\"}"
                    writer.print("HTTP/1.1 200 OK\r\n")
                    writer.print("Content-Type: application/json\r\n")
                    writer.print("Content-Length: ${resp.length}\r\n\r\n")
                    writer.print(resp)
                } else {
                    val resp = "{\"status\":\"error\",\"message\":\"${importResult.exceptionOrNull()?.localizedMessage}\"}"
                    writer.print("HTTP/1.1 500 Internal Error\r\n")
                    writer.print("Content-Type: application/json\r\n")
                    writer.print("Content-Length: ${resp.length}\r\n\r\n")
                    writer.print(resp)
                }
                writer.flush()
            } else {
                writer.print("HTTP/1.1 404 Not Found\r\n\r\n")
                writer.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverJob?.cancel()
    }

    companion object {
        fun getLocalIpAddress(context: Context): String {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    return String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    val addrs = intf.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is InetAddress) {
                            val host = addr.hostAddress
                            if (host != null && !host.contains(":")) {
                                return host
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "192.168.1.10"
        }

        suspend fun receiveDataFromSender(senderIp: String, port: Int = 8888, soulRepository: SoulRepository): Result<Unit> {
            return withContext(Dispatchers.IO) {
                try {
                    val cleanIp = senderIp.trim()
                        .removePrefix("http://")
                        .removePrefix("https://")
                        .substringBefore(":")
                        .substringBefore("/")

                    val urlString = "http://$cleanIp:$port/export"
                    val connection = URL(urlString).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 8000
                    connection.readTimeout = 12000
                    connection.requestMethod = "GET"

                    if (connection.responseCode == 200) {
                        val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                        soulRepository.importAppDataJson(jsonString)
                    } else {
                        Result.failure(Exception("HTTP Error ${connection.responseCode} from Sender"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }
}
