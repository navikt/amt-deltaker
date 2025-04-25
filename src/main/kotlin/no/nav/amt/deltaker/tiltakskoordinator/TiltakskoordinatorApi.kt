package no.nav.amt.deltaker.tiltakskoordinator

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import java.util.UUID

fun Routing.registerTiltakskoordinatorApi(deltakerService: DeltakerService) {
    val apiPath = "/tiltakskoordinator/deltakere"

    fun List<Deltaker>.toDeltakereResponse() = this.map { it.toDeltakerOppdatering(deltakerService.getHistorikk(it.id)) }

    authenticate("SYSTEM") {
        post("$apiPath/del-med-arrangor") {
            val request = call.receive<DelMedArrangorRequest>()

            val oppdaterteDeltakere = deltakerService.upsertEndretDeltakere(
                request.deltakerIder,
                EndringFraTiltakskoordinator.DelMedArrangor,
                request.endretAv,
            ).toDeltakereResponse()
            call.respond(oppdaterteDeltakere)
        }

        post("$apiPath/del-med-arrangor-v2") {
            val request = call.receive<DelMedArrangorRequest>()

            val oppdaterteDeltakere = deltakerService.upsertEndretDeltakere(
                request.deltakerIder,
                EndringFraTiltakskoordinator.DelMedArrangor,
                request.endretAv,
            ).toDeltakereResponse()
            call.respond(oppdaterteDeltakere)
        }

        post("$apiPath/tildel-plass") {
            val request = call.receive<DeltakereRequest>()
            val deltakerIder = request.deltakere
            val oppdaterteDeltakere = deltakerService.upsertEndretDeltakere(
                deltakerIder,
                EndringFraTiltakskoordinator.TildelPlass,
                request.endretAv,
            ).toDeltakereResponse()

            call.respond(oppdaterteDeltakere)
        }

        post("$apiPath/sett-paa-venteliste") {
            val request = call.receive<DeltakereRequest>()
            val deltakerIder = request.deltakere
            val oppdaterteDeltakere = deltakerService.upsertEndretDeltakere(
                deltakerIder,
                EndringFraTiltakskoordinator.SettPaaVenteliste,
                request.endretAv,
            ).toDeltakereResponse()

            call.respond(oppdaterteDeltakere)
        }
    }
}

data class DeltakereRequest(
    val deltakere: List<UUID>,
    val endretAv: String,
)
