package no.nav.amt.deltaker.deltaker.model

import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.navbruker.model.NavBruker
import no.nav.amt.deltaker.tiltakskoordinator.Deltakeroppdatering
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerVedImport
import no.nav.amt.lib.models.deltaker.DeltakerVedVedtak
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Deltaker(
    val id: UUID,
    val navBruker: NavBruker,
    val deltakerliste: Deltakerliste,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val status: DeltakerStatus,
    val vedtaksinformasjon: Vedtaksinformasjon?,
    val sistEndret: LocalDateTime,
    val kilde: Kilde,
    val erManueltDeltMedArrangor: Boolean,
    val opprettet: LocalDateTime?,
) {
    fun harSluttet(): Boolean = status.type in AVSLUTTENDE_STATUSER

    fun deltarPaKurs(): Boolean = deltakerliste.erKurs()

    fun toDeltakerVedVedtak(): DeltakerVedVedtak = DeltakerVedVedtak(
        id = id,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = deltakelsesinnhold,
        status = status,
    )

    fun toDeltakerVedImport(innsoktDato: LocalDate) = DeltakerVedImport(
        deltakerId = id,
        innsoktDato = innsoktDato,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        status = status,
    )

    fun toDeltakerOppdatering(historikk: List<DeltakerHistorikk>): Deltakeroppdatering {
        return Deltakeroppdatering(
            id = id,
            startdato = startdato,
            sluttdato = sluttdato,
            dagerPerUke = dagerPerUke,
            deltakelsesprosent = deltakelsesprosent,
            bakgrunnsinformasjon = bakgrunnsinformasjon,
            deltakelsesinnhold = deltakelsesinnhold,
            status = status,
            historikk = historikk,
            sistEndret = sistEndret,
            erManueltDeltMedArrangor = erManueltDeltMedArrangor,
            forcedUpdate = false,
        )
    }

    data class Vedtaksinformasjon(
        val fattet: LocalDateTime?,
        val fattetAvNav: Boolean,
        val opprettet: LocalDateTime,
        val opprettetAv: UUID,
        val opprettetAvEnhet: UUID,
        val sistEndret: LocalDateTime,
        val sistEndretAv: UUID,
        val sistEndretAvEnhet: UUID,
    )
}
