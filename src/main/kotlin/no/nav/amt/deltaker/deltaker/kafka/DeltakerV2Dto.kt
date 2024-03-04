package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.deltaker.model.DeltakerHistorikk
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navbruker.model.Adresse
import no.nav.amt.deltaker.navbruker.model.Adressebeskyttelse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerV2Dto(
    val id: UUID,
    val deltakerlisteId: UUID,
    val personalia: DeltakerPersonaliaDto,
    val status: DeltakerStatusDto,
    val dagerPerUke: Float?,
    val prosentStilling: Double?,
    val oppstartsdato: LocalDate?,
    val sluttdato: LocalDate?,
    val innsoktDato: LocalDate,
    val forsteVedtakFattet: LocalDate?,
    val bestillingTekst: String?,
    val navKontor: String?,
    val navVeileder: DeltakerNavVeilederDto?,
    val deltarPaKurs: Boolean,
    val kilde: Kilde?,
    val innhold: DeltakelsesInnhold?,
    val historikk: List<DeltakerHistorikk>?,
    val sistEndretAv: UUID?,
    val sistEndretAvEnhet: UUID?,
    val sistEndret: LocalDateTime?,
) {
    enum class Kilde {
        KOMET,
        ARENA,
    }

    data class DeltakerPersonaliaDto(
        val personId: UUID?,
        val personident: String,
        val navn: Navn,
        val kontaktinformasjon: DeltakerKontaktinformasjonDto,
        val skjermet: Boolean,
        val adresse: Adresse?,
        val adressebeskyttelse: Adressebeskyttelse?,
    )

    data class Navn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
    )

    data class DeltakerStatusDto(
        val id: UUID?,
        val type: DeltakerStatus.Type,
        val aarsak: DeltakerStatus.Aarsak?,
        val gyldigFra: LocalDateTime,
        val opprettetDato: LocalDateTime,
    )

    data class DeltakerKontaktinformasjonDto(
        val telefonnummer: String?,
        val epost: String?,
    )

    data class DeltakerNavVeilederDto(
        val id: UUID,
        val navn: String,
        val epost: String?,
        val telefonnummer: String?,
    )

    data class DeltakelsesInnhold(
        val ledetekst: String,
        val innhold: List<Innhold>,
    )
}

fun NavAnsatt.toDeltakerNavVeilederDto() =
    DeltakerV2Dto.DeltakerNavVeilederDto(
        id = id,
        navn = navn,
        epost = epost,
        telefonnummer = telefon,
    )
