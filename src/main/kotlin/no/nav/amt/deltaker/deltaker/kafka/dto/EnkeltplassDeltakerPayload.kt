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
    fun toDeltaker(deltakerliste: Deltakerliste, navBruker: NavBruker) = Deltaker(
        id = id,
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = startDato,
        sluttdato = sluttDato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = prosentDeltid,
        bakgrunnsinformasjon = null,
        deltakelsesinnhold = null,
        status = DeltakerStatus(
            id = UUID.randomUUID(),
            type = status,
            aarsak = statusAarsak,
            // TODO: kan denne i praksis være null? hvis ja blir det uansett feil å sette current_timestamp på vei inn i db slik som i amt-tiltak?
            gyldigFra = statusEndretDato!!,
            gyldigTil = null,
            opprettet = LocalDateTime.now(), // TODO: mappes fra created_at i db i rowmapper
        ),
        vedtaksinformasjon = null,
        kilde = Kilde.ARENA,
        sistEndret = LocalDateTime.now(),
        erManueltDeltMedArrangor = false,
        opprettet = registrertDato,
    )
}
