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
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.extensions.getDeltakerId
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

fun Routing.registerDeltakerApi(
    deltakerRepository: DeltakerRepository,
    deltakerService: DeltakerService,
    historikkService: DeltakerHistorikkService,
) {
    suspend fun ApplicationCall.handleDeltakerEndring(endringRequest: EndringRequest) {
        val deltakerId = this.getDeltakerId()

        val deltaker = deltakerService.upsertEndretDeltaker(deltakerId, endringRequest)
        val historikk = historikkService.getForDeltaker(deltaker.id)

        this.respond(deltakerEndringResponseFromDeltaker(deltaker, historikk))
    }

    authenticate("SYSTEM") {
        get("/deltaker/{deltakerId}") {
            val deltaker = deltakerRepository
                .get(call.getDeltakerId())
                .onFailure { call.respond(HttpStatusCode.NotFound) }
                .getOrThrow()

            call.respond(deltaker)
        }

        post("/deltaker/{deltakerId}/endre-deltaker") {
            call.handleDeltakerEndring(call.receive<EndringRequest>())
        }

        post("/deltaker/{deltakerId}/bakgrunnsinformasjon") {
            call.handleDeltakerEndring(call.receive<BakgrunnsinformasjonRequest>())
        }

        post("/deltaker/{deltakerId}/innhold") {
            call.handleDeltakerEndring(call.receive<InnholdRequest>())
        }

        post("/deltaker/{deltakerId}/deltakelsesmengde") {
            call.handleDeltakerEndring(call.receive<DeltakelsesmengdeRequest>())
        }

        post("/deltaker/{deltakerId}/startdato") {
            call.handleDeltakerEndring(call.receive<StartdatoRequest>())
        }
        post("/deltaker/{deltakerId}/sluttdato") {
            call.handleDeltakerEndring(call.receive<SluttdatoRequest>())
        }

        post("/deltaker/{deltakerId}/sluttarsak") {
            call.handleDeltakerEndring(call.receive<SluttarsakRequest>())
        }

        post("/deltaker/{deltakerId}/forleng") {
            call.handleDeltakerEndring(call.receive<ForlengDeltakelseRequest>())
        }

        post("/deltaker/{deltakerId}/ikke-aktuell") {
            call.handleDeltakerEndring(call.receive<IkkeAktuellRequest>())
        }

        post("/deltaker/{deltakerId}/avbryt") {
            call.handleDeltakerEndring(call.receive<AvbrytDeltakelseRequest>())
        }

        post("/deltaker/{deltakerId}/avslutt") {
            call.handleDeltakerEndring(call.receive<AvsluttDeltakelseRequest>())
        }

        post("/deltaker/{deltakerId}/endre-avslutning") {
            call.handleDeltakerEndring(call.receive<EndreAvslutningRequest>())
        }

        post("/deltaker/{deltakerId}/reaktiver") {
            call.handleDeltakerEndring(call.receive<ReaktiverDeltakelseRequest>())
        }

        post("/deltaker/{deltakerId}/fjern-oppstartsdato") {
            call.handleDeltakerEndring(call.receive<FjernOppstartsdatoRequest>())
        }

        post("/deltaker/{deltakerId}/sist-besokt") {
            deltakerService.oppdaterSistBesokt(
                deltakerId = call.getDeltakerId(),
                sistBesokt = call.receive<ZonedDateTime>(),
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}
