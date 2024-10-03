package ar.edu.austral.inf.sd

import ar.edu.austral.inf.sd.server.api.BadRequestException
import ar.edu.austral.inf.sd.server.api.PlayApiService
import ar.edu.austral.inf.sd.server.api.RegisterNodeApiService
import ar.edu.austral.inf.sd.server.api.RelayApiService
import ar.edu.austral.inf.sd.server.model.PlayResponse
import ar.edu.austral.inf.sd.server.model.RegisterResponse
import ar.edu.austral.inf.sd.server.model.Signature
import ar.edu.austral.inf.sd.server.model.Signatures
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.IOException
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

@Component
class ApiServicesImpl: RegisterNodeApiService, RelayApiService, PlayApiService {

    @Value("\${server.name:nada}")
    private val myServerName: String = ""
    @Value("\${server.port:8080}")
    private val myServerPort: Int = 0
    @Value("\${server.host:localhost}")
    private val myServerHost: String = ""
    private val nodes: MutableList<RegisterResponse> = mutableListOf()
    private var nextNode: RegisterResponse? = null
    private val messageDigest = MessageDigest.getInstance("SHA-512")
    private var salt = newSalt()
    private val currentRequest
        get() = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
    private var resultReady = CountDownLatch(1)
    private var currentMessageWaiting = MutableStateFlow<PlayResponse?>(null)
    private var currentMessageResponse = MutableStateFlow<PlayResponse?>(null)

    override fun registerNode(host: String?, port: Int?, name: String?): RegisterResponse {
        println("registerNode($host, $port, $name)")
        val nextNode = if (nodes.isEmpty()) {
            // es el primer nodo
            val me = RegisterResponse(myServerName, currentRequest.serverName, myServerPort, "", "")
            nodes.add(me)
            me
        } else {
            nodes.last()
        }
        val uuid = UUID.randomUUID().toString()
        val salt = newSalt()
        val node = RegisterResponse(name!!, host!!, port!!, uuid, salt)
        nodes.add(node)

        return RegisterResponse(nextNode.name, nextNode.nextHost, nextNode.nextPort, uuid, salt)
    }

    override fun relayMessage(message: String, signatures: Signatures): Signature {
        val receivedHash = doHash(message.encodeToByteArray(), salt)
        val receivedContentType = currentRequest.getPart("message")?.contentType ?: "nada"
        val receivedLength = message.length

        if (nextNode != null) {
            // Reenviar el mensaje al siguiente nodo en la cadena con el contentType adecuado
            val updatedSignatures = signatures.copy(
                items = signatures.items + clientSign(message, receivedContentType)
            )
            sendRelayMessage(message, receivedContentType, nextNode!!, updatedSignatures)
        } else {
            // me llego algo, no lo tengo que pasar
            if (currentMessageWaiting.value == null) throw BadRequestException("no waiting message")
            val current = currentMessageWaiting.getAndUpdate { null }!!
            val errors = mutableListOf<String>()
            if (receivedHash != current.originalHash) errors.add("Hash Failure")
            if (receivedContentType != current.originalContentType) errors.add("Content Type Failure")
            if (receivedLength != current.originalLength) errors.add("Content Length Failure")
            for( item in signatures.items) {
                val node = nodes.firstOrNull { it.name == item.name }
                if (node == null) {
                    errors.add("Invalid name ${item.name}")
                } else {
                    val itemCalculatedHash = doHash(message.encodeToByteArray(), node.hash)
                    if (item.hash != itemCalculatedHash) errors.add("Hash Failure ${item.name}")
                    if (item.contentLength != current.originalLength) errors.add("Content Length Diff ${item.name}")
                    if (item.contentType != current.originalContentType) errors.add("Content Type Diff ${item.name}")
                }
            }
            val response = current.copy(
                contentResult = errors.joinToString(),
                receivedHash = receivedHash,
                receivedLength = receivedLength,
                receivedContentType = receivedContentType,
                signatures = signatures
            )
            currentMessageResponse.update { response }
            resultReady.countDown()
        }
        return Signature(
            name = myServerName,
            hash = receivedHash,
            contentType = receivedContentType,
            contentLength = receivedLength
        )
    }

    override fun sendMessage(body: String): PlayResponse {
        if (nodes.isEmpty()) {
            // inicializamos el primer nodo como yo mismo
            val me = RegisterResponse(currentRequest.serverName, "", myServerPort, "", "")
            nodes.add(me)
        }
        currentMessageWaiting.update { newResponse(body) }
        val contentType = currentRequest.contentType
        sendRelayMessage(body, contentType, nodes.last(), Signatures(listOf()))
        resultReady.await()
        resultReady = CountDownLatch(1)
        return currentMessageResponse.value!!
    }

    internal fun registerToServer(registerHost: String, registerPort: Int) {
        val client = OkHttpClient()

        // Construir la URL con los parámetros de consulta (host, port, name)
        val url = HttpUrl.Builder()
            .scheme("http")
            .host(registerHost)
            .port(registerPort)
            .addPathSegment("register-node")
            .addQueryParameter("host", myServerHost)
            .addQueryParameter("port", myServerPort.toString())
            .addQueryParameter("name", myServerName)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null, 0, 0))
            .build()

        println("Enviando request: $request")
        try {
            // Enviar la solicitud y procesar la respuesta
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val registerResponse: RegisterResponse = jacksonObjectMapper().readValue(responseBody)
                    println("nextNode = $registerResponse")
                    nextNode = RegisterResponse(registerResponse.name, registerResponse.nextHost, registerResponse.nextPort,
                        registerResponse.uuid, registerResponse.hash)
                    salt = registerResponse.hash
                }
            }
        } catch (e: Exception) {
            println("Error al registrar el nodo: ${e.message}")
        }
    }


    private fun sendRelayMessage(body: String, contentType: String, relayNode: RegisterResponse, signatures: Signatures) {
        val client = OkHttpClient()
        val objectMapper = jacksonObjectMapper()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("message",null, body.toByteArray().toRequestBody(contentType.toMediaType()))
            .addFormDataPart("signatures", null, objectMapper.writeValueAsString(signatures).toByteArray()
                .toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        println("Request: "+requestBody.parts + "\n" + requestBody.type)

        val request = Request.Builder()
            .url("http://${relayNode.nextHost}:${relayNode.nextPort}/relay")
            .post(requestBody)
            .build()

        try {
            // Enviar la solicitud y procesar la respuesta
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val responseBody = response.body?.string()
                println("Mensaje relay exitoso, respuesta: $responseBody")
            }
        } catch (e: Exception) {
            println("Error en sendRelayMessage: ${e.message}")
        }
    }


    private fun clientSign(message: String, contentType: String): Signature {
        val receivedHash = doHash(message.encodeToByteArray(), salt)
        return Signature(myServerName, receivedHash, contentType, message.length)
    }

    private fun newResponse(body: String) = PlayResponse(
        "Unknown",
        currentRequest.contentType,
        body.length,
        doHash(body.encodeToByteArray(), salt),
        "Unknown",
        -1,
        "N/A",
        Signatures(listOf())
    )

    private fun doHash(body: ByteArray, salt: String):  String {
        val saltBytes = Base64.getDecoder().decode(salt)
        messageDigest.update(saltBytes)
        val digest = messageDigest.digest(body)
        return Base64.getEncoder().encodeToString(digest)
    }

    companion object {
        fun newSalt(): String = Base64.getEncoder().encodeToString(Random.nextBytes(9))
    }
}