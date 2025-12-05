package no.nav.amt.deltaker.deltaker.api.deltaker

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.DtoMappers.deltakerEndringResponseFromDeltaker
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvbrytDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndreAvslutningRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.FjernOppstartsdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.IkkeAktuellRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.InnholdRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttarsakRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.StartdatoRequest
import java.time.ZonedDateTime
import java.util.UUID

fun Routing.registerDeltakerApi(deltakerService: DeltakerService, historikkService: DeltakerHistorikkService) {
    authenticate("SYSTEM") {
        get("/deltaker/{deltakerId}") {
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])
            val deltaker = deltakerService
                .get(deltakerId)
                .onFailure { call.respond(HttpStatusCode.NotFound) }
                .getOrThrow()

            call.respond(deltaker)
        }

        post("/deltaker/{deltakerId}/bakgrunnsinformasjon") {
            val request = call.receive<BakgrunnsinformasjonRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/innhold") {
            val request = call.receive<InnholdRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/deltakelsesmengde") {
            val request = call.receive<DeltakelsesmengdeRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/startdato") {
            val request = call.receive<StartdatoRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }
        post("/deltaker/{deltakerId}/sluttdato") {
            val request = call.receive<SluttdatoRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/sluttarsak") {
            val request = call.receive<SluttarsakRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/forleng") {
            val request = call.receive<ForlengDeltakelseRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/ikke-aktuell") {
            val request = call.receive<IkkeAktuellRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/avbryt") {
            val request = call.receive<AvbrytDeltakelseRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/avslutt") {
            val request = call.receive<AvsluttDeltakelseRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/endre-avslutning") {
            val request = call.receive<EndreAvslutningRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/reaktiver") {
            val request = call.receive<ReaktiverDeltakelseRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/fjern-oppstartsdato") {
            val request = call.receive<FjernOppstartsdatoRequest>()
            call.handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/sist-besokt") {
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])
            val sistBesokt = call.receive<ZonedDateTime>()

            deltakerService.oppdaterSistBesokt(deltakerId, sistBesokt)
            call.respond(HttpStatusCode.OK)
        }
    }
}

private suspend fun ApplicationCall.handleDeltakerEndring(
    deltakerService: DeltakerService,
    endringRequest: EndringRequest,
    historikkService: DeltakerHistorikkService,
) {
    val deltakerId = UUID.fromString(this.parameters["deltakerId"])

    val deltaker = deltakerService.upsertEndretDeltaker(deltakerId, endringRequest)
    val historikk = historikkService.getForDeltaker(deltaker.id)

    this.respond(deltakerEndringResponseFromDeltaker(deltaker, historikk))
}
