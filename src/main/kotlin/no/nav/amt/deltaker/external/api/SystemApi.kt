package no.nav.amt.deltaker.external.api

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.external.data.ArrangorResponse
import no.nav.amt.deltaker.external.data.DeltakerResponse
import no.nav.amt.deltaker.external.data.GjennomforingResponse
import no.nav.amt.deltaker.external.data.HarAktiveDeltakelserResponse
import no.nav.amt.deltaker.external.data.HentDeltakelserRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun Routing.registerNavInternApi(deltakerService: DeltakerService) {
    val apiPath = "/external"

    authenticate("EXTERNAL-SYSTEM") {
        post("$apiPath/aktiv-deltaker") {
            // Brukes av veilarboppfolging til å bestemme om oppfølgingsperioden kan avsluttes
            val request = call.receive<HentDeltakelserRequest>()
            val deltakelser = deltakerService.getDeltakelserForPerson(request.norskIdent)
            val harAktiveDeltakelser = deltakelser.any { deltaker -> deltaker.status.erAktiv() }
            call.respond(HarAktiveDeltakelserResponse(harAktiveDeltakelser))
        }

        post("$apiPath/deltakelser") {
            // brukes av tiltakspenger for å vise tiltak for saksbehandler og i søknadsdialog
            val request = call.receive<HentDeltakelserRequest>()
            val deltakelser = deltakerService.getDeltakelserForPerson(request.norskIdent)

            call.respond(deltakelser.toResponse())
        }
    }
}

fun DeltakerStatus.erAktiv() = this.type in listOf(
    DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
    DeltakerStatus.Type.VENTER_PA_OPPSTART,
    DeltakerStatus.Type.DELTAR,
    DeltakerStatus.Type.SOKT_INN,
    DeltakerStatus.Type.VURDERES,
    DeltakerStatus.Type.VENTELISTE,
)

fun Deltakerliste.toResponse() = GjennomforingResponse(
    id = id,
    navn = navn,
    type = tiltakstype.arenaKode.name,
    tiltakstypeNavn = tiltakstype.navn,
    arrangor = ArrangorResponse(
        navn = arrangor.navn,
        virksomhetsnummer = arrangor.organisasjonsnummer,
    ),
)

fun Deltaker.toResponse() = DeltakerResponse(
    id = id,
    gjennomforing = deltakerliste.toResponse(),
    startDato = startdato,
    sluttDato = sluttdato,
    status = status.type,
    dagerPerUke = dagerPerUke,
    prosentStilling = deltakelsesprosent,
    registrertDato = opprettet ?: throw IllegalStateException("Ugyldig registrertDato"),
)

fun List<Deltaker>.toResponse() = this.map { deltaker -> deltaker.toResponse() }
