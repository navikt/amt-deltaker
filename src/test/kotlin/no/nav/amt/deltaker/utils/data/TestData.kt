package no.nav.amt.deltaker.utils.data

import no.nav.amt.deltaker.amtperson.dto.NavBrukerDto
import no.nav.amt.deltaker.amtperson.dto.NavEnhetDto
import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.deltaker.model.Vedtak
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlisteDto
import no.nav.amt.deltaker.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.navbruker.model.Adresse
import no.nav.amt.deltaker.navbruker.model.Adressebeskyttelse
import no.nav.amt.deltaker.navbruker.model.Bostedsadresse
import no.nav.amt.deltaker.navbruker.model.Kontaktadresse
import no.nav.amt.deltaker.navbruker.model.Matrikkeladresse
import no.nav.amt.deltaker.navbruker.model.NavBruker
import no.nav.amt.deltaker.navbruker.model.Vegadresse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object TestData {
    fun randomIdent() = (10_00_00_00_000..31_12_00_99_999).random().toString()

    fun randomNavIdent() = ('A'..'Z').random().toString() + (100_000..999_999).random().toString()

    fun randomEnhetsnummer() = (1000..9999).random().toString()

    fun randomOrgnr() = (900_000_000..999_999_998).random().toString()

    fun lagArrangor(
        id: UUID = UUID.randomUUID(),
        navn: String = "Arrangor 1",
        organisasjonsnummer: String = randomOrgnr(),
        overordnetArrangorId: UUID? = null,
    ) = Arrangor(id, navn, organisasjonsnummer, overordnetArrangorId)

    fun lagNavAnsatt(
        id: UUID = UUID.randomUUID(),
        navIdent: String = randomNavIdent(),
        navn: String = "Veileder Veiledersen",
        telefon: String = "99988777",
        epost: String = "ansatt@nav.no",
    ) = NavAnsatt(id, navIdent, navn, epost, telefon)

    fun lagNavEnhet(
        id: UUID = UUID.randomUUID(),
        enhetsnummer: String = randomEnhetsnummer(),
        navn: String = "NAV Testheim",
    ) = NavEnhet(id, enhetsnummer, navn)

    fun lagNavBruker(
        personId: UUID = UUID.randomUUID(),
        personident: String = randomIdent(),
        fornavn: String = "Fornavn",
        mellomnavn: String? = "Mellomnavn",
        etternavn: String = "Etternavn",
        navVeilederId: UUID? = lagNavAnsatt().id,
        navEnhetId: UUID? = lagNavEnhet().id,
        telefon: String? = "77788999",
        epost: String? = "nav_bruker@gmail.com",
        erSkjermet: Boolean = false,
        adresse: Adresse? = lagAdresse(),
        adressebeskyttelse: Adressebeskyttelse? = null,
    ) = NavBruker(
        personId,
        personident,
        fornavn,
        mellomnavn,
        etternavn,
        navVeilederId,
        navEnhetId,
        telefon,
        epost,
        erSkjermet,
        adresse,
        adressebeskyttelse,
    )

    fun lagTiltakstype(
        id: UUID = UUID.randomUUID(),
        type: Tiltakstype.Type = Tiltakstype.Type.entries.random(),
        navn: String = "Test tiltak $type",
        innhold: DeltakerRegistreringInnhold = lagDeltakerRegistreringInnhold(),
    ) = Tiltakstype(id, navn, type, innhold)

    fun lagDeltakerRegistreringInnhold(
        innholdselementer: List<Innholdselement> = listOf(Innholdselement("Tekst", "kode")),
        ledetekst: String = "Beskrivelse av tilaket",
    ) = DeltakerRegistreringInnhold(innholdselementer, ledetekst)

    fun lagDeltakerliste(
        id: UUID = UUID.randomUUID(),
        arrangor: Arrangor = lagArrangor(),
        tiltakstype: Tiltakstype = lagTiltakstype(),
        navn: String = "Test Deltakerliste ${tiltakstype.type}",
        status: Deltakerliste.Status = Deltakerliste.Status.GJENNOMFORES,
        startDato: LocalDate = LocalDate.now().minusMonths(1),
        sluttDato: LocalDate? = LocalDate.now().plusYears(1),
        oppstart: Deltakerliste.Oppstartstype? = finnOppstartstype(tiltakstype.type),
    ) = Deltakerliste(id, tiltakstype, navn, status, startDato, sluttDato, oppstart, arrangor)

    fun lagDeltakerlisteDto(
        arrangor: Arrangor = lagArrangor(),
        deltakerliste: Deltakerliste = lagDeltakerliste(arrangor = arrangor),
    ) = DeltakerlisteDto(
        id = deltakerliste.id,
        tiltakstype = DeltakerlisteDto.Tiltakstype(
            deltakerliste.tiltakstype.navn,
            deltakerliste.tiltakstype.type.name,
        ),
        navn = deltakerliste.navn,
        startDato = deltakerliste.startDato,
        sluttDato = deltakerliste.sluttDato,
        status = deltakerliste.status.name,
        virksomhetsnummer = arrangor.organisasjonsnummer,
        oppstart = deltakerliste.oppstart,
    )

    fun lagNavBrukerDto(
        navBruker: NavBruker,
    ) = NavBrukerDto(
        personId = navBruker.personId,
        personident = navBruker.personident,
        fornavn = navBruker.fornavn,
        mellomnavn = navBruker.mellomnavn,
        etternavn = navBruker.etternavn,
        navVeilederId = navBruker.navVeilederId,
        navEnhet = navBruker.navEnhetId?.let { lagNavEnhetDto(lagNavEnhet(id = it)) },
        telefon = navBruker.telefon,
        epost = navBruker.epost,
        erSkjermet = navBruker.erSkjermet,
        adresse = navBruker.adresse,
        adressebeskyttelse = navBruker.adressebeskyttelse,
    )

    fun lagNavEnhetDto(
        navEnhet: NavEnhet,
    ) = NavEnhetDto(
        id = navEnhet.id,
        enhetId = navEnhet.enhetsnummer,
        navn = navEnhet.navn,
    )

    fun lagAdresse(): Adresse =
        Adresse(
            bostedsadresse = Bostedsadresse(
                coAdressenavn = "C/O Gutterommet",
                vegadresse = null,
                matrikkeladresse = Matrikkeladresse(
                    tilleggsnavn = "Gården",
                    postnummer = "0484",
                    poststed = "OSLO",
                ),
            ),
            oppholdsadresse = null,
            kontaktadresse = Kontaktadresse(
                coAdressenavn = null,
                vegadresse = Vegadresse(
                    husnummer = "1",
                    husbokstav = null,
                    adressenavn = "Gate",
                    tilleggsnavn = null,
                    postnummer = "1234",
                    poststed = "MOSS",
                ),
                postboksadresse = null,
            ),
        )

    fun lagDeltaker(
        id: UUID = UUID.randomUUID(),
        navBruker: NavBruker = lagNavBruker(),
        deltakerliste: Deltakerliste = lagDeltakerliste(),
        startdato: LocalDate? = LocalDate.now().minusMonths(3),
        sluttdato: LocalDate? = LocalDate.now().minusDays(1),
        dagerPerUke: Float? = 5F,
        deltakelsesprosent: Float? = 100F,
        bakgrunnsinformasjon: String? = "Søkes inn fordi...",
        innhold: List<Innhold> = emptyList(),
        status: DeltakerStatus = lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
        vedtaksinformasjon: Deltaker.Vedtaksinformasjon? = null,
        sistEndret: LocalDateTime = LocalDateTime.now(),
    ) = Deltaker(
        id,
        navBruker,
        deltakerliste,
        startdato,
        sluttdato,
        dagerPerUke,
        deltakelsesprosent,
        bakgrunnsinformasjon,
        innhold,
        status,
        vedtaksinformasjon,
        sistEndret,
    )

    fun lagDeltakerStatus(
        id: UUID = UUID.randomUUID(),
        type: DeltakerStatus.Type = DeltakerStatus.Type.DELTAR,
        aarsak: DeltakerStatus.Aarsak.Type? = null,
        beskrivelse: String? = null,
        gyldigFra: LocalDateTime = LocalDateTime.now(),
        gyldigTil: LocalDateTime? = null,
        opprettet: LocalDateTime = gyldigFra,
    ) = DeltakerStatus(
        id,
        type,
        aarsak?.let { DeltakerStatus.Aarsak(it, beskrivelse) },
        gyldigFra,
        gyldigTil,
        opprettet,
    )

    fun lagDeltakerEndring(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        endringstype: DeltakerEndring.Endringstype = DeltakerEndring.Endringstype.BAKGRUNNSINFORMASJON,
        endring: DeltakerEndring.Endring = DeltakerEndring.Endring.EndreBakgrunnsinformasjon("Oppdatert bakgrunnsinformasjon"),
        endretAv: UUID = lagNavAnsatt().id,
        endretAvEnhet: UUID = lagNavEnhet().id,
        endret: LocalDateTime = LocalDateTime.now(),
    ) = DeltakerEndring(id, deltakerId, endringstype, endring, endretAv, endretAvEnhet, endret)

    fun lagVedtak(
        id: UUID = UUID.randomUUID(),
        deltakerVedVedtak: Deltaker = lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        ),
        deltakerId: UUID = deltakerVedVedtak.id,
        fattet: LocalDateTime? = null,
        gyldigTil: LocalDateTime? = null,
        fattetAvNav: Boolean = false,
        opprettet: LocalDateTime = LocalDateTime.now(),
        opprettetAv: NavAnsatt = lagNavAnsatt(),
        opprettetAvEnhet: NavEnhet = lagNavEnhet(),
        sistEndret: LocalDateTime = opprettet,
        sistEndretAv: NavAnsatt = opprettetAv,
        sistEndretAvEnhet: NavEnhet = opprettetAvEnhet,
    ) = Vedtak(
        id,
        deltakerId,
        fattet,
        gyldigTil,
        deltakerVedVedtak.toDeltakerVedVedtak(),
        fattetAvNav,
        opprettet,
        opprettetAv.id,
        opprettetAvEnhet.id,
        sistEndret,
        sistEndretAv.id,
        sistEndretAvEnhet.id,
    )

    private fun finnOppstartstype(type: Tiltakstype.Type) = when (type) {
        Tiltakstype.Type.JOBBK,
        Tiltakstype.Type.GRUPPEAMO,
        Tiltakstype.Type.GRUFAGYRKE,
        -> Deltakerliste.Oppstartstype.FELLES

        else -> Deltakerliste.Oppstartstype.LOPENDE
    }
}
