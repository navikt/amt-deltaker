package no.nav.amt.deltaker.hendelse.model

import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import java.util.UUID

data class HendelseDeltaker(
    val id: UUID,
    val personident: String,
    val deltakerliste: Deltakerliste,
) {
    data class Deltakerliste(
        val id: UUID,
        val navn: String,
        val arrangor: Arrangor,
        val tiltak: Tiltak,
    ) {
        data class Arrangor(
            val id: UUID,
            val organisasjonsnummer: String,
            val navn: String,
            val overordnetArrangor: Arrangor?,
        )

        data class Tiltak(
            val navn: String,
            val type: Tiltakstype.ArenaKode,
            val ledetekst: String?,
        )
    }
}

fun Deltaker.toHendelseDeltaker(overordnetArrangor: Arrangor?) = HendelseDeltaker(
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
        ),
    ),
)

private fun Arrangor.toHendelseArrangor(overordnetArrangor: HendelseDeltaker.Deltakerliste.Arrangor? = null) =
    HendelseDeltaker.Deltakerliste.Arrangor(
        id,
        organisasjonsnummer,
        navn,
        overordnetArrangor,
    )
