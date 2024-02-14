package no.nav.amt.deltaker.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

class AmtPersonServiceClient(
    private val baseUrl: String,
    private val scope: String,
    private val httpClient: HttpClient,
    private val azureAdTokenClient: AzureAdTokenClient,
) {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    suspend fun hentNavAnsatt(navIdent: String): NavAnsatt {
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val response = httpClient.post("$baseUrl/api/nav-ansatt") {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(NavAnsattRequest(navIdent)))
        }
        if (!response.status.isSuccess()) {
            log.error(
                "Kunne ikke hente nav-ansatt med ident $navIdent fra amt-person-service. " +
                    "Status=${response.status.value} error=${response.bodyAsText()}",
            )
            throw RuntimeException("Kunne ikke hente NAV-ansatt fra amt-person-service")
        }
        return response.body()
    }

    suspend fun hentNavAnsatt(id: UUID): NavAnsatt {
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val response = httpClient.get("$baseUrl/api/nav-ansatt/$id") {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            log.error(
                "Kunne ikke hente nav-ansatt med id $id fra amt-person-service. " +
                    "Status=${response.status.value} error=${response.bodyAsText()}",
            )
            throw RuntimeException("Kunne ikke hente NAV-ansatt fra amt-person-service")
        }
        return response.body()
    }

    suspend fun hentNavEnhet(navEnhetsnummer: String): NavEnhet {
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val response = httpClient.post("$baseUrl/api/nav-enhet") {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(NavEnhetRequest(navEnhetsnummer)))
        }
        if (!response.status.isSuccess()) {
            log.error(
                "Kunne ikke hente nav-enhet med nummer $navEnhetsnummer fra amt-person-service. " +
                    "Status=${response.status.value} error=${response.bodyAsText()}",
            )
            throw RuntimeException("Kunne ikke hente NAV-enhet fra amt-person-service")
        }
        return response.body<NavEnhetDto>().tilNavEnhet()
    }
}

data class NavAnsattRequest(
    val navIdent: String,
)

data class NavEnhetRequest(
    val enhetId: String,
)

data class NavEnhetDto(
    val id: UUID,
    val enhetId: String,
    val navn: String,
) {
    fun tilNavEnhet(): NavEnhet {
        return NavEnhet(
            id = id,
            enhetsnummer = enhetId,
            navn = navn,
        )
    }
}
