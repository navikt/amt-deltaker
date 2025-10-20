package no.nav.amt.deltaker.apiclients.mulighetsrommet

import no.nav.amt.deltaker.utils.JsonUtils.fromJsonString
import no.nav.amt.lib.ktor.auth.AzureAdTokenClient
import no.nav.common.rest.client.RestClient.baseClient
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.UUID

class MulighetsrommetApiClient(
    private val baseUrl: String,
    private val azureAdTokenClient: AzureAdTokenClient,
    private val scope: String,
    private val httpClient: OkHttpClient = baseClient(),
) {
    suspend fun hentGjennomforingV2(id: UUID): Gjennomforing {
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val request = Request
            .Builder()
            .url("$baseUrl/api/v2/tiltaksgjennomforinger/$id")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Klarte ikke å hente gjennomføring fra Mulighetsrommet v2 API. status=${response.code}")
            }

            val body = response.body?.string() ?: throw RuntimeException("Body is missing")
            val responseBody = fromJsonString<GjennomforingV2Response>(body)
            return responseBody.toGjennomforing()
        }
    }
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
