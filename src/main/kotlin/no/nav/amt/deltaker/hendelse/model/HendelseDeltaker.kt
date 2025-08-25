package no.nav.amt.deltaker.hendelse.model

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.hendelse.HendelseDeltaker
import java.time.LocalDate

fun Deltaker.toHendelseDeltaker(overordnetArrangor: Arrangor?, forsteVedtakFattet: LocalDate?) = HendelseDeltaker(
    id = id,
    personident = navBruker.personident,
    deltakerliste = HendelseDeltaker.Deltakerliste(
        id = deltakerliste.id,
        navn = deltakerliste.navn,
        arrangor = deltakerliste.arrangor.toHendelseArrangor(overordnetArrangor?.toHendelseArrangor()),
        startdato = deltakerliste.startDato,
        sluttdato = deltakerliste.sluttDato,
        oppstartstype = HendelseDeltaker.Deltakerliste.Oppstartstype.valueOf(deltakerliste.oppstart.toString()),
        tiltak = HendelseDeltaker.Deltakerliste.Tiltak(
            navn = deltakerliste.tiltakstype.visningsnavn,
            type = deltakerliste.tiltakstype.arenaKode,
            ledetekst = deltakerliste.tiltakstype.innhold?.ledetekst,
            tiltakskode = deltakerliste.tiltakstype.tiltakskode,
        ),
    ),
    forsteVedtakFattet = forsteVedtakFattet,
    opprettetDato = opprettet?.toLocalDate(),
)

private fun Arrangor.toHendelseArrangor(overordnetArrangor: HendelseDeltaker.Deltakerliste.Arrangor? = null) =
    HendelseDeltaker.Deltakerliste.Arrangor(
        id,
        organisasjonsnummer,
        navn,
        overordnetArrangor,
    )
