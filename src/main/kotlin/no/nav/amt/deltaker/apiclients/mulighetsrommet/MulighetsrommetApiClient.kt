package no.nav.amt.deltaker.apiclients.mulighetsrommet

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import java.util.UUID

class MulighetsrommetApiClient(
    baseUrl: String,
    scope: String,
    httpClient: HttpClient,
    azureAdTokenClient: AzureAdTokenClient,
) : ApiClientBase(
        baseUrl = baseUrl,
        scope = scope,
        httpClient = httpClient,
        azureAdTokenClient = azureAdTokenClient,
    ) {
    suspend fun hentGjennomforingV2(id: UUID): GjennomforingV2Response = performGet("$baseUrl/api/v2/tiltaksgjennomforinger/$id")
        .failIfNotSuccess("Klarte ikke å hente gjennomføring fra Mulighetsrommet v2 API.")
        .body<GjennomforingV2Response>()
}
