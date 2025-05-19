package no.nav.amt.deltaker.job

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorService
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerDtoMapperService
import no.nav.amt.deltaker.deltaker.model.Kilde
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakerStatusOppdateringTest {
    companion object {
        private val deltakerRepository: DeltakerRepository = DeltakerRepository()
        private lateinit var deltakerService: DeltakerService

        private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonClient())
        private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonClient(), navEnhetService)
        private val deltakerEndringRepository = DeltakerEndringRepository()
        private val vedtakRepository = VedtakRepository()
        private val forslagRepository = ForslagRepository()
        private val endringFraArrangorRepository = EndringFraArrangorRepository()
        private val importertFraArenaRepository = ImportertFraArenaRepository()
        private val kafkaProducer = Producer<String, String>(LocalKafkaConfig(SingletonKafkaProvider.getHost()))
        private val deltakerHistorikkService =
            DeltakerHistorikkService(
                deltakerEndringRepository,
                vedtakRepository,
                forslagRepository,
                endringFraArrangorRepository,
                importertFraArenaRepository,
                InnsokPaaFellesOppstartRepository(),
                EndringFraTiltakskoordinatorRepository(),
                vurderingService = VurderingService(VurderingRepository()),
            )
        private val vurderingRepository = VurderingRepository()
        private val unleashToggle = mockk<UnleashToggle>()
        private val deltakerDtoMapperService =
            DeltakerDtoMapperService(navAnsattService, navEnhetService, deltakerHistorikkService, vurderingRepository)
        private val deltakerProducer = DeltakerProducer(kafkaProducer)
        private val deltakerV1Producer = DeltakerV1Producer(kafkaProducer)
        private val deltakerProducerService =
            DeltakerProducerService(deltakerDtoMapperService, deltakerProducer, deltakerV1Producer, unleashToggle)
        private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        private val vurderingService = VurderingService(vurderingRepository)
        private val hendelseService = HendelseService(
            HendelseProducer(kafkaProducer),
            navAnsattService,
            navEnhetService,
            arrangorService,
            deltakerHistorikkService,
            vurderingService,
        )
        private val forslagService = ForslagService(forslagRepository, mockk(), deltakerRepository, deltakerProducerService)

        private val deltakerEndringService = DeltakerEndringService(
            deltakerEndringRepository = deltakerEndringRepository,
            navAnsattService = navAnsattService,
            navEnhetService = navEnhetService,
            hendelseService = hendelseService,
            forslagService = forslagService,
            deltakerHistorikkService = deltakerHistorikkService,
        )
        private val vedtakService = VedtakService(vedtakRepository)
        private val endringFraArrangorService = EndringFraArrangorService(
            endringFraArrangorRepository = endringFraArrangorRepository,
            hendelseService = hendelseService,
            deltakerHistorikkService = deltakerHistorikkService,
        )

        private val endringFraTiltakskoordinatorService = EndringFraTiltakskoordinatorService(
            EndringFraTiltakskoordinatorRepository(),
            navAnsattService,
        )

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgres16Container

            deltakerService = DeltakerService(
                deltakerRepository,
                deltakerEndringService,
                deltakerProducerService,
                vedtakService,
                hendelseService,
                endringFraArrangorService,
                forslagService,
                importertFraArenaRepository,
                deltakerHistorikkService,
                unleashToggle,
                endringFraTiltakskoordinatorService,
                amtTiltakClient = mockk(),
                navAnsattService,
                navEnhetService,
            )
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
        every { unleashToggle.erKometMasterForTiltakstype(any()) } returns true
    }

    @Test
    fun `oppdaterDeltakerStatuser - startdato er passert - setter status DELTAR`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().minusDays(1),
            sluttdato = LocalDate.now().plusWeeks(2),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        runBlocking {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerFraDb.sluttdato shouldBe deltaker.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - startdato er passert men komet er ikke master - setter ikke status til DELTAR`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().minusDays(1),
            sluttdato = LocalDate.now().plusWeeks(2),
            kilde = Kilde.ARENA,
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        every { unleashToggle.erKometMasterForTiltakstype(any()) } returns false

        runBlocking {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - sluttdato er passert, ikke kurs - setter status HAR_SLUTTET`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusWeeks(1),
            sluttdato = LocalDate.now().minusDays(2),
            deltakerliste = TestData.lagDeltakerliste(
                oppstart = Deltakerliste.Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().plusMonths(3),
            ),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        runBlocking {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            deltakerFraDb.sluttdato shouldBe deltaker.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - sluttdato er passert, ikke kurs, har fremtidig status - bruker fremtidig status HAR_SLUTTET`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusWeeks(1),
            sluttdato = LocalDate.now().minusDays(2),
            deltakerliste = TestData.lagDeltakerliste(
                oppstart = Deltakerliste.Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().plusMonths(3),
            ),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)
        val fremtidigStatus = TestData.lagDeltakerStatus(
            type = DeltakerStatus.Type.HAR_SLUTTET,
            aarsak = DeltakerStatus.Aarsak.Type.FATT_JOBB,
            gyldigFra = LocalDateTime.now(),
            gyldigTil = null,
        )
        TestRepository.insert(fremtidigStatus, deltaker.id)

        runBlocking {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            deltakerFraDb.sluttdato shouldBe deltaker.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - sluttdato er passert, kurs - setter status FULLFORT`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusWeeks(1),
            sluttdato = LocalDate.now().minusDays(2),
            deltakerliste = TestData.lagDeltakerliste(
                oppstart = Deltakerliste.Oppstartstype.FELLES,
                sluttDato = LocalDate.now().minusDays(2),
            ),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        runBlocking {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.FULLFORT
            deltakerFraDb.sluttdato shouldBe deltaker.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - sluttdato er passert og tidligere enn kursets sluttdato - setter status AVBRUTT`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusWeeks(1),
            sluttdato = LocalDate.now().minusDays(2),
            deltakerliste = TestData.lagDeltakerliste(
                oppstart = Deltakerliste.Oppstartstype.FELLES,
                sluttDato = LocalDate.now().plusDays(2),
            ),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        runBlocking {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.AVBRUTT
            deltakerFraDb.sluttdato shouldBe deltaker.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - deltakerliste avsluttet, status DELTAR - setter status HAR_SLUTTET, oppdatert sluttdato`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().plusDays(2),
            deltakerliste = TestData.lagDeltakerliste(
                oppstart = Deltakerliste.Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = Deltakerliste.Status.AVSLUTTET,
            ),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        runBlocking {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            deltakerFraDb.status.aarsak shouldBe null
            deltakerFraDb.sluttdato shouldBe deltaker.deltakerliste.sluttDato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - deltakerliste avsluttet, status VENTER_PA_OPPSTART - setter status IKKE_AKTUELL`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
            deltakerliste = TestData.lagDeltakerliste(
                oppstart = Deltakerliste.Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = Deltakerliste.Status.AVSLUTTET,
            ),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        runBlocking {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            deltakerFraDb.status.aarsak shouldBe null
            deltakerFraDb.sluttdato shouldBe null
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - deltakerliste avlyst, status DELTAR - setter status HAR_SLUTTET med sluttarsak`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().plusDays(2),
            deltakerliste = TestData.lagDeltakerliste(
                oppstart = Deltakerliste.Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = Deltakerliste.Status.AVLYST,
            ),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        runBlocking {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
            deltakerFraDb.sluttdato shouldBe deltaker.deltakerliste.sluttDato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - deltakerliste avbrutt, status VENTER_PA_OPPSTART - setter status IKKE_AKTUELL med sluttarsak`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
            deltakerliste = TestData.lagDeltakerliste(
                oppstart = Deltakerliste.Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = Deltakerliste.Status.AVBRUTT,
            ),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        runBlocking {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
            deltakerFraDb.sluttdato shouldBe null
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - deltakerliste avbrutt, status UTKAST_TIL_PAMELDING - setter status AVBRUTT_UTKAST`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            startdato = null,
            sluttdato = null,
            deltakerliste = TestData.lagDeltakerliste(
                oppstart = Deltakerliste.Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = Deltakerliste.Status.AVBRUTT,
            ),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
        )
        TestRepository.insert(deltaker, vedtak)

        runBlocking {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.AVBRUTT_UTKAST
            deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
            deltakerFraDb.sluttdato shouldBe null
            assertProducedHendelse(deltaker.id, HendelseType.AvbrytUtkast::class)
        }
    }

    @Test
    fun `avsluttDeltakelserPaaDeltakerliste - deltakerliste avlyst - setter riktig status og sluttarsak`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltakerliste = TestData.lagDeltakerliste(
            oppstart = Deltakerliste.Oppstartstype.LOPENDE,
            sluttDato = LocalDate.now().minusDays(2),
            status = Deltakerliste.Status.AVLYST,
        )
        TestRepository.insert(deltakerliste)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().plusDays(2),
            deltakerliste = deltakerliste,
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)
        val deltaker2 = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().minusDays(2),
            deltakerliste = deltakerliste,
        )
        val vedtak2 = TestData.lagVedtak(
            deltakerId = deltaker2.id,
            deltakerVedVedtak = deltaker2,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker2, vedtak2)

        runBlocking {
            deltakerService.avsluttDeltakelserPaaDeltakerliste(deltakerliste)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
            deltakerFraDb.sluttdato shouldBe deltakerliste.sluttDato
            val deltaker2FraDb = deltakerRepository.get(deltaker2.id).getOrThrow()
            deltaker2FraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            deltaker2FraDb.status.aarsak?.type shouldBe null
            deltaker2FraDb.sluttdato shouldBe deltaker2.sluttdato
        }
    }
}
