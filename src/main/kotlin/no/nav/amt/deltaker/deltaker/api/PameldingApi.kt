package no.nav.amt.deltaker.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.OpprettKladdRequestValidator
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.api.model.request.AvbrytUtkastRequest
import no.nav.amt.deltaker.deltaker.api.model.request.OpprettKladdRequest
import no.nav.amt.deltaker.deltaker.api.model.request.UtkastRequest
import no.nav.amt.deltaker.deltaker.api.model.toDeltakerEndringResponse
import no.nav.amt.deltaker.deltaker.api.model.toKladdResponse
import java.util.UUID

fun Routing.registerPameldingApi(
    opprettKladdRequestValidator: OpprettKladdRequestValidator,
    pameldingService: PameldingService,
    historikkService: DeltakerHistorikkService,
) {
    authenticate("SYSTEM") {
        post("/pamelding") {
            val opprettKladdRequest = call.receive<OpprettKladdRequest>()

            val validationResult = opprettKladdRequestValidator.validateRequest(opprettKladdRequest)
            if (validationResult is ValidationResult.Invalid) {
                throw RequestValidationException(opprettKladdRequest, validationResult.reasons)
            }

            val deltaker = pameldingService.opprettDeltaker(
                deltakerListeId = opprettKladdRequest.deltakerlisteId,
                personIdent = opprettKladdRequest.personident,
            )

            call.respond(deltaker.toKladdResponse())
        }

        post("/pamelding/{deltakerId}") {
            val request = call.receive<UtkastRequest>()
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])

            val deltaker = pameldingService.upsertUtkast(deltakerId, request)
            val historikk = historikkService.getForDeltaker(deltaker.id)
            call.respond(deltaker.toDeltakerEndringResponse(historikk))
        }

        post("/pamelding/{deltakerId}/innbygger/godkjenn-utkast") {
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])

            val oppdatertDeltaker = pameldingService.innbyggerGodkjennUtkast(deltakerId)
            val historikk = historikkService.getForDeltaker(oppdatertDeltaker.id)

            call.respond(oppdatertDeltaker.toDeltakerEndringResponse(historikk))
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
