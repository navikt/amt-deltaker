package no.nav.amt.deltaker.deltaker

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.auth.AzureAdTokenClient
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

class AmtTiltakClient(
    private val baseUrl: String,
    private val scope: String,
    private val httpClient: HttpClient,
    private val azureAdTokenClient: AzureAdTokenClient,
) {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    suspend fun delMedArrangor(deltakerIder: List<UUID>): Map<UUID, DeltakerStatus> {
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val response = httpClient.post("$baseUrl/api/tiltakskoordinator/del-med-arrangor") {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(deltakerIder))
        }
        if (!response.status.isSuccess()) {
            log.error(
                "Kunne ikke dele med arrangor. " +
                    "Status=${response.status.value} error=${response.bodyAsText()}",
            )
            throw RuntimeException("Kunne ikke dele med arrangor")
        }
        return response.body()
    }
}
