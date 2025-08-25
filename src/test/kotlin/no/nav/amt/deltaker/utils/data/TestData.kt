package no.nav.amt.deltaker.utils.data

import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerV2Dto
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.Kilde
import no.nav.amt.deltaker.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.deltaker.vurdering.Vurdering
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlisteDto
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerVedImport
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.person.address.Bostedsadresse
import no.nav.amt.lib.models.person.address.Kontaktadresse
import no.nav.amt.lib.models.person.address.Matrikkeladresse
import no.nav.amt.lib.models.person.address.Vegadresse
import no.nav.amt.lib.models.person.dto.NavBrukerDto
import no.nav.amt.lib.models.person.dto.NavEnhetDto
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object TestData {
    fun randomIdent() = (10_00_00_00_000..31_12_00_99_999).random().toString()

    fun randomNavIdent() = ('A'..'Z').random().toString() + (100_000..999_999).random().toString()

    fun randomEnhetsnummer() = (1000..9999999999).random().toString()

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
        navEnhetId: UUID? = UUID.randomUUID(),
    ) = NavAnsatt(id, navIdent, navn, epost, telefon, navEnhetId)

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
        oppfolgingsperioder: List<Oppfolgingsperiode> = listOf(lagOppfolgingsperiode()),
        innsatsgruppe: Innsatsgruppe? = Innsatsgruppe.STANDARD_INNSATS,
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
        oppfolgingsperioder,
        innsatsgruppe,
    )

    fun lagOppfolgingsperiode(
        id: UUID = UUID.randomUUID(),
        startdato: LocalDateTime = LocalDateTime.now().minusMonths(1),
        sluttdato: LocalDateTime? = null,
    ) = Oppfolgingsperiode(
        id,
        startdato,
        sluttdato,
    )

    private val tiltakstypeCache = mutableMapOf<Tiltakstype.Tiltakskode, Tiltakstype>()

    fun lagTiltakstype(
        id: UUID = UUID.randomUUID(),
        tiltakskode: Tiltakstype.Tiltakskode = Tiltakstype.Tiltakskode.OPPFOLGING,
        arenaKode: Tiltakstype.ArenaKode = tiltakskode.toArenaKode(),
        navn: String = "Test tiltak $arenaKode",
        innsatsgrupper: Set<Innsatsgruppe> = setOf(Innsatsgruppe.STANDARD_INNSATS),
        innhold: DeltakerRegistreringInnhold? = lagDeltakerRegistreringInnhold(),
    ): Tiltakstype {
        val tiltak = tiltakstypeCache[tiltakskode] ?: Tiltakstype(id, navn, tiltakskode, arenaKode, innsatsgrupper, innhold)
        val nyttTiltak = tiltak.copy(navn = navn, innhold = innhold, innsatsgrupper = innsatsgrupper)
        tiltakstypeCache[tiltak.tiltakskode] = nyttTiltak

        return nyttTiltak
    }

    fun lagDeltakerRegistreringInnhold(
        innholdselementer: List<Innholdselement> = listOf(Innholdselement("Tekst", "kode")),
        ledetekst: String = "Beskrivelse av tilaket",
    ) = DeltakerRegistreringInnhold(innholdselementer, ledetekst)

    fun lagDeltakerliste(
        id: UUID = UUID.randomUUID(),
        arrangor: Arrangor = lagArrangor(),
        tiltakstype: Tiltakstype = lagTiltakstype(),
        navn: String = "Test Deltakerliste ${tiltakstype.arenaKode}",
        status: Deltakerliste.Status = Deltakerliste.Status.GJENNOMFORES,
        startDato: LocalDate = LocalDate.now().minusMonths(1),
        sluttDato: LocalDate? = LocalDate.now().plusYears(1),
        oppstart: Deltakerliste.Oppstartstype = finnOppstartstype(tiltakstype.arenaKode),
        apentForPamelding: Boolean = true,
    ) = Deltakerliste(id, tiltakstype, navn, status, startDato, sluttDato, oppstart, apentForPamelding, arrangor)

    fun lagDeltakerlisteMedLopendeOppstart(
        tiltakstype: Tiltakstype = lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
    ) = lagDeltakerliste(
        tiltakstype = tiltakstype,
    )

    fun lagDeltakerlisteMedFellesOppstart(
        tiltakstype: Tiltakstype = lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
    ) = lagDeltakerliste(
        tiltakstype = tiltakstype,
    )

    fun lagDeltakerlisteDto(arrangor: Arrangor = lagArrangor(), deltakerliste: Deltakerliste = lagDeltakerliste(arrangor = arrangor)) =
        DeltakerlisteDto(
            id = deltakerliste.id,
            tiltakstype = DeltakerlisteDto.Tiltakstype(
                deltakerliste.tiltakstype.navn,
                deltakerliste.tiltakstype.arenaKode.name,
            ),
            navn = deltakerliste.navn,
            startDato = deltakerliste.startDato,
            sluttDato = deltakerliste.sluttDato,
            status = deltakerliste.status.name,
            virksomhetsnummer = arrangor.organisasjonsnummer,
            oppstart = deltakerliste.oppstart,
            apentForPamelding = deltakerliste.apentForPamelding,
        )

    fun lagNavBrukerDto(navBruker: NavBruker, navEnhet: NavEnhet) = NavBrukerDto(
        personId = navBruker.personId,
        personident = navBruker.personident,
        fornavn = navBruker.fornavn,
        mellomnavn = navBruker.mellomnavn,
        etternavn = navBruker.etternavn,
        navVeilederId = navBruker.navVeilederId,
        navEnhet = lagNavEnhetDto(navEnhet),
        telefon = navBruker.telefon,
        epost = navBruker.epost,
        erSkjermet = navBruker.erSkjermet,
        adresse = navBruker.adresse,
        adressebeskyttelse = navBruker.adressebeskyttelse,
        oppfolgingsperioder = navBruker.oppfolgingsperioder,
        innsatsgruppe = navBruker.innsatsgruppe,
    )

    fun lagNavEnhetDto(navEnhet: NavEnhet) = NavEnhetDto(
        id = navEnhet.id,
        enhetId = navEnhet.enhetsnummer,
        navn = navEnhet.navn,
    )

    fun lagAdresse(): Adresse = Adresse(
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

    fun lagDeltakerKladd(
        id: UUID = UUID.randomUUID(),
        navBruker: NavBruker = lagNavBruker(),
        deltakerliste: Deltakerliste = lagDeltakerliste(),
    ) = lagDeltaker(
        id = id,
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = null,
        sluttdato = null,
        dagerPerUke = null,
        deltakelsesprosent = null,
        bakgrunnsinformasjon = null,
        innhold = Deltakelsesinnhold(deltakerliste.tiltakstype.innhold?.ledetekst, emptyList()),
        status = lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
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
        innhold: Deltakelsesinnhold? = Deltakelsesinnhold("ledetekst", emptyList()),
        status: DeltakerStatus = lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
        vedtaksinformasjon: Vedtaksinformasjon? = null,
        sistEndret: LocalDateTime = LocalDateTime.now(),
        kilde: Kilde = Kilde.KOMET,
        erManueltDeltMedArrangor: Boolean = false,
    ) = Deltaker(
        id = id,
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = innhold,
        status = status,
        vedtaksinformasjon = vedtaksinformasjon,
        sistEndret = sistEndret,
        kilde = kilde,
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
        opprettet = null,
    )

    fun lagDeltakerStatus(
        id: UUID = UUID.randomUUID(),
        type: DeltakerStatus.Type = DeltakerStatus.Type.DELTAR,
        aarsak: DeltakerStatus.Aarsak.Type? = null,
        beskrivelse: String? = null,
        gyldigFra: LocalDateTime = LocalDateTime.now().minusMinutes(5),
        gyldigTil: LocalDateTime? = null,
        opprettet: LocalDateTime = LocalDateTime.now(),
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
        endring: DeltakerEndring.Endring = DeltakerEndring.Endring.EndreBakgrunnsinformasjon("Oppdatert bakgrunnsinformasjon"),
        endretAv: UUID = lagNavAnsatt().id,
        endretAvEnhet: UUID = lagNavEnhet().id,
        endret: LocalDateTime = LocalDateTime.now(),
        forslag: Forslag? = null,
    ) = DeltakerEndring(id, deltakerId, endring, endretAv, endretAvEnhet, endret, forslag)

    fun lagForslag(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        opprettetAvArrangorAnsattId: UUID = UUID.randomUUID(),
        opprettet: LocalDateTime = LocalDateTime.now(),
        begrunnelse: String = "Begrunnelse fra arrangør",
        endring: Forslag.Endring = Forslag.ForlengDeltakelse(LocalDate.now().plusWeeks(2)),
        status: Forslag.Status = Forslag.Status.VenterPaSvar,
    ) = Forslag(id, deltakerId, opprettetAvArrangorAnsattId, opprettet, begrunnelse, endring, status)

    fun lagEndringFraArrangor(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        opprettetAvArrangorAnsattId: UUID = UUID.randomUUID(),
        opprettet: LocalDateTime = LocalDateTime.now(),
        endring: EndringFraArrangor.Endring = EndringFraArrangor.LeggTilOppstartsdato(
            LocalDate.now().plusDays(2),
            LocalDate.now().plusMonths(3),
        ),
    ) = EndringFraArrangor(id, deltakerId, opprettetAvArrangorAnsattId, opprettet, endring)

    fun lagVedtak(
        id: UUID = UUID.randomUUID(),
        deltakerVedVedtak: Deltaker = lagDeltaker(
            status = lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        ),
        deltakerId: UUID = deltakerVedVedtak.id,
        fattet: LocalDateTime? = null,
        gyldigTil: LocalDateTime? = null,
        fattetAvNav: Boolean = false,
        opprettet: LocalDateTime = fattet ?: LocalDateTime.now(),
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

    fun lagInnsoktPaaKurs(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        innsokt: LocalDateTime = LocalDateTime.now(),
        innsoktAv: UUID = UUID.randomUUID(),
        innsoktAvEnhet: UUID = UUID.randomUUID(),
        deltakelsesinnholdVedInnsok: Deltakelsesinnhold = Deltakelsesinnhold("", emptyList()),
        utkastDelt: LocalDateTime = LocalDateTime.now().minusDays(2),
        utkastGodkjentAvNav: Boolean = false,
    ) = InnsokPaaFellesOppstart(
        id = id,
        deltakerId = deltakerId,
        innsokt = innsokt,
        innsoktAv = innsoktAv,
        innsoktAvEnhet = innsoktAvEnhet,
        deltakelsesinnholdVedInnsok = deltakelsesinnholdVedInnsok,
        utkastDelt = utkastDelt,
        utkastGodkjentAvNav = utkastGodkjentAvNav,
    )

    fun lagImportertFraArena(
        deltakerId: UUID = UUID.randomUUID(),
        importertDato: LocalDateTime = LocalDateTime.now(),
        deltakerVedImport: DeltakerVedImport = lagDeltaker(id = deltakerId).toDeltakerVedImport(LocalDate.now()),
    ) = ImportertFraArena(
        deltakerId,
        importertDato,
        deltakerVedImport,
    )

    fun lagEndringFraTiltakskoordinator(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        endring: EndringFraTiltakskoordinator.Endring = EndringFraTiltakskoordinator.DelMedArrangor,
        endretAv: UUID = UUID.randomUUID(),
        endretAvEnhet: UUID = UUID.randomUUID(),
        endret: LocalDateTime = LocalDateTime.now(),
    ) = EndringFraTiltakskoordinator(
        id = id,
        deltakerId = deltakerId,
        endring = endring,
        endretAv = endretAv,
        endretAvEnhet = endretAvEnhet,
        endret = endret,
    )

    fun lagVurdering(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        vurderingstype: Vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
        begrunnelse: String? = null,
        opprettetAvArrangorAnsattId: UUID = UUID.randomUUID(),
        gyldigFra: LocalDateTime = LocalDateTime.now(),
    ) = Vurdering(
        id = id,
        deltakerId = deltakerId,
        vurderingstype = vurderingstype,
        begrunnelse = begrunnelse,
        opprettetAvArrangorAnsattId = opprettetAvArrangorAnsattId,
        gyldigFra = gyldigFra,
    )

    fun Deltaker.toDeltakerV2(
        innsoktDato: LocalDate = LocalDate.now(),
        forsteVedtakFattet: LocalDate? = null,
        deltakerhistorikk: List<DeltakerHistorikk>? = null,
    ) = DeltakerV2Dto(
        id = id,
        deltakerlisteId = deltakerliste.id,
        personalia = DeltakerV2Dto.DeltakerPersonaliaDto(
            personId = navBruker.personId,
            personident = navBruker.personident,
            navn = DeltakerV2Dto.Navn(
                fornavn = navBruker.fornavn,
                mellomnavn = navBruker.mellomnavn,
                etternavn = navBruker.etternavn,
            ),
            kontaktinformasjon = DeltakerV2Dto.DeltakerKontaktinformasjonDto(
                telefonnummer = navBruker.telefon,
                epost = navBruker.epost,
            ),
            skjermet = navBruker.erSkjermet,
            adresse = navBruker.adresse,
            adressebeskyttelse = navBruker.adressebeskyttelse,
        ),
        status = DeltakerV2Dto.DeltakerStatusDto(
            id = status.id,
            type = status.type,
            aarsak = status.aarsak?.type,
            aarsaksbeskrivelse = status.aarsak?.beskrivelse,
            gyldigFra = status.gyldigFra,
            opprettetDato = status.opprettet,
        ),
        dagerPerUke = dagerPerUke,
        prosentStilling = deltakelsesprosent?.toDouble(),
        oppstartsdato = startdato,
        sluttdato = sluttdato,
        innsoktDato = innsoktDato,
        forsteVedtakFattet = forsteVedtakFattet,
        bestillingTekst = bakgrunnsinformasjon,
        navKontor = null,
        navVeileder = null,
        deltarPaKurs = deltarPaKurs(),
        kilde = kilde,
        innhold = deltakelsesinnhold,
        historikk = deltakerhistorikk,
        sistEndret = sistEndret,
        sistEndretAv = null,
        sistEndretAvEnhet = null,
        vurderingerFraArrangor = emptyList(),
        forcedUpdate = null,
        oppfolgingsperioder = navBruker.oppfolgingsperioder,
    )

    private fun finnOppstartstype(type: Tiltakstype.ArenaKode) = when (type) {
        Tiltakstype.ArenaKode.JOBBK,
        Tiltakstype.ArenaKode.GRUPPEAMO,
        Tiltakstype.ArenaKode.GRUFAGYRKE,
        -> Deltakerliste.Oppstartstype.FELLES

        else -> Deltakerliste.Oppstartstype.LOPENDE
    }
}
