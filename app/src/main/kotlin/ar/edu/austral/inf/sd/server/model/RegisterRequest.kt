package ar.edu.austral.inf.sd.server.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

/**
 * 
 * @param host 
 * @param port 
 * @param name 
 */
data class RegisterRequest(

    @get:JsonProperty("host", required = true) val host: String,

    @get:JsonProperty("port", required = true) val port: Int,

    @get:JsonProperty("name", required = true) val name: String,

    @get:JsonProperty("uuid", required = true) val uuid: UUID?,

    @get:JsonProperty("salt", required = true) val salt: String?
) {

}

