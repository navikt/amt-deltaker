package no.nav.amt.deltaker.tiltakskoordinator

import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerOppdateringResult
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import java.net.SocketException
import java.sql.SQLException
import java.util.UUID

fun Routing.registerTiltakskoordinatorApi(deltakerService: DeltakerService) {
    val apiPath = "/tiltakskoordinator/deltakere"

    fun List<DeltakerOppdateringResult>.toDeltakereResponse() =
        this.map { it.toDeltakerResponse(deltakerService.getHistorikk(it.deltaker.id)) }

    authenticate("SYSTEM") {
        post("$apiPath/del-med-arrangor") {
            val request = call.receive<DelMedArrangorRequest>()

            val oppdaterteDeltakere = deltakerService
                .oppdaterDeltakere(
                    request.deltakerIder,
                    EndringFraTiltakskoordinator.DelMedArrangor,
                    request.endretAv,
                ).toDeltakereResponse()
            call.respond(oppdaterteDeltakere)
        }

        post("$apiPath/del-med-arrangor-v2") {
            val request = call.receive<DelMedArrangorRequest>()

            val oppdaterteDeltakere = deltakerService
                .oppdaterDeltakere(
                    request.deltakerIder,
                    EndringFraTiltakskoordinator.DelMedArrangor,
                    request.endretAv,
                ).toDeltakereResponse()
            call.respond(oppdaterteDeltakere)
        }

        post("$apiPath/tildel-plass") {
            val request = call.receive<DeltakereRequest>()
            val deltakerIder = request.deltakere
            val oppdaterteDeltakere = deltakerService
                .oppdaterDeltakere(
                    deltakerIder,
                    EndringFraTiltakskoordinator.TildelPlass,
                    request.endretAv,
                ).toDeltakereResponse()

            call.respond(oppdaterteDeltakere)
        }

        post("$apiPath/sett-paa-venteliste") {
            val request = call.receive<DeltakereRequest>()
            val deltakerIder = request.deltakere
            val oppdaterteDeltakere = deltakerService
                .oppdaterDeltakere(
                    deltakerIder,
                    EndringFraTiltakskoordinator.SettPaaVenteliste,
                    request.endretAv,
                ).toDeltakereResponse()

            call.respond(oppdaterteDeltakere)
        }

        post("$apiPath/gi-avslag") {
            val request = call.receive<AvslagRequest>()
            val deltakeroppdatering = deltakerService
                .giAvslag(
                    request.deltakerId,
                    request.avslag,
                    request.endretAv,
                ).toDeltakerOppdatering(deltakerService.getHistorikk(request.deltakerId))

            call.respond(deltakeroppdatering)
        }
    }
}

data class DeltakereRequest(
    val deltakere: List<UUID>,
    val endretAv: String,
)

data class AvslagRequest(
    val deltakerId: UUID,
    val avslag: EndringFraTiltakskoordinator.Avslag,
    val endretAv: String,
)

fun DeltakerOppdateringResult.toDeltakerResponse(historikk: List<DeltakerHistorikk>): DeltakerOppdateringResponse {
    val feilkode = when (exceptionOrNull) {
        is IllegalStateException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
        is IllegalArgumentException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
        is SQLException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
        is SocketTimeoutException -> DeltakerOppdateringFeilkode.MIDLERTIDIG_FEIL
        is SocketException -> DeltakerOppdateringFeilkode.MIDLERTIDIG_FEIL
        is Exception -> null
        else -> null
    }
    return DeltakerOppdateringResponse(
        id = deltaker.id,
        startdato = deltaker.startdato,
        sluttdato = deltaker.sluttdato,
        dagerPerUke = deltaker.dagerPerUke,
        deltakelsesprosent = deltaker.deltakelsesprosent,
        bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
        deltakelsesinnhold = deltaker.deltakelsesinnhold,
        status = deltaker.status,
        historikk = historikk,
        sistEndret = deltaker.sistEndret,
        erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
        feilkode = feilkode,
    )
}
