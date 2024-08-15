package no.nav.amt.deltaker.deltaker.api

import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.model.DeltakelserRequest
import no.nav.amt.deltaker.deltaker.api.model.DeltakelserResponseMapper
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype

fun Routing.registerHentDeltakelserApi(
    tilgangskontrollService: TilgangskontrollService,
    deltakerService: DeltakerService,
    deltakelserResponseMapper: DeltakelserResponseMapper,
) {
    authenticate("VEILEDER") {
        post("/deltakelser") {
            val request = call.receive<DeltakelserRequest>()
            tilgangskontrollService.verifiserLesetilgang(getNavAnsattAzureId(), request.norskIdent)

            // filtrerer bort deltakelser som ikke er AFT for å få dev til å se mest mulig prodlik ut
            val deltakelser = deltakerService.getDeltakelser(request.norskIdent)
                .filter { it.deltakerliste.tiltakstype.tiltakskode == Tiltakstype.Tiltakskode.ARBEIDSFORBEREDENDE_TRENING }

            call.respond(deltakelserResponseMapper.toDeltakelserResponse(deltakelser))
        }
    }
}
