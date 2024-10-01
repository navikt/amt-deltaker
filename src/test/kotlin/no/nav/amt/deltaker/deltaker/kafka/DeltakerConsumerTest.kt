package no.nav.amt.deltaker.deltaker.kafka

import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.amtperson.AmtPersonServiceClient
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.model.Kilde
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.deltaker.kafka.utils.assertOnProducedDeltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestData.toDeltakerV2
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.awaitility.Awaitility
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class DeltakerConsumerTest {
    companion object {
        lateinit var deltakerRepository: DeltakerRepository
        lateinit var importertFraArenaRepository: ImportertFraArenaRepository
        lateinit var deltakerlisteRepository: DeltakerlisteRepository
        lateinit var navBrukerService: NavBrukerService
        lateinit var navBrukerRepository: NavBrukerRepository
        lateinit var amtPersonServiceClient: AmtPersonServiceClient
        lateinit var navEnhetService: NavEnhetService
        lateinit var navAnsattService: NavAnsattService
        lateinit var unleashToggle: UnleashToggle
        lateinit var consumer: DeltakerConsumer
        lateinit var deltakerProducer: DeltakerProducer
        lateinit var deltakerV1Producer: DeltakerV1Producer
        lateinit var deltakerEndringService: DeltakerEndringService
        lateinit var deltakerV2MapperService: DeltakerV2MapperService
        lateinit var deltakerProducerService: DeltakerProducerService
        lateinit var deltakerHistorikkService: DeltakerHistorikkService
        lateinit var deltakerEndringRepository: DeltakerEndringRepository
        lateinit var vedtakRepository: VedtakRepository
        lateinit var forslagRepository: ForslagRepository
        lateinit var endringFraArrangorRepository: EndringFraArrangorRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgres16Container
            deltakerRepository = DeltakerRepository()
            importertFraArenaRepository = ImportertFraArenaRepository()
            deltakerlisteRepository = DeltakerlisteRepository()
            navBrukerRepository = NavBrukerRepository()
            amtPersonServiceClient = mockk()
            navEnhetService = mockk()
            navAnsattService = NavAnsattService(mockk(), amtPersonServiceClient)
            navBrukerService = NavBrukerService(navBrukerRepository, amtPersonServiceClient, navEnhetService, navAnsattService)
            unleashToggle = mockk()
            deltakerEndringRepository = mockk()
            vedtakRepository = mockk()
            forslagRepository = mockk()
            endringFraArrangorRepository = mockk()
            deltakerHistorikkService = DeltakerHistorikkService(
                deltakerEndringRepository,
                vedtakRepository,
                forslagRepository,
                endringFraArrangorRepository,
                importertFraArenaRepository,
            )
            deltakerEndringService = mockk()
            deltakerV2MapperService = DeltakerV2MapperService(navAnsattService, navEnhetService, deltakerHistorikkService)
            deltakerProducer = DeltakerProducer(
                LocalKafkaConfig(SingletonKafkaProvider.getHost()),
            )
            deltakerV1Producer = mockk(relaxed = true)
            deltakerProducerService = DeltakerProducerService(deltakerV2MapperService, deltakerProducer, deltakerV1Producer, unleashToggle)
            consumer = DeltakerConsumer(
                deltakerRepository,
                deltakerlisteRepository,
                navBrukerService,
                deltakerEndringService,
                importertFraArenaRepository,
                deltakerProducerService,
            )
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
        clearMocks(deltakerV1Producer, deltakerEndringService)
    }

    @Test
    fun `consumeDeltaker - ny KOMET deltaker - lagrer ikke deltaker`(): Unit = runBlocking {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        TestRepository.insert(deltakerliste)
        val deltaker = TestData.lagDeltaker(kilde = Kilde.KOMET, deltakerliste = deltakerliste)

        val deltakerV2Dto = deltaker.toDeltakerV2()

        consumer.consume(deltaker.id, objectMapper.writeValueAsString(deltakerV2Dto))
        Awaitility.await().atLeast(5, TimeUnit.SECONDS)
        deltakerRepository.get(deltaker.id).getOrNull() shouldBe null
        importertFraArenaRepository.getForDeltaker(deltaker.id) shouldBe null
    }

    @Test
    fun `consumeDeltaker - ny ARENA deltaker - lagrer deltaker`(): Unit = runBlocking {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        TestRepository.insert(deltakerliste)
        val statusOpprettet = LocalDateTime.now().minusWeeks(1)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerliste,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR, opprettet = statusOpprettet),
            sistEndret = sistEndret,
        )

        TestRepository.insert(deltaker.navBruker)
        every { deltakerEndringService.getForDeltaker(deltaker.id) } returns emptyList()
        every { deltakerEndringRepository.getForDeltaker(deltaker.id) } returns emptyList()
        every { vedtakRepository.getForDeltaker(deltaker.id) } returns emptyList()
        every { forslagRepository.getForDeltaker(deltaker.id) } returns emptyList()
        every { endringFraArrangorRepository.getForDeltaker(deltaker.id) } returns emptyList()

        val deltakerV2Dto = deltaker.toDeltakerV2()

        consumer.consume(deltaker.id, objectMapper.writeValueAsString(deltakerV2Dto))
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            deltakerRepository.get(deltaker.id).getOrNull() != null
        }
        val expectedHistorikk = DeltakerHistorikk.ImportertFraArena(
            importertFraArena = ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = deltaker.toDeltakerVedImport(deltakerV2Dto.innsoktDato),
            ),
        )

        val expectedProducedDeltaker = deltakerV2Dto.copy(
            historikk = listOf(expectedHistorikk),
            bestillingTekst = null,
            forsteVedtakFattet = expectedHistorikk.importertFraArena.deltakerVedImport.innsoktDato,
        )

        assertOnProducedDeltaker(expectedProducedDeltaker)
        verify(exactly = 0) { deltakerV1Producer.produce(any()) }

        val insertedDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

        insertedDeltaker.deltakerliste.id shouldBe deltaker.deltakerliste.id
        insertedDeltaker.startdato shouldBe deltaker.startdato
        insertedDeltaker.sluttdato shouldBe deltaker.sluttdato
        insertedDeltaker.dagerPerUke shouldBe deltaker.dagerPerUke
        insertedDeltaker.deltakelsesprosent shouldBe deltaker.deltakelsesprosent
        insertedDeltaker.bakgrunnsinformasjon shouldBe null
        insertedDeltaker.deltakelsesinnhold shouldBe null
        insertedDeltaker.status.type shouldBe deltaker.status.type
        insertedDeltaker.status.opprettet shouldBeCloseTo statusOpprettet
        insertedDeltaker.vedtaksinformasjon shouldBe null
        insertedDeltaker.kilde shouldBe Kilde.ARENA
        insertedDeltaker.sistEndret shouldBeCloseTo sistEndret

        val importertFraArena = importertFraArenaRepository.getForDeltaker(deltaker.id)
            ?: throw RuntimeException("Fant ikke importert fra arena")
        importertFraArena.importertDato.toLocalDate() shouldBe LocalDate.now()
        importertFraArena.deltakerVedImport.innsoktDato shouldBe deltakerV2Dto.innsoktDato
        importertFraArena.deltakerVedImport.startdato shouldBe deltakerV2Dto.oppstartsdato
        importertFraArena.deltakerVedImport.sluttdato shouldBe deltakerV2Dto.sluttdato
        importertFraArena.deltakerVedImport.dagerPerUke shouldBe deltakerV2Dto.dagerPerUke
        importertFraArena.deltakerVedImport.deltakelsesprosent shouldBe deltakerV2Dto.prosentStilling?.toFloat()
        importertFraArena.deltakerVedImport.status.type shouldBe deltakerV2Dto.status.type
    }

    @Test
    fun `consumeDeltaker - oppdatert ARENA deltaker - lagrer deltaker uten bakgrunnsinfo`(): Unit = runBlocking {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakstype.Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val statusOpprettet = LocalDateTime.now().minusWeeks(1)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val innsoktDato = LocalDate.now().minusWeeks(2)
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerliste,
            innhold = null,
            bakgrunnsinformasjon = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR, opprettet = statusOpprettet),
            sistEndret = sistEndret,
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(
            ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = deltaker.toDeltakerVedImport(innsoktDato),
            ),
        )

        val oppdatertDeltaker = deltaker.copy(
            bakgrunnsinformasjon = "Test",
            startdato = LocalDate.now().minusDays(2),
        )

        every { deltakerEndringService.getForDeltaker(deltaker.id) } returns emptyList()
        every { deltakerEndringRepository.getForDeltaker(deltaker.id) } returns emptyList()
        every { vedtakRepository.getForDeltaker(deltaker.id) } returns emptyList()
        every { forslagRepository.getForDeltaker(deltaker.id) } returns emptyList()
        every { endringFraArrangorRepository.getForDeltaker(deltaker.id) } returns emptyList()

        val deltakerV2Dto = oppdatertDeltaker.toDeltakerV2(innsoktDato = innsoktDato)

        consumer.consume(deltaker.id, objectMapper.writeValueAsString(deltakerV2Dto))
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            deltakerRepository.get(deltaker.id).getOrNull() != null
        }
        val expectedHistorikk = DeltakerHistorikk.ImportertFraArena(
            importertFraArena = ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = oppdatertDeltaker.toDeltakerVedImport(deltakerV2Dto.innsoktDato),
            ),
        )

        val expectedProducedDeltaker = deltakerV2Dto.copy(
            historikk = listOf(expectedHistorikk),
            bestillingTekst = null,
            forsteVedtakFattet = expectedHistorikk.importertFraArena.deltakerVedImport.innsoktDato,
        )

        assertOnProducedDeltaker(expectedProducedDeltaker)
        verify(exactly = 0) { deltakerV1Producer.produce(any()) }

        val insertedDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

        insertedDeltaker.deltakerliste.id shouldBe oppdatertDeltaker.deltakerliste.id
        insertedDeltaker.startdato shouldBe oppdatertDeltaker.startdato
        insertedDeltaker.sluttdato shouldBe oppdatertDeltaker.sluttdato
        insertedDeltaker.dagerPerUke shouldBe oppdatertDeltaker.dagerPerUke
        insertedDeltaker.deltakelsesprosent shouldBe oppdatertDeltaker.deltakelsesprosent
        insertedDeltaker.bakgrunnsinformasjon shouldBe null
        insertedDeltaker.deltakelsesinnhold shouldBe null
        insertedDeltaker.status.type shouldBe oppdatertDeltaker.status.type
        insertedDeltaker.status.opprettet shouldBeCloseTo statusOpprettet
        insertedDeltaker.vedtaksinformasjon shouldBe null
        insertedDeltaker.kilde shouldBe Kilde.ARENA
        insertedDeltaker.sistEndret shouldBeCloseTo sistEndret

        val importertFraArena = importertFraArenaRepository.getForDeltaker(deltaker.id)
            ?: throw RuntimeException("Fant ikke importert fra arena")
        importertFraArena.importertDato.toLocalDate() shouldBe LocalDate.now()
        importertFraArena.deltakerVedImport.innsoktDato shouldBe deltakerV2Dto.innsoktDato
        importertFraArena.deltakerVedImport.startdato shouldBe deltakerV2Dto.oppstartsdato
        importertFraArena.deltakerVedImport.sluttdato shouldBe deltakerV2Dto.sluttdato
        importertFraArena.deltakerVedImport.dagerPerUke shouldBe deltakerV2Dto.dagerPerUke
        importertFraArena.deltakerVedImport.deltakelsesprosent shouldBe deltakerV2Dto.prosentStilling?.toFloat()
        importertFraArena.deltakerVedImport.status.type shouldBe deltakerV2Dto.status.type
    }
}
