package ar.edu.austral.inf.sd.server.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 
 * @param name El nombre del nodo que firmo
 * @param hash El hash del contenido calculado por el nodo
 * @param contentType El hash de la firma del nodo
 * @param contentLength La longitud del contenido
 */
data class Signature(

    @get:JsonProperty("name", required = true) val name: String,

    @get:JsonProperty("hash", required = true) val hash: String,

    @get:JsonProperty("contentType") val contentType: String? = null,

    @get:JsonProperty("contentLength") val contentLength: Int? = null
    )
