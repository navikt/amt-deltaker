package no.nav.amt.deltaker.deltaker.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.model.Deltakelsesinnhold
import no.nav.amt.deltaker.deltaker.model.DeltakerHistorikk
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.sammenlignHistorikk
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.navbruker.model.NavBruker
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class DeltakerV2MapperServiceTest {
    companion object {
        private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonClient())
        private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonClient())
        private val deltakerHistorikkService = DeltakerHistorikkService(
            DeltakerEndringRepository(),
            VedtakRepository(),
            ForslagRepository(),
            EndringFraArrangorRepository(),
        )
        private val deltakerV2MapperService = DeltakerV2MapperService(navAnsattService, navEnhetService, deltakerHistorikkService)

        @BeforeClass
        @JvmStatic
        fun setup() {
            SingletonPostgres16Container
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `tilDeltakerV2Dto - utkast til pamelding - returnerer riktig DeltakerV2Dto`(): Unit = runBlocking {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val veileder = TestData.lagNavAnsatt()
        val brukersEnhet = TestData.lagNavEnhet()
        TestRepository.insert(veileder)
        TestRepository.insert(brukersEnhet)
        val navBruker = TestData.lagNavBruker(navVeilederId = veileder.id, navEnhetId = brukersEnhet.id)
        TestRepository.insert(navBruker)
        val deltaker = TestData.lagDeltaker(
            navBruker = navBruker,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        TestRepository.insert(deltaker)
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = null,
        )
        TestRepository.insert(vedtak)

        val deltakerV2Dto = deltakerV2MapperService.tilDeltakerV2Dto(deltaker)

        deltakerV2Dto.id shouldBe deltaker.id
        deltakerV2Dto.deltakerlisteId shouldBe deltaker.deltakerliste.id
        sammenlignPersonalia(deltakerV2Dto.personalia, navBruker)
        sammenlignStatus(deltakerV2Dto.status, deltaker.status)
        deltakerV2Dto.dagerPerUke shouldBe deltaker.dagerPerUke
        deltakerV2Dto.prosentStilling shouldBe deltaker.deltakelsesprosent?.toDouble()
        deltakerV2Dto.oppstartsdato shouldBe deltaker.startdato
        deltakerV2Dto.sluttdato shouldBe deltaker.sluttdato
        deltakerV2Dto.innsoktDato shouldBe vedtak.opprettet.toLocalDate()
        deltakerV2Dto.forsteVedtakFattet shouldBe null
        deltakerV2Dto.bestillingTekst shouldBe deltaker.bakgrunnsinformasjon
        deltakerV2Dto.navKontor shouldBe brukersEnhet.navn
        deltakerV2Dto.navVeileder shouldBe veileder.toDeltakerNavVeilederDto()
        deltakerV2Dto.deltarPaKurs shouldBe deltaker.deltarPaKurs()
        deltakerV2Dto.kilde shouldBe DeltakerV2Dto.Kilde.KOMET
        deltakerV2Dto.innhold shouldBe Deltakelsesinnhold(deltaker.deltakelsesinnhold!!.ledetekst, deltaker.deltakelsesinnhold!!.innhold)
        deltakerV2Dto.historikk?.size shouldBe 1
        sammenlignHistorikk(deltakerV2Dto.historikk?.first()!!, DeltakerHistorikk.Vedtak(vedtak))
        deltakerV2Dto.sistEndret shouldBeCloseTo deltaker.sistEndret
        deltakerV2Dto.sistEndretAv shouldBe sistEndretAv.id
        deltakerV2Dto.sistEndretAvEnhet shouldBe sistEndretAvEnhet.id
    }

    @Test
    fun `tilDeltakerV2Dto - har sluttet - returnerer riktig DeltakerV2Dto`(): Unit = runBlocking {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val veileder = TestData.lagNavAnsatt()
        val brukersEnhet = TestData.lagNavEnhet()
        TestRepository.insert(veileder)
        TestRepository.insert(brukersEnhet)
        val navBruker = TestData.lagNavBruker(navVeilederId = veileder.id, navEnhetId = brukersEnhet.id)
        TestRepository.insert(navBruker)
        val deltaker = TestData.lagDeltaker(
            navBruker = navBruker,
            status = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.HAR_SLUTTET,
                aarsak = DeltakerStatus.Aarsak.Type.ANNET,
                beskrivelse = "Flyttet",
            ),
        )
        TestRepository.insert(deltaker)
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            opprettet = LocalDateTime.now().minusWeeks(3),
            fattet = LocalDateTime.now().minusWeeks(1),
        )
        TestRepository.insert(vedtak)
        val endring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = veileder.id,
            endretAvEnhet = brukersEnhet.id,
            endret = LocalDateTime.now().minusDays(2),
        )
        TestRepository.insert(endring)
        val forslag = TestData.lagForslag(
            deltakerId = deltaker.id,
            status = Forslag.Status.Tilbakekalt(
                tilbakekaltAvArrangorAnsattId = UUID.randomUUID(),
                tilbakekalt = LocalDateTime.now().minusDays(1),
            ),
        )
        TestRepository.insert(forslag)
        val endringFraArrangor = TestData.lagEndringFraArrangor(
            deltakerId = deltaker.id,
            opprettet = LocalDateTime.now(),
        )
        TestRepository.insert(endringFraArrangor)

        val deltakerV2Dto = deltakerV2MapperService.tilDeltakerV2Dto(deltaker)

        deltakerV2Dto.id shouldBe deltaker.id
        deltakerV2Dto.deltakerlisteId shouldBe deltaker.deltakerliste.id
        sammenlignPersonalia(deltakerV2Dto.personalia, navBruker)
        sammenlignStatus(deltakerV2Dto.status, deltaker.status)
        deltakerV2Dto.dagerPerUke shouldBe deltaker.dagerPerUke
        deltakerV2Dto.prosentStilling shouldBe deltaker.deltakelsesprosent?.toDouble()
        deltakerV2Dto.oppstartsdato shouldBe deltaker.startdato
        deltakerV2Dto.sluttdato shouldBe deltaker.sluttdato
        deltakerV2Dto.innsoktDato shouldBe vedtak.opprettet.toLocalDate()
        deltakerV2Dto.forsteVedtakFattet shouldBe LocalDateTime.now().minusWeeks(1).toLocalDate()
        deltakerV2Dto.bestillingTekst shouldBe deltaker.bakgrunnsinformasjon
        deltakerV2Dto.navKontor shouldBe brukersEnhet.navn
        deltakerV2Dto.navVeileder shouldBe veileder.toDeltakerNavVeilederDto()
        deltakerV2Dto.deltarPaKurs shouldBe deltaker.deltarPaKurs()
        deltakerV2Dto.kilde shouldBe DeltakerV2Dto.Kilde.KOMET
        deltakerV2Dto.innhold shouldBe Deltakelsesinnhold(deltaker.deltakelsesinnhold!!.ledetekst, deltaker.deltakelsesinnhold!!.innhold)
        deltakerV2Dto.historikk?.size shouldBe 4
        sammenlignHistorikk(deltakerV2Dto.historikk?.get(0)!!, DeltakerHistorikk.EndringFraArrangor(endringFraArrangor))
        sammenlignHistorikk(deltakerV2Dto.historikk?.get(1)!!, DeltakerHistorikk.Forslag(forslag))
        sammenlignHistorikk(deltakerV2Dto.historikk?.get(2)!!, DeltakerHistorikk.Endring(endring))
        sammenlignHistorikk(deltakerV2Dto.historikk?.get(3)!!, DeltakerHistorikk.Vedtak(vedtak))
        deltakerV2Dto.sistEndret shouldBeCloseTo deltaker.sistEndret
        deltakerV2Dto.sistEndretAv shouldBe veileder.id
        deltakerV2Dto.sistEndretAvEnhet shouldBe brukersEnhet.id
    }
}

fun sammenlignPersonalia(personaliaDto: DeltakerV2Dto.DeltakerPersonaliaDto, navBruker: NavBruker) {
    personaliaDto.personId shouldBe navBruker.personId
    personaliaDto.personident shouldBe navBruker.personident
    personaliaDto.navn.fornavn shouldBe navBruker.fornavn
    personaliaDto.navn.mellomnavn shouldBe navBruker.mellomnavn
    personaliaDto.navn.etternavn shouldBe navBruker.etternavn
    personaliaDto.kontaktinformasjon.telefonnummer shouldBe navBruker.telefon
    personaliaDto.kontaktinformasjon.epost shouldBe navBruker.epost
    personaliaDto.skjermet shouldBe navBruker.erSkjermet
    personaliaDto.adresse shouldBe navBruker.adresse
    personaliaDto.adressebeskyttelse shouldBe navBruker.adressebeskyttelse
}

fun sammenlignStatus(deltakerStatusDto: DeltakerV2Dto.DeltakerStatusDto, deltakerStatus: DeltakerStatus) {
    deltakerStatusDto.id shouldBe deltakerStatus.id
    deltakerStatusDto.type shouldBe deltakerStatus.type
    deltakerStatusDto.aarsak shouldBe deltakerStatus.aarsak?.type
    deltakerStatusDto.aarsaksbeskrivelse shouldBe deltakerStatus.aarsak?.beskrivelse
    deltakerStatusDto.gyldigFra shouldBeCloseTo deltakerStatus.gyldigFra
    deltakerStatusDto.opprettetDato shouldBeCloseTo deltakerStatus.opprettet
}
