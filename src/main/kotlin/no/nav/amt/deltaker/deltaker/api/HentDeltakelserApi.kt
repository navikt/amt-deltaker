package no.nav.amt.deltaker.deltaker.api

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.model.DeltakelserRequest
import no.nav.amt.deltaker.deltaker.api.model.DeltakelserResponseMapper
import no.nav.amt.deltaker.unleash.UnleashToggle

fun Routing.registerHentDeltakelserApi(
    tilgangskontrollService: TilgangskontrollService,
    deltakerService: DeltakerService,
    deltakelserResponseMapper: DeltakelserResponseMapper,
    unleashToggle: UnleashToggle,
) {
    // API til valp hvor de henter ut alle deltakelser til person
    authenticate("VEILEDER") {
        post("/deltakelser") {
            val request = call.receive<DeltakelserRequest>()
            tilgangskontrollService.verifiserLesetilgang(call.getNavAnsattAzureId(), request.norskIdent)

            val deltakelser = deltakerService.getDeltakelserForPerson(request.norskIdent)
                .filter {
                    unleashToggle.erKometMasterForTiltakstype(it.deltakerliste.tiltakstype.arenaKode) ||
                        (unleashToggle.skalLeseArenaDeltakereForTiltakstype(it.deltakerliste.tiltakstype.arenaKode) && Environment.isDev())
                }

            call.respond(deltakelserResponseMapper.toDeltakelserResponse(deltakelser))
        }
    }
}
