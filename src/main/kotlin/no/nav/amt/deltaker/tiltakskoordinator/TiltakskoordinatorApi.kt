package no.nav.amt.deltaker.tiltakskoordinator

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import no.nav.amt.lib.models.tiltakskoordinator.response.EndringFraTiltakskoordinatorResponse

fun Routing.registerTiltakskoordinatorApi(deltakerService: DeltakerService) {
    val apiPath = "/tiltakskoordinator/deltakere"

    authenticate("SYSTEM") {
        post("$apiPath/del-med-arrangor") {
            val request = call.receive<DelMedArrangorRequest>()

            val deltakere = deltakerService.upsertEndretDeltakere(request)
            call.respond(deltakere.map { it.toResponse() })
        }
    }
}

private fun Deltaker.toResponse() = EndringFraTiltakskoordinatorResponse(id, erManueltDeltMedArrangor, sistEndret)
