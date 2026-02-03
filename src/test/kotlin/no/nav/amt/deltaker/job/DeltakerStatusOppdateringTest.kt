package no.nav.amt.deltaker.job

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.DatabaseTestExtension
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
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockPersonServiceClient
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.TestOutboxEnvironment
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakerStatusOppdateringTest {
    private val deltakerRepository: DeltakerRepository = DeltakerRepository()

    private val navEnhetRepository = NavEnhetRepository()
    private val navEnhetService = NavEnhetService(navEnhetRepository, mockPersonServiceClient())

    private val navAnsattRepository = NavAnsattRepository()
    private val navAnsattService = NavAnsattService(navAnsattRepository, mockPersonServiceClient(), navEnhetService)

    private val deltakerEndringRepository = DeltakerEndringRepository()
    private val vedtakRepository = VedtakRepository()
    private val forslagRepository = ForslagRepository()
    private val endringFraArrangorRepository = EndringFraArrangorRepository()
    private val importertFraArenaRepository = ImportertFraArenaRepository()
    private val deltakerHistorikkService =
        DeltakerHistorikkService(
            deltakerEndringRepository,
            vedtakRepository,
            forslagRepository,
            endringFraArrangorRepository,
            importertFraArenaRepository,
            InnsokPaaFellesOppstartRepository(),
            EndringFraTiltakskoordinatorRepository(),
            VurderingRepository(),
        )
    private val vurderingRepository = VurderingRepository()
    private val unleashToggle = mockk<UnleashToggle>()
    private val deltakerKafkaPayloadMapperService =
        DeltakerKafkaPayloadBuilder(navAnsattRepository, navEnhetRepository, deltakerHistorikkService, vurderingRepository)

    private val deltakerProducer = DeltakerProducer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)
    private val deltakerV1Producer = DeltakerV1Producer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)
    private val deltakerProducerService =
        DeltakerProducerService(deltakerKafkaPayloadMapperService, deltakerProducer, deltakerV1Producer, unleashToggle)
    private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
    private val vurderingService = VurderingService(vurderingRepository)
    private val hendelseService = HendelseService(
        HendelseProducer(TestOutboxEnvironment.outboxService),
        navAnsattRepository,
        navAnsattService,
        navEnhetRepository,
        navEnhetService,
        arrangorService,
        deltakerHistorikkService,
        vurderingService,
    )
    private val forslagService = ForslagService(forslagRepository, mockk(), deltakerRepository, deltakerProducerService)

    private val deltakerEndringService = DeltakerEndringService(
        deltakerEndringRepository = deltakerEndringRepository,
        navAnsattRepository = navAnsattRepository,
        navEnhetRepository = navEnhetRepository,
        hendelseService = hendelseService,
        forslagService = forslagService,
        deltakerHistorikkService = deltakerHistorikkService,
    )
    private val vedtakService = VedtakService(vedtakRepository)
    private val endringFraTiltakskoordinatorRepository = EndringFraTiltakskoordinatorRepository()

    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        deltakerEndringRepository = deltakerEndringRepository,
        deltakerEndringService = deltakerEndringService,
        deltakerProducerService = deltakerProducerService,
        vedtakRepository = vedtakRepository,
        vedtakService = vedtakService,
        hendelseService = hendelseService,
        endringFraArrangorRepository = endringFraArrangorRepository,
        forslagRepository = forslagRepository,
        importertFraArenaRepository = importertFraArenaRepository,
        deltakerHistorikkService = deltakerHistorikkService,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
    )

    companion object {
        @JvmField
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() {
        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
        every { unleashToggle.skalDelesMedEksterne(any<Tiltakskode>()) } returns true
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

        runTest {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerFraDb.sluttdato shouldBe deltaker.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - startdato er passert men komet er ikke master - setter status til DELTAR`() {
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

        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns false
        every { unleashToggle.skalLeseArenaDataForTiltakstype(any<Tiltakskode>()) } returns true

        runTest {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR
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
                oppstart = Oppstartstype.LOPENDE,
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

        runTest {
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
                oppstart = Oppstartstype.LOPENDE,
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
            gyldigFra = LocalDateTime.now().minusMinutes(1),
            gyldigTil = null,
        )
        TestRepository.insert(fremtidigStatus, deltaker.id)

        runTest {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            assertSoftly(deltakerFraDb) {
                status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
                sluttdato shouldBe deltaker.sluttdato
            }
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
                oppstart = Oppstartstype.FELLES,
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

        runTest {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.FULLFORT
            deltakerFraDb.sluttdato shouldBe deltaker.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltakerStatuser - sluttdato er passert og tidligere enn kursets sluttdato - setter status FULLFORT`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusWeeks(1),
            sluttdato = LocalDate.now().minusDays(2),
            deltakerliste = TestData.lagDeltakerliste(
                oppstart = Oppstartstype.FELLES,
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

        runTest {
            deltakerService.oppdaterDeltakerStatuser()

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.FULLFORT
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
                oppstart = Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = GjennomforingStatusType.AVSLUTTET,
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

        runTest {
            deltakerService.oppdaterDeltakerStatuser()

            assertSoftly(deltakerRepository.get(deltaker.id).getOrThrow()) {
                status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                status.aarsak shouldBe null
                sluttdato shouldBe deltaker.deltakerliste.sluttDato
            }
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
                oppstart = Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = GjennomforingStatusType.AVSLUTTET,
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

        runTest {
            deltakerService.oppdaterDeltakerStatuser()

            assertSoftly(deltakerRepository.get(deltaker.id).getOrThrow()) {
                status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
                status.aarsak shouldBe null
                sluttdato shouldBe null
            }
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
                oppstart = Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = GjennomforingStatusType.AVLYST,
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

        runTest {
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
                oppstart = Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = GjennomforingStatusType.AVBRUTT,
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

        runTest {
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
                oppstart = Oppstartstype.LOPENDE,
                sluttDato = LocalDate.now().minusDays(2),
                status = GjennomforingStatusType.AVBRUTT,
            ),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAv,
            opprettetAvEnhet = sistEndretAvEnhet,
        )
        TestRepository.insert(deltaker, vedtak)

        runTest {
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
            oppstart = Oppstartstype.LOPENDE,
            sluttDato = LocalDate.now().minusDays(2),
            status = GjennomforingStatusType.AVLYST,
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

        runTest {
            deltakerService.avsluttDeltakelserPaaDeltakerliste(deltakerliste)

            assertSoftly(deltakerRepository.get(deltaker.id).getOrThrow()) {
                status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
                sluttdato shouldBe deltakerliste.sluttDato
            }

            assertSoftly(deltakerRepository.get(deltaker2.id).getOrThrow()) {
                status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                status.aarsak?.type shouldBe null
                sluttdato shouldBe deltaker2.sluttdato
            }
        }
    }
}
