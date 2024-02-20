package no.nav.amt.deltaker.deltaker.api

import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.api.model.OpprettKladdRequest

fun Routing.registerPameldingApi(
    pameldingService: PameldingService,
) {
    authenticate("SYSTEM") {
        post("/pamelding") {
            val request = call.receive<OpprettKladdRequest>()

            val deltaker = pameldingService.opprettKladd(
                deltakerlisteId = request.deltakerlisteId,
                personident = request.personident,
                opprettetAv = request.opprettetAv,
                opprettetAvEnhet = request.opprettetAvEnhet,
            )

            call.respond(deltaker)
        }
    }
}
