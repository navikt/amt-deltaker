package no.nav.amt.deltaker.tiltakskoordinator

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import no.nav.amt.lib.models.tiltakskoordinator.response.EndringFraTiltakskoordinatorResponse
import org.slf4j.LoggerFactory
import java.util.UUID

fun Routing.registerTiltakskoordinatorApi(deltakerService: DeltakerService, unleashToggle: UnleashToggle) {
    val log = LoggerFactory.getLogger(javaClass)
    val apiPath = "/tiltakskoordinator/deltakere"

    authenticate("SYSTEM") {
        post("$apiPath/del-med-arrangor") {
            val request = call.receive<DelMedArrangorRequest>()

            val deltakere = deltakerService.upsertEndretDeltakere(request)
            call.respond(deltakere.map { it.toResponse() })
        }

        post("$apiPath/sett-paa-venteliste") {
            val request = call.receive<DeltakereRequest>()
            val deltakere = deltakerService.getDeltakelser(
                request.deltakere,
            ).filter { it.deltakerliste.id == request.deltakerlisteId }

            if (!unleashToggle.erKometMasterForTiltakstype(deltakere.first().deltakerliste.tiltakstype.arenaKode)) {
                log.error("Operasjon er ikke tillatt før komet er master")
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            if (request.deltakere.size > deltakere.size) {
                log.error(
                    "Alle deltakere i bulk operasjon må være på samme deltakerliste. " +
                        "deltakere: ${request.deltakere}, " +
                        "deltakerliste: ${request.deltakerlisteId}",
                )
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val oppdaterteDeltakere = deltakerService.settPaaVenteliste(deltakere)

            call.respond(oppdaterteDeltakere)
        }
    }
}

data class DeltakereRequest(
    val deltakere: List<UUID>,
    val deltakerlisteId: UUID,
)

private fun Deltaker.toResponse() = EndringFraTiltakskoordinatorResponse(id, erManueltDeltMedArrangor, sistEndret)
