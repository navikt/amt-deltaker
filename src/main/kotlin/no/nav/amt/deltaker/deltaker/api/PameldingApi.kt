package no.nav.amt.deltaker.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.api.model.OpprettKladdRequest
import no.nav.amt.deltaker.deltaker.api.model.UtkastRequest
import java.util.UUID

fun Routing.registerPameldingApi(
    pameldingService: PameldingService,
) {
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

            pameldingService.upsertUtkast(deltakerId, request)
            call.respond(HttpStatusCode.OK)
        }
    }
}
