package no.nav.amt.deltaker.deltaker.api.utils

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.utils.generateJWT
import java.util.UUID

internal fun HttpRequestBuilder.postRequest(body: Any) {
    header(
        HttpHeaders.Authorization,
        "Bearer ${
            generateJWT(
                consumerClientId = "amt-deltaker-bff",
                audience = "amt-deltaker",
            )
        }",
    )
    header("aktiv-enhet", "0101")
    contentType(ContentType.Application.Json)
    setBody(objectMapper.writeValueAsString(body))
}

internal fun HttpRequestBuilder.postVeilederRequest(body: Any) {
    val navAnsattAzureId = UUID.randomUUID()
    header(
        HttpHeaders.Authorization,
        "Bearer ${
            generateJWT(
                oid = navAnsattAzureId.toString(),
                consumerClientId = "frontend-clientid",
                audience = "amt-deltaker",
            )
        }",
    )
    header("aktiv-enhet", "0101")
    contentType(ContentType.Application.Json)
    setBody(objectMapper.writeValueAsString(body))
}

internal fun HttpRequestBuilder.noBodyRequest() {
    header(
        HttpHeaders.Authorization,
        "Bearer ${
            generateJWT(
                consumerClientId = "amt-deltaker-bff",
                audience = "amt-deltaker",
            )
        }",
    )
}
