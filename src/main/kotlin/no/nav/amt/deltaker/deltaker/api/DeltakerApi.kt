package no.nav.amt.deltaker.deltaker.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.pipeline.PipelineContext
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.model.AvsluttDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.model.DeltakelsesmengdeRequest
import no.nav.amt.deltaker.deltaker.api.model.EndringRequest
import no.nav.amt.deltaker.deltaker.api.model.ForlengDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.IkkeAktuellRequest
import no.nav.amt.deltaker.deltaker.api.model.InnholdRequest
import no.nav.amt.deltaker.deltaker.api.model.ReaktiverDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttarsakRequest
import no.nav.amt.deltaker.deltaker.api.model.SluttdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.StartdatoRequest
import no.nav.amt.deltaker.deltaker.api.model.toDeltakerEndringResponse
import java.time.ZonedDateTime
import java.util.UUID

fun Routing.registerDeltakerApi(deltakerService: DeltakerService, historikkService: DeltakerHistorikkService) {
    authenticate("SYSTEM") {
        get("/deltaker/{deltakerId}") {
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])
            val deltaker = deltakerService.get(deltakerId)
                .onFailure { call.respond(HttpStatusCode.NotFound) }
                .getOrThrow()

            call.respond(deltaker)
        }

        post("/deltaker/{deltakerId}/bakgrunnsinformasjon") {
            val request = call.receive<BakgrunnsinformasjonRequest>()
            handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/innhold") {
            val request = call.receive<InnholdRequest>()
            handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/deltakelsesmengde") {
            val request = call.receive<DeltakelsesmengdeRequest>()
            handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/startdato") {
            val request = call.receive<StartdatoRequest>()
            handleDeltakerEndring(deltakerService, request, historikkService)
        }
        post("/deltaker/{deltakerId}/sluttdato") {
            val request = call.receive<SluttdatoRequest>()
            handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/sluttarsak") {
            val request = call.receive<SluttarsakRequest>()
            handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/forleng") {
            val request = call.receive<ForlengDeltakelseRequest>()
            handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/ikke-aktuell") {
            val request = call.receive<IkkeAktuellRequest>()
            handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/avslutt") {
            val request = call.receive<AvsluttDeltakelseRequest>()
            handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/reaktiver") {
            val request = call.receive<ReaktiverDeltakelseRequest>()
            handleDeltakerEndring(deltakerService, request, historikkService)
        }

        post("/deltaker/{deltakerId}/vedtak/{vedtakId}/fatt") {
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])
            val vedtakId = UUID.fromString(call.parameters["vedtakId"])

            val deltaker = deltakerService.fattVedtak(deltakerId, vedtakId)
            val historikk = historikkService.getForDeltaker(deltaker.id)

            call.respond(deltaker.toDeltakerEndringResponse(historikk))
        }

        post("/deltaker/{deltakerId}/sist-besokt") {
            val deltakerId = UUID.fromString(call.parameters["deltakerId"])
            val sistBesokt = call.receive<ZonedDateTime>()

            deltakerService.oppdaterSistBesokt(deltakerId, sistBesokt)
            call.respond(HttpStatusCode.OK)
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.handleDeltakerEndring(
    deltakerService: DeltakerService,
    request: EndringRequest,
    historikkService: DeltakerHistorikkService,
) {
    val deltakerId = UUID.fromString(call.parameters["deltakerId"])

    val deltaker = deltakerService.upsertEndretDeltaker(deltakerId, request)
    val historikk = historikkService.getForDeltaker(deltaker.id)

    call.respond(deltaker.toDeltakerEndringResponse(historikk))
}
