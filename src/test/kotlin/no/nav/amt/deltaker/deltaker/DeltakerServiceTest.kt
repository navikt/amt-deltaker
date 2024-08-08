package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.api.model.AvsluttDeltakelseRequest
import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.model.ForlengDeltakelseRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorService
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV2MapperService
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.hendelse.model.HendelseType
import no.nav.amt.deltaker.kafka.utils.assertProduced
import no.nav.amt.deltaker.kafka.utils.assertProducedFeilregistrert
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.SingletonPostgresContainer
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

class DeltakerServiceTest {
    companion object {
        private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonClient())
        private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonClient())
        private val deltakerRepository = DeltakerRepository()
        private val deltakerEndringRepository = DeltakerEndringRepository()
        private val vedtakRepository = VedtakRepository()
        private val forslagRepository = ForslagRepository()
        private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        private val deltakerHistorikkService = DeltakerHistorikkService(deltakerEndringRepository, vedtakRepository, forslagRepository)
        private val hendelseService = HendelseService(
            HendelseProducer(LocalKafkaConfig(SingletonKafkaProvider.getHost())),
            navAnsattService,
            navEnhetService,
            arrangorService,
            deltakerHistorikkService,
        )
        private val deltakerV2MapperService =
            DeltakerV2MapperService(navAnsattService, navEnhetService, deltakerHistorikkService)
        private val deltakerProducer = DeltakerProducer(
            LocalKafkaConfig(SingletonKafkaProvider.getHost()),
            deltakerV2MapperService,
        )
        private val forslagService = ForslagService(
            forslagRepository,
            ArrangorMeldingProducer(LocalKafkaConfig(SingletonKafkaProvider.getHost())),
            deltakerRepository,
            deltakerProducer,
        )
        private val vedtakService = VedtakService(vedtakRepository, hendelseService)
        private val deltakerEndringService =
            DeltakerEndringService(deltakerEndringRepository, navAnsattService, navEnhetService, hendelseService, forslagService)
        private val endringFraArrangorService = EndringFraArrangorService(EndringFraArrangorRepository())

        private val deltakerService = DeltakerService(
            deltakerRepository = deltakerRepository,
            deltakerProducer = deltakerProducer,
            deltakerEndringService = deltakerEndringService,
            vedtakService = vedtakService,
            hendelseService = hendelseService,
            endringFraArrangorService = endringFraArrangorService,
        )

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `upsertDeltaker - deltaker endrer status fra kladd til utkast - oppdaterer og publiserer til kafka`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
        )
        TestRepository.insert(deltaker)

        val deltakerMedOppdatertStatus = deltaker.copy(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltakerMedOppdatertStatus,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = null,
        )
        TestRepository.insert(vedtak)
        val oppdatertDeltaker = deltakerMedOppdatertStatus.copy(
            vedtaksinformasjon = vedtak.tilVedtaksinformasjon(),
        )

        runBlocking {
            val deltakerFraDb = deltakerService.upsertDeltaker(oppdatertDeltaker)
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
            deltakerFraDb.vedtaksinformasjon?.opprettetAv shouldBe vedtak.opprettetAv

            assertProduced(deltaker.id)
        }
    }

    @Test
    fun `upsertDeltaker - oppretter kladd - oppdaterer i db`() {
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)
        TestRepository.insert(deltakerliste)
        val opprettetAv = TestData.lagNavAnsatt()
        val opprettetAvEnhet = TestData.lagNavEnhet()
        val navBruker = TestData.lagNavBruker(navVeilederId = opprettetAv.id, navEnhetId = opprettetAvEnhet.id)
        TestRepository.insert(navBruker)

        val deltaker = TestData.lagDeltaker(
            navBruker = navBruker,
            deltakerliste = deltakerliste,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
        )

        runBlocking {
            val deltakerFraDb = deltakerService.upsertDeltaker(deltaker)
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.KLADD
            deltakerFraDb.vedtaksinformasjon shouldBe null
        }
    }

    @Test
    fun `upsertEndretDeltaker - ingen endring - upserter ikke`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(sistEndret = LocalDateTime.now().minusDays(2))
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = BakgrunnsinformasjonRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
        )

        deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

        deltakerService.get(deltaker.id).getOrThrow().sistEndret shouldBeCloseTo deltaker.sistEndret
    }

    @Test
    fun `upsertEndretDeltaker - avslutt i fremtiden - setter fremtidig HAR_SLUTTET`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)

        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusWeeks(1),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = null,
            forslagId = null,
        )

        val deltakerrespons = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

        deltakerrespons.status.type shouldBe DeltakerStatus.Type.DELTAR
        deltakerrespons.sluttdato shouldBe endringsrequest.sluttdato

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

        val statuser = deltakerRepository.getDeltakerStatuser(deltaker.id)
        statuser.size shouldBe 2
        statuser.first { it.id == deltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == deltaker.status.id }.type shouldBe DeltakerStatus.Type.DELTAR
        statuser.first { it.id != deltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id != deltaker.status.id }.gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
        statuser.first { it.id != deltaker.status.id }.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        statuser.first { it.id != deltaker.status.id }.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
    }

    @Test
    fun `upsertEndretDeltaker - avslutt i fremtiden, blir forlenget - deaktiverer fremtidig HAR_SLUTTET`(): Unit = runBlocking {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusDays(2),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)
        val fremtidigHarSluttetStatus = TestData.lagDeltakerStatus(
            type = DeltakerStatus.Type.HAR_SLUTTET,
            gyldigFra = LocalDateTime.now().plusDays(2),
        )
        TestRepository.insert(fremtidigHarSluttetStatus, deltaker.id)

        val endringsrequest = ForlengDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusMonths(1),
            begrunnelse = "begrunnelse",
            forslagId = null,
        )

        val deltakerrespons = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

        deltakerrespons.status.type shouldBe DeltakerStatus.Type.DELTAR
        deltakerrespons.sluttdato shouldBe endringsrequest.sluttdato

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

        val statuser = deltakerRepository.getDeltakerStatuser(deltaker.id)
        statuser.size shouldBe 2
        statuser.first { it.id == deltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == deltaker.status.id }.type shouldBe DeltakerStatus.Type.DELTAR
        statuser.first { it.id == fremtidigHarSluttetStatus.id }.gyldigTil shouldNotBe null
        statuser.first { it.id == fremtidigHarSluttetStatus.id }.gyldigFra.toLocalDate() shouldBe LocalDate.now().plusDays(2)
        statuser.first { it.id == fremtidigHarSluttetStatus.id }.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
    }

    @Test
    fun `produserDeltakereForPerson - deltaker finnes - publiserer til kafka`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        TestRepository.insert(deltaker)
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)

        runBlocking {
            deltakerService.produserDeltakereForPerson(deltaker.navBruker.personident)

            assertProduced(deltaker.id)
        }
    }

    @Test
    fun `fattVedtak - deltaker har status utkast - oppretter ny status og upserter`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        val ansatt = TestData.lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = TestData.lagNavEnhet(id = vedtak.opprettetAvEnhet)
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

        runBlocking {
            deltakerService.fattVedtak(deltaker.id, vedtak.id)
        }

        assertProduced(deltaker.id)
        assertProducedHendelse(deltaker.id, HendelseType.InnbyggerGodkjennUtkast::class)

        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        oppdatertDeltaker.vedtaksinformasjon!!.fattet shouldBeCloseTo LocalDateTime.now()
    }

    @Test
    fun `fattVedtak - deltaker har ikke status utkast - upserter uten å endre status`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        val ansatt = TestData.lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = TestData.lagNavEnhet(id = vedtak.opprettetAvEnhet)
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

        runBlocking {
            deltakerService.fattVedtak(deltaker.id, vedtak.id)
        }

        assertProduced(deltaker.id)
        assertProducedHendelse(deltaker.id, HendelseType.InnbyggerGodkjennUtkast::class)
        val oppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
        oppdatertDeltaker.vedtaksinformasjon!!.fattet shouldBeCloseTo LocalDateTime.now()
    }

    @Test
    fun `fattVedtak - vedtak kunne ikke fattes - upserter ikke`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker, fattet = LocalDateTime.now())
        val ansatt = TestData.lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = TestData.lagNavEnhet(id = vedtak.opprettetAvEnhet)
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                deltakerService.fattVedtak(deltaker.id, vedtak.id)
            }
        }

        val ikkeOppdatertDeltaker = deltakerService.get(deltaker.id).getOrThrow()

        ikkeOppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
    }

    @Test
    fun `oppdaterSistBesokt - produserer hendelse`() {
        val deltaker = TestData.lagDeltaker()
        val sistBesokt = ZonedDateTime.now()

        TestRepository.insert(deltaker)

        runBlocking {
            deltakerService.oppdaterSistBesokt(deltaker.id, sistBesokt)
        }

        assertProducedHendelse(deltaker.id, HendelseType.DeltakerSistBesokt::class)
    }

    @Test
    fun `feilregistrerDeltaker - deltaker feilregistreres og oppdatert deltaker produseres`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        val vedtak = TestData.lagVedtak(deltakerVedVedtak = deltaker)
        val ansatt = TestData.lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = TestData.lagNavEnhet(id = vedtak.opprettetAvEnhet)
        val deltakerEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = ansatt.id,
            endretAvEnhet = enhet.id,
        )
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak, deltakerEndring)

        runBlocking {
            deltakerService.feilregistrerDeltaker(deltaker.id)

            val deltakerFraDb = deltakerService.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.FEILREGISTRERT
            deltakerFraDb.startdato shouldBe null
            deltakerFraDb.sluttdato shouldBe null
            deltakerFraDb.dagerPerUke shouldBe null
            deltakerFraDb.deltakelsesprosent shouldBe null
            deltakerFraDb.bakgrunnsinformasjon shouldBe null
            deltakerFraDb.innhold shouldBe emptyList()

            assertProducedFeilregistrert(deltaker.id)
        }
    }
}
