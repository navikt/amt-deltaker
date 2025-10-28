package no.nav.amt.deltaker.deltaker.kafka.dto

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.person.NavBruker
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// Denne klassen utgår når vi har blitt master for enkeltplassdeltakere
data class EnkeltplassDeltakerPayload(
    val id: UUID,
    val gjennomforingId: UUID,
    val personIdent: String,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val status: DeltakerStatus.Type,
    val statusAarsak: DeltakerStatus.Aarsak?,
    val dagerPerUke: Float?,
    val prosentDeltid: Float?,
    val registrertDato: LocalDateTime,
    val statusEndretDato: LocalDateTime?,
    val innsokBegrunnelse: String?,
) {
    fun toDeltaker(
        deltakerliste: Deltakerliste,
        navBruker: NavBruker,
        forrigeDeltakerStatus: DeltakerStatus,
    ) = Deltaker(
        id = id,
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = startDato,
        sluttdato = sluttDato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = prosentDeltid,
        bakgrunnsinformasjon = null,
        deltakelsesinnhold = null,
        status = if (forrigeDeltakerStatus.aarsak == statusAarsak &&
            forrigeDeltakerStatus.type == status
        ) {
            forrigeDeltakerStatus
        } else {
            DeltakerStatus(
                id = UUID.randomUUID(),
                // TODO: upsert da? Vi må sjekke om det er ny status før vi setter ny status id
                // if (nyesteStatus.type == status.type && nyesteStatus.aarsak == status.aarsak)
                type = status,
                aarsak = statusAarsak,
                // statusEndretDato skal i praksis aldri være null for enkeltplasstiltak (sjekket i arena)
                gyldigFra = statusEndretDato!!,
                gyldigTil = null,
                opprettet = LocalDateTime.now(), // Bruker current_timestamp fra databasen
            )
        },
        vedtaksinformasjon = null,
        kilde = Kilde.ARENA,
        sistEndret = LocalDateTime.now(),
        erManueltDeltMedArrangor = false,
        opprettet = registrertDato,
    )
}
