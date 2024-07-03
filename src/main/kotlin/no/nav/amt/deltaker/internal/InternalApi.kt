package no.nav.amt.deltaker.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.auth.AuthorizationException
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.internal.konvertervedtak.KonverterVedtakService
import java.util.UUID

fun Routing.registerInternalApi(deltakerService: DeltakerService, konverterVedtakService: KonverterVedtakService) {
    post("/internal/feilregistrer/{deltakerId}") {
        if (isInternal(call.request.local.remoteAddress)) {
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])
            deltakerService.feilregistrerDeltaker(deltakerId)
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }

    post("/internal/konverter-vedtak") {
        if (isInternal(call.request.local.remoteAddress)) {
            konverterVedtakService.konverterVedtak()
            call.respond(HttpStatusCode.OK)
        } else {
            throw AuthorizationException("Ikke tilgang til api")
        }
    }
}

fun isInternal(remoteAdress: String): Boolean {
    return remoteAdress == "127.0.0.1"
}
