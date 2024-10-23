package no.nav.amt.deltaker.isoppfolgingstilfelle

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.amt.deltaker.auth.AzureAdTokenClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

class IsOppfolgingstilfelleClient(
    private val baseUrl: String,
    private val scope: String,
    private val httpClient: HttpClient,
    private val azureAdTokenClient: AzureAdTokenClient,
) {
    val log: Logger = LoggerFactory.getLogger(javaClass)
    private val personIdentHeader = "NAV_PERSONIDENT_HEADER"

    suspend fun erSykmeldtMedArbeidsgiver(personident: String): Boolean {
        val oppfolgingstilfeller = hentOppfolgingstilfeller(personident).oppfolgingstilfelleList

        if (oppfolgingstilfeller.isEmpty()) {
            return false
        }
        return oppfolgingstilfeller.filter { it.gyldigForDato(LocalDate.now()) }
            .firstOrNull { it.arbeidstakerAtTilfelleEnd } != null
    }

    private suspend fun hentOppfolgingstilfeller(personident: String): OppfolgingstilfellePersonDTO {
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val response = httpClient.get("$baseUrl/api/system/v1/oppfolgingstilfelle/personident") {
            header(HttpHeaders.Authorization, token)
            header(personIdentHeader, personident)
            contentType(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            log.error(
                "Kunne ikke hente oppfølgingstilfelle fra isoppfolgingstilfelle. " +
                    "Status=${response.status.value} error=${response.bodyAsText()}",
            )
            throw RuntimeException("Kunne ikke hente oppfølgingstilfelle fra isoppfolgingstilfelle")
        }
        return response.body<OppfolgingstilfellePersonDTO>()
    }
}

data class OppfolgingstilfellePersonDTO(
    val oppfolgingstilfelleList: List<OppfolgingstilfelleDTO>,
)

data class OppfolgingstilfelleDTO(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val start: LocalDate,
    val end: LocalDate,
) {
    fun gyldigForDato(dato: LocalDate): Boolean {
        return dato in start..end
    }
}
