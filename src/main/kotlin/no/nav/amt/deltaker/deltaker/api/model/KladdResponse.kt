package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.navbruker.model.NavBruker
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class KladdResponse(
    val id: UUID,
    val navBruker: NavBruker,
    val deltakerliste: Deltakerliste,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val innhold: List<Innhold>,
    val status: DeltakerStatus,
    val sistEndretAv: NavAnsatt,
    val sistEndretAvEnhet: NavEnhet,
    val sistEndret: LocalDateTime,
    val opprettet: LocalDateTime,
)

fun Deltaker.toKladdResponse(sistEndretAv: NavAnsatt, sistEndretAvEnhet: NavEnhet): KladdResponse =
    KladdResponse(
        id = id,
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        innhold = innhold,
        status = status,
        sistEndretAv = sistEndretAv,
        sistEndretAvEnhet = sistEndretAvEnhet,
        sistEndret = sistEndret,
        opprettet = opprettet,
    )
