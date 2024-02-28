package no.nav.amt.deltaker.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.KladdService
import no.nav.amt.deltaker.deltaker.api.model.OppdaterDeltakerRequest
import no.nav.amt.deltaker.deltaker.api.model.OpprettKladdRequest

fun Routing.registerDeltakerApi(
    kladdService: KladdService,
    deltakerService: DeltakerService,
) {
    authenticate("SYSTEM") {
        post("/pamelding") {
            val request = call.receive<OpprettKladdRequest>()

            val deltaker = kladdService.opprettKladd(
                deltakerlisteId = request.deltakerlisteId,
                personident = request.personident,
                opprettetAv = request.opprettetAv,
                opprettetAvEnhet = request.opprettetAvEnhet,
            )

            call.respond(deltaker)
        }

        post("/deltaker") {
            val request = call.receive<OppdaterDeltakerRequest>()

            deltakerService.oppdaterDeltaker(request)
            call.respond(HttpStatusCode.OK)
        }
    }
}
