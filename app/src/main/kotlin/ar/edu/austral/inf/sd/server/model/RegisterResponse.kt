package ar.edu.austral.inf.sd.server.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 
 * @param nextHost 
 * @param nextPort 
 * @param uuid 
 * @param hash 
 */
data class RegisterResponse(

    @get:JsonProperty("name", required = true) val name: String,

    @get:JsonProperty("nextHost", required = true) val nextHost: String,

    @get:JsonProperty("nextPort", required = true) val nextPort: Int,

    @get:JsonProperty("uuid", required = true) val uuid: String,

    @get:JsonProperty("hash", required = true) val hash: String
    ) {

}

