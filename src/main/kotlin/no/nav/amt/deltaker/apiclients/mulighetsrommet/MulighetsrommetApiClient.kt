package no.nav.amt.deltaker.apiclients.mulighetsrommet

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.amt.lib.ktor.clients.ApiClientBase
import no.nav.amt.lib.ktor.clients.failIfNotSuccess
import java.time.LocalDate
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
    suspend fun hentGjennomforingV2(id: UUID): Gjennomforing = performGet("$baseUrl/api/v2/tiltaksgjennomforinger/$id")
        .failIfNotSuccess("Klarte ikke å hente gjennomføring fra Mulighetsrommet v2 API.")
        .body<GjennomforingV2Response>()
        .toGjennomforing()
}

data class GjennomforingV2Response(
    val id: UUID,
    val tiltakstype: TiltakstypeResponse,
    val arrangor: ArrangorResponse,
) {
    data class ArrangorResponse(
        val organisasjonsnummer: String,
    )

    data class TiltakstypeResponse(
        val tiltakskode: String,
        val arenakode: String,
    )

    fun toGjennomforing() = Gjennomforing(
        id = id,
        tiltakstype = Gjennomforing.Tiltakstype(arenaKode = tiltakstype.arenakode),
        virksomhetsnummer = arrangor.organisasjonsnummer,
    )

    // TODO toDeltakerListe() ?
}

data class Gjennomforing(
    val id: UUID,
    val tiltakstype: Tiltakstype,
    val navn: String? = null,
    val startDato: LocalDate? = null,
    val sluttDato: LocalDate? = null,
    val status: Status? = null,
    val virksomhetsnummer: String,
    val oppstart: Oppstartstype? = null,
) {
    enum class Oppstartstype {
        LOPENDE,
        FELLES,
    }

    data class Tiltakstype(
        val id: UUID? = null,
        val navn: String? = null,
        val arenaKode: String,
    )

    enum class Status {
        GJENNOMFORES,
        AVBRUTT,
        AVLYST,
        AVSLUTTET,
    }
}
