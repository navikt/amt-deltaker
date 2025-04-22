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
import no.nav.amt.lib.models.tiltakskoordinator.response.EndringFraTiltakskoordinatorResponse
import java.util.UUID

fun Routing.registerTiltakskoordinatorApi(deltakerService: DeltakerService) {
    val apiPath = "/tiltakskoordinator/deltakere"

    fun List<Deltaker>.toDeltakereResponse() = this.map { it.toDeltakerOppdatering(deltakerService.getHistorikk(it.id)) }

    authenticate("SYSTEM") {
        post("$apiPath/del-med-arrangor") {
            val request = call.receive<DelMedArrangorRequest>()

            val deltakere = deltakerService.upsertEndretDeltakere(
                request.deltakerIder,
                EndringFraTiltakskoordinator.DelMedArrangor,
                request.endretAv,
            )
            call.respond(deltakere.map { it.toResponse() })
        }

        post("$apiPath/sett-paa-venteliste") {
            val request = call.receive<DeltakereRequest>()
            val deltakerIder = request.deltakere
            val oppdaterteDeltakere = deltakerService.upsertEndretDeltakere(
                deltakerIder,
                EndringFraTiltakskoordinator.DelMedArrangor,
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

private fun Deltaker.toResponse() = EndringFraTiltakskoordinatorResponse(id, erManueltDeltMedArrangor, sistEndret)
