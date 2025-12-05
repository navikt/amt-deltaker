package no.nav.amt.deltaker.external.api

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.external.data.ArrangorResponse
import no.nav.amt.deltaker.external.data.DeltakerResponse
import no.nav.amt.deltaker.external.data.GjennomforingResponse
import no.nav.amt.lib.models.deltaker.DeltakerStatus

fun DeltakerStatus.erAktiv() = this.type in listOf(
    DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
    DeltakerStatus.Type.VENTER_PA_OPPSTART,
    DeltakerStatus.Type.DELTAR,
    DeltakerStatus.Type.SOKT_INN,
    DeltakerStatus.Type.VURDERES,
    DeltakerStatus.Type.VENTELISTE,
)

fun List<Deltaker>.toResponse() = this.map { deltaker -> deltaker.toResponse() }

fun Deltaker.toResponse() = DeltakerResponse(
    id = id,
    gjennomforing = deltakerliste.toResponse(),
    startDato = startdato,
    sluttDato = sluttdato,
    status = status.type,
    dagerPerUke = dagerPerUke,
    prosentStilling = deltakelsesprosent,
    registrertDato = opprettet,
)

private fun Deltakerliste.toResponse() = GjennomforingResponse(
    id = id,
    navn = navn,
    type = tiltakstype.tiltakskode.toArenaKode().name, // kan ikke endres, benyttes av tiltakspenger
    tiltakskode = tiltakstype.tiltakskode,
    tiltakstypeNavn = tiltakstype.navn,
    arrangor = ArrangorResponse(
        navn = arrangor.navn,
        virksomhetsnummer = arrangor.organisasjonsnummer,
    ),
)
