package no.nav.amt.deltaker.tiltakskoordinator.api.response

import io.ktor.network.sockets.SocketTimeoutException
import no.nav.amt.deltaker.deltaker.DeltakerOppdateringResult
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.net.SocketException
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerOppdateringResponse(
    val id: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val status: DeltakerStatus,
    val historikk: List<DeltakerHistorikk>,
    val sistEndret: LocalDateTime = LocalDateTime.now(),
    val erManueltDeltMedArrangor: Boolean,
    val feilkode: DeltakerOppdateringFeilkode?,
) {
    companion object {
        fun fromDeltakerOppdateringResult(
            oppdateringResult: DeltakerOppdateringResult,
            historikk: List<DeltakerHistorikk>,
        ): DeltakerOppdateringResponse = with(oppdateringResult.deltaker) {
            DeltakerOppdateringResponse(
                id = id,
                startdato = startdato,
                sluttdato = sluttdato,
                dagerPerUke = dagerPerUke,
                deltakelsesprosent = deltakelsesprosent,
                bakgrunnsinformasjon = bakgrunnsinformasjon,
                deltakelsesinnhold = deltakelsesinnhold,
                status = status,
                historikk = historikk,
                sistEndret = sistEndret,
                erManueltDeltMedArrangor = erManueltDeltMedArrangor,
                feilkode = oppdateringResult.exceptionOrNull?.toOppdateringFeilkode(),
            )
        }

        private fun Throwable.toOppdateringFeilkode() = when (this) {
            is IllegalStateException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
            is IllegalArgumentException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
            is SQLException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
            is SocketTimeoutException -> DeltakerOppdateringFeilkode.MIDLERTIDIG_FEIL
            is SocketException -> DeltakerOppdateringFeilkode.MIDLERTIDIG_FEIL
            is Exception -> null
            else -> null
        }
    }
}
