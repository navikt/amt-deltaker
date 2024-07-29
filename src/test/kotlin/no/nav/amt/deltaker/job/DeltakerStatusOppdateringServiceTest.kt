package no.nav.amt.deltaker.job

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV2MapperService
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
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
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakerStatusOppdateringServiceTest {
    companion object {
        private val deltakerRepository: DeltakerRepository = DeltakerRepository()
        lateinit var deltakerStatusOppdateringService: DeltakerStatusOppdateringService
        private lateinit var deltakerService: DeltakerService

        private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonClient())
        private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonClient())
        private val deltakerEndringRepository = DeltakerEndringRepository()
        private val vedtakRepository = VedtakRepository()
        private val forslagRepository = ForslagRepository()
        private val deltakerHistorikkService = DeltakerHistorikkService(deltakerEndringRepository, vedtakRepository, forslagRepository)
        private val deltakerV2MapperService = DeltakerV2MapperService(navAnsattService, navEnhetService, deltakerHistorikkService)
        private val deltakerProducer = DeltakerProducer(LocalKafkaConfig(SingletonKafkaProvider.getHost()), deltakerV2MapperService)
        private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        private val hendelseService = HendelseService(
            HendelseProducer(LocalKafkaConfig(SingletonKafkaProvider.getHost())),
            navAnsattService,
            navEnhetService,
            arrangorService,
            deltakerHistorikkService,
        )
        private val forslagService = ForslagService(forslagRepository, mockk(), deltakerRepository, deltakerProducer)

        private val deltakerEndringService = DeltakerEndringService(
            repository = deltakerEndringRepository,
            navAnsattService = navAnsattService,
            navEnhetService = navEnhetService,
            hendelseService = hendelseService,
            forslagService = forslagService,
        )
        private val vedtakService = VedtakService(vedtakRepository, hendelseService)

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
            deltakerService = DeltakerService(
                deltakerRepository,
                deltakerEndringService,
                deltakerProducer,
                vedtakService,
                hendelseService,
            )
            deltakerStatusOppdateringService = DeltakerStatusOppdateringService(deltakerRepository, deltakerService)
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
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
            deltakerStatusOppdateringService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerFraDb.sluttdato shouldBe deltaker.sluttdato
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
            deltakerStatusOppdateringService.oppdaterDeltakerStatuser()

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
            deltakerStatusOppdateringService.oppdaterDeltakerStatuser()

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
            deltakerStatusOppdateringService.oppdaterDeltakerStatuser()

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
            deltakerStatusOppdateringService.oppdaterDeltakerStatuser()

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
            deltakerStatusOppdateringService.oppdaterDeltakerStatuser()

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
            deltakerStatusOppdateringService.oppdaterDeltakerStatuser()

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
            deltakerStatusOppdateringService.oppdaterDeltakerStatuser()

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
            deltakerStatusOppdateringService.oppdaterDeltakerStatuser()

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
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(deltaker, vedtak)

        runBlocking {
            deltakerStatusOppdateringService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.AVBRUTT_UTKAST
            deltakerFraDb.status.aarsak shouldBe null
            deltakerFraDb.sluttdato shouldBe null
        }
    }

    @Test
    fun `avsluttDeltakelserForAvbruttDeltakerliste - deltakerliste avlyst - setter riktig status og sluttarsak`() {
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
            deltakerStatusOppdateringService.avsluttDeltakelserForAvbruttDeltakerliste(deltakerliste.id)

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
