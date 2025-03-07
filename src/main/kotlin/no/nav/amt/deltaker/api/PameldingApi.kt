package no.nav.amt.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import no.nav.amt.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.api.model.AvbrytUtkastRequest
import no.nav.amt.deltaker.api.model.OpprettKladdRequest
import no.nav.amt.deltaker.api.model.UtkastRequest
import no.nav.amt.deltaker.api.model.toDeltakerEndringResponse
import no.nav.amt.deltaker.deltaker.PameldingService
import java.util.UUID

fun Routing.registerPameldingApi(pameldingService: PameldingService, historikkService: DeltakerHistorikkService) {
    authenticate("SYSTEM") {
        post("/pamelding") {
            val request = call.receive<OpprettKladdRequest>()

            val deltaker = pameldingService.opprettKladd(
                deltakerlisteId = request.deltakerlisteId,
                personident = request.personident,
            )

            call.respond(deltaker)
        }

        post("/pamelding/{deltakerId}") {
            val request = call.receive<UtkastRequest>()
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])

            val deltaker = pameldingService.upsertUtkast(deltakerId, request)
            val historikk = historikkService.getForDeltaker(deltaker.id)
            call.respond(deltaker.toDeltakerEndringResponse(historikk))
        }

        post("/pamelding/{deltakerId}/avbryt") {
            val request = call.receive<AvbrytUtkastRequest>()
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])

            pameldingService.avbrytUtkast(deltakerId, request)
            call.respond(HttpStatusCode.OK)
        }

        delete("/pamelding/{deltakerId}") {
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])
            pameldingService.slettKladd(deltakerId)
            call.respond(HttpStatusCode.OK)
        }
    }
}
