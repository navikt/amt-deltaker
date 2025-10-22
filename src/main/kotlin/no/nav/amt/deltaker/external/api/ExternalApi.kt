package no.nav.amt.deltaker.external.api

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.external.data.ArrangorResponse
import no.nav.amt.deltaker.external.data.DeltakelserResponseMapper
import no.nav.amt.deltaker.external.data.DeltakerPersonaliaResponse
import no.nav.amt.deltaker.external.data.DeltakerResponse
import no.nav.amt.deltaker.external.data.GjennomforingResponse
import no.nav.amt.deltaker.external.data.HarAktiveDeltakelserResponse
import no.nav.amt.deltaker.external.data.HentDeltakelserRequest
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

fun Routing.registerExternalApi(
    deltakerService: DeltakerService,
    navEnhetService: NavEnhetService,
    tilgangskontrollService: TilgangskontrollService,
    deltakelserResponseMapper: DeltakelserResponseMapper,
    unleashToggle: UnleashToggle,
) {
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
            val deltakelser = deltakerService
                .getDeltakelserForPerson(request.norskIdent)
                .filter { deltaker -> deltaker.status.type != DeltakerStatus.Type.KLADD }

            call.respond(deltakelser.toResponse())
        }
    }

    authenticate("MULIGHETSROMMET-SYSTEM") {
        // Brukes av mulighetsrommet for å hente personalia på deltakere i deres økonomi-løsning
        // Dokumentasjon: docs/external-deltakere-personalia.md
        post("$apiPath/deltakere/personalia") {
            val request = call.receive<List<DeltakerID>>()
            val deltakere: List<Deltaker> = deltakerService.getDeltakelser(request)
            val navEnheter = navEnhetService.getEnheter(deltakere.mapNotNull { it.navBruker.navEnhetId }.toSet())

            call.respond(deltakere.map { DeltakerPersonaliaResponse.from(it, navEnheter) })
        }
    }

    authenticate("VEILEDER") {
        post("/deltakelser") {
            // API til valp hvor de henter ut alle deltakelser til person
            val request = call.receive<HentDeltakelserRequest>()
            tilgangskontrollService.verifiserLesetilgang(call.getNavAnsattAzureId(), request.norskIdent)

            val deltakelser = deltakerService
                .getDeltakelserForPerson(request.norskIdent)
                .filter {
                    unleashToggle.erKometMasterForTiltakstype(it.deltakerliste.tiltakstype.tiltakskode) ||
                        (unleashToggle.skalLeseArenaDataForTiltakstype(it.deltakerliste.tiltakstype.tiltakskode) && Environment.isDev())
                }

            val responseBody = deltakelserResponseMapper.toDeltakelserResponse(deltakelser)
            call.respond(responseBody)
        }
    }
}

private typealias DeltakerID = UUID

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
