package no.nav.amt.deltaker.tiltakskoordinator.api

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerOppdateringResult
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.tiltakskoordinator.api.DtoMappers.fromDeltakerOppdateringResult
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.request.DeltakereRequest
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.request.GiAvslagRequest
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest

fun Routing.registerTiltakskoordinatorApi(deltakerService: DeltakerService) {
    val apiPath = "/tiltakskoordinator/deltakere"

    fun List<DeltakerOppdateringResult>.toDeltakerOppdateringResult() = this.map {
        fromDeltakerOppdateringResult(
            oppdateringResult = it,
            historikk = deltakerService.getHistorikk(it.deltaker.id),
        )
    }

    authenticate("SYSTEM") {
        post("$apiPath/del-med-arrangor") {
            val request = call.receive<DelMedArrangorRequest>()

            val oppdaterteDeltakere = deltakerService
                .oppdaterDeltakere(
                    request.deltakerIder,
                    EndringFraTiltakskoordinator.DelMedArrangor,
                    request.endretAv,
                ).toDeltakerOppdateringResult()
            call.respond(oppdaterteDeltakere)
        }

        post("$apiPath/tildel-plass") {
            val request = call.receive<DeltakereRequest>()
            val deltakerIder = request.deltakere
            val oppdaterteDeltakere = deltakerService
                .oppdaterDeltakere(
                    deltakerIder,
                    EndringFraTiltakskoordinator.TildelPlass,
                    request.endretAv,
                ).toDeltakerOppdateringResult()

            call.respond(oppdaterteDeltakere)
        }

        post("$apiPath/sett-paa-venteliste") {
            val request = call.receive<DeltakereRequest>()
            val deltakerIder = request.deltakere
            val oppdaterteDeltakere = deltakerService
                .oppdaterDeltakere(
                    deltakerIder,
                    EndringFraTiltakskoordinator.SettPaaVenteliste,
                    request.endretAv,
                ).toDeltakerOppdateringResult()

            call.respond(oppdaterteDeltakere)
        }

        post("$apiPath/gi-avslag") {
            val request = call.receive<GiAvslagRequest>()
            val deltakeroppdatering = deltakerService
                .giAvslag(
                    request.deltakerId,
                    request.avslag,
                    request.endretAv,
                ).toDeltakerOppdatering(deltakerService.getHistorikk(request.deltakerId))

            call.respond(deltakeroppdatering)
        }
    }
}
