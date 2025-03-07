package no.nav.amt.deltaker.varselhendelse.model

import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.lib.models.hendelse.HendelseDeltaker
import java.time.LocalDate

fun Deltaker.toHendelseDeltaker(overordnetArrangor: Arrangor?, forsteVedtakFattet: LocalDate?) = HendelseDeltaker(
    id = id,
    personident = navBruker.personident,
    deltakerliste = HendelseDeltaker.Deltakerliste(
        id = deltakerliste.id,
        navn = deltakerliste.navn,
        arrangor = deltakerliste.arrangor.toHendelseArrangor(overordnetArrangor?.toHendelseArrangor()),
        tiltak = HendelseDeltaker.Deltakerliste.Tiltak(
            navn = deltakerliste.tiltakstype.navn,
            type = deltakerliste.tiltakstype.arenaKode,
            ledetekst = deltakerliste.tiltakstype.innhold?.ledetekst,
            tiltakskode = deltakerliste.tiltakstype.tiltakskode,
        ),
    ),
    forsteVedtakFattet = forsteVedtakFattet,
)

private fun Arrangor.toHendelseArrangor(overordnetArrangor: HendelseDeltaker.Deltakerliste.Arrangor? = null) =
    HendelseDeltaker.Deltakerliste.Arrangor(
        id,
        organisasjonsnummer,
        navn,
        overordnetArrangor,
    )
