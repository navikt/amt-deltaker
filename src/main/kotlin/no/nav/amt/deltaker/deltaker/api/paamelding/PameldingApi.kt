package no.nav.amt.deltaker.deltaker.api.paamelding

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.api.DtoMappers.opprettKladdResponseFromDeltaker
import no.nav.amt.deltaker.deltaker.api.DtoMappers.utkastResponseFromDeltaker
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.OpprettKladdRequest
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.UtkastRequest
import java.util.UUID

fun Routing.registerPameldingApi(pameldingService: PameldingService, historikkService: DeltakerHistorikkService) {
    authenticate("SYSTEM") {
        // pamelding/kladd
        post("/pamelding") {
            val opprettKladdRequest = call.receive<OpprettKladdRequest>()

            val deltaker = pameldingService.opprettDeltaker(
                deltakerListeId = opprettKladdRequest.deltakerlisteId,
                personIdent = opprettKladdRequest.personident,
            )

            call.respond(opprettKladdResponseFromDeltaker(deltaker))
        }

        /*
            Kalles av av frontend via amt-deltaker-bff med:
            /pamelding/{deltakerId} godkjentAvNav=false
            /pamelding/{deltakerId}/utenGodkjenning godkjentAvNav=true
         */
        post("/pamelding/{deltakerId}") {
            val request = call.receive<UtkastRequest>()
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])

            val deltaker = pameldingService.upsertUtkast(deltakerId, request)
            val historikk = historikkService.getForDeltaker(deltaker.id)
            call.respond(utkastResponseFromDeltaker(deltaker, historikk))
        }

        post("/pamelding/{deltakerId}/innbygger/godkjenn-utkast") {
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])

            val oppdatertDeltaker = pameldingService.innbyggerGodkjennUtkast(deltakerId)
            val historikk = historikkService.getForDeltaker(oppdatertDeltaker.id)

            call.respond(utkastResponseFromDeltaker(oppdatertDeltaker, historikk))
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
