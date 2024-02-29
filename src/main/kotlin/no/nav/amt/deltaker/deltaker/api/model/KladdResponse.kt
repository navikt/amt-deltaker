package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.navbruker.model.NavBruker
import java.time.LocalDate
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
)

fun Deltaker.toKladdResponse(): KladdResponse =
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
    )
