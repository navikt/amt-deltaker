package no.nav.amt.deltaker.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import java.util.UUID

fun Routing.registerDeltakerApi(
    deltakerService: DeltakerService,
) {
    authenticate("SYSTEM") {
        post("/deltaker/{deltakerId}/bakgrunnsinformasjon") {
            val request = call.receive<BakgrunnsinformasjonRequest>()
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])

            deltakerService.upsertEndretDeltaker(deltakerId, request)
            call.respond(HttpStatusCode.OK)
        }
    }
}
