package ar.edu.austral.inf.sd.server.api

import ar.edu.austral.inf.sd.server.model.RegisterResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping("\${api.base-path:}")
class RegisterNodeApiController(@Autowired(required = true) val service: RegisterNodeApiService) {


    @RequestMapping(
        method = [RequestMethod.POST],
        value = ["/register-node"],
        produces = ["application/json"]
    )
    fun registerNode(
        @Valid @NotBlank @RequestParam(value = "host", required = false) host: kotlin.String?,
        @Valid @NotNull @PositiveOrZero @RequestParam(value = "port", required = false) port: kotlin.Int?,
        @Valid @NotNull @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        @RequestParam(value = "uuid", required = false) uuid: java.util.UUID?,
        @Valid @NotBlank @RequestParam(value = "salt", required = false) salt: kotlin.String?,
        @Valid @NotBlank @RequestParam(value = "name", required = false) name: kotlin.String?
    ): ResponseEntity<RegisterResponse> {
        return ResponseEntity(service.registerNode(host, port, uuid, salt, name), HttpStatus.valueOf(200))
    }
}
