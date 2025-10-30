package no.nav.amt.deltaker.deltaker.kafka

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.apiclients.mulighetsrommet.GjennomforingV2Response
import no.nav.amt.deltaker.apiclients.mulighetsrommet.MulighetsrommetApiClient
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadMapperService
import no.nav.amt.deltaker.deltaker.kafka.dto.EnkeltplassDeltakerPayload
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class EnkeltplassDeltakerConsumerTest {
    companion object {
        private val unleashToggle = mockk<UnleashToggle>()
        private val mulighetsrommetApiClient = mockk<MulighetsrommetApiClient>()
        private val arrangorService = mockk<ArrangorService>()
        private val navBrukerService = mockk<NavBrukerService>()
        private val deltakerKafkaPayloadMapperService = mockk<DeltakerKafkaPayloadMapperService>()
        private val deltakerProducer = mockk<DeltakerProducer>()
        private val deltakerRepository = spyk(DeltakerRepository())
        private val importertFraArenaRepository = ImportertFraArenaRepository()
        private val deltakerlisteRepository = DeltakerlisteRepository()
        private val tiltakstypeRepository = mockk<TiltakstypeRepository>()
        private val deltakerProducerService = spyk(
            DeltakerProducerService(
                deltakerKafkaPayloadMapperService = deltakerKafkaPayloadMapperService,
                deltakerProducer = deltakerProducer,
                deltakerV1Producer = mockk(),
                unleashToggle = unleashToggle,
            ),
        )
        private val deltakerService = spyk(
            DeltakerService(
                deltakerRepository = deltakerRepository,
                deltakerProducerService = deltakerProducerService,
                importertFraArenaRepository = importertFraArenaRepository,
                unleashToggle = unleashToggle,
                deltakerEndringService = mockk(),
                navAnsattService = mockk(),
                vedtakService = mockk(),
                hendelseService = mockk(),
                endringFraArrangorService = mockk(),
                forslagService = mockk(),
                deltakerHistorikkService = mockk(),
                endringFraTiltakskoordinatorService = mockk(),
                endringFraTiltakskoordinatorRepository = mockk(),
                navEnhetService = mockk(),
            ),
        )

        private val consumer = EnkeltplassDeltakerConsumer(
            deltakerService,
            deltakerlisteRepository,
            navBrukerService,
            importertFraArenaRepository,
            unleashToggle,
            mulighetsrommetApiClient,
            arrangorService,
            tiltakstypeRepository,
        )

        @JvmStatic
        @BeforeAll
        fun setup() {
            @Suppress("UnusedExpression")
            SingletonPostgres16Container
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
        clearAllMocks()
    }

    private fun toPayload(
        deltaker: Deltaker,
        registrertDato: LocalDateTime = deltaker.opprettet,
        statusEndretDato: LocalDateTime = deltaker.status.gyldigFra,
        innsokBegrunnelse: String? = null,
    ) = EnkeltplassDeltakerPayload(
        id = deltaker.id,
        gjennomforingId = deltaker.deltakerliste.id,
        personIdent = deltaker.navBruker.personident,
        startDato = deltaker.startdato,
        sluttDato = deltaker.sluttdato,
        status = deltaker.status.type,
        statusAarsak = deltaker.status.aarsak,
        dagerPerUke = deltaker.dagerPerUke,
        prosentDeltid = deltaker.deltakelsesprosent,
        registrertDato = registrertDato,
        statusEndretDato = statusEndretDato,
        innsokBegrunnelse = innsokBegrunnelse,
    )

    @Test
    fun `consumeDeltaker - skalLeseArenaDataForTiltakstype=false - lagrer ikke enkeltplasser og produserer ikke til deltaker-v2 topic`() {
        val deltakerListe = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )
        TestRepository.insert(deltakerListe)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerListe,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR, opprettet = sistEndret),
            sistEndret = sistEndret,
        )

        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false
        runBlocking {
            consumer.consumeDeltaker(toPayload(deltaker))
        }

        coVerify(exactly = 0) { deltakerService.upsertDeltaker(any()) }
        verify(exactly = 0) { deltakerProducer.produce(any()) }
    }

    @Test
    fun `consumeDeltaker - gjennomføring er allerede lagret - lagrer enkeltplasser og produserer til deltaker-v2`() {
        val deltakerListe = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )
        TestRepository.insert(deltakerListe)

        val statusOpprettet = LocalDateTime.now().minusWeeks(1)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerListe,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR, opprettet = statusOpprettet),
            sistEndret = sistEndret,
        )
        TestRepository.insert(deltaker.navBruker)
        val importertFraArena = DeltakerHistorikk.ImportertFraArena(
            importertFraArena = ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = deltaker.toDeltakerVedImport(LocalDate.now()),
            ),
        )

        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns true
        every { unleashToggle.erKometMasterForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false
        coEvery { deltakerKafkaPayloadMapperService.tilDeltakerPayload(any()) } returns DeltakerKafkaPayloadBuilder(
            deltaker = deltaker,
            deltakerhistorikk = listOf(importertFraArena),
            vurderinger = emptyList(),
            navAnsatt = null,
            navEnhet = null,
            forcedUpdate = null,
        )
        every { deltakerProducer.produce(any()) } just Runs
        coEvery { navBrukerService.get(deltaker.navBruker.personident) } returns Result.success(deltaker.navBruker)

        runBlocking {
            consumer.consumeDeltaker(toPayload(deltaker))
        }

        coVerify(exactly = 1) {
            deltakerService.upsertDeltaker(
                match {
                    it.id == deltaker.id &&
                        it.deltakerliste.id == deltaker.deltakerliste.id &&
                        it.status.type == deltaker.status.type &&
                        it.bakgrunnsinformasjon == null // comes from payload
                },
                eq(false),
                isNull(),
            )
        }
        coVerify(exactly = 1) {
            deltakerProducerService.produce(any(), any(), any())
        }
        verify { deltakerProducer.produce(any()) }

        val deltakerFromDb = deltakerService.get(deltaker.id).getOrThrow()
        deltakerFromDb.shouldNotBeNull()
        val importertFraArenaFromDb = importertFraArenaRepository.getForDeltaker(deltaker.id)
        importertFraArenaFromDb.shouldNotBeNull()

        deltakerFromDb.id shouldBe deltaker.id
        deltakerFromDb.deltakerliste.id shouldBe deltaker.deltakerliste.id
        deltakerFromDb.status.type shouldBe deltaker.status.type
        deltakerFromDb.bakgrunnsinformasjon.shouldBeNull() // comes from payload

        importertFraArenaFromDb.deltakerId shouldBe importertFraArena.importertFraArena.deltakerId
        importertFraArenaFromDb.deltakerVedImport.status.type shouldBe importertFraArena.importertFraArena.deltakerVedImport.status.type
        importertFraArenaFromDb.importertDato.truncatedTo(ChronoUnit.SECONDS) shouldBe
            importertFraArena.importertFraArena.importertDato.truncatedTo(ChronoUnit.SECONDS)
    }

    @Test
    fun `consumeDeltaker - gjennomføring eksisterer ikke i db - henter gjennomføring synkront fra mulighetsrommet api og lagrer`() {
        val deltakerListe = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )

        val statusOpprettet = LocalDateTime.now().minusWeeks(1)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerListe,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR, opprettet = statusOpprettet),
            sistEndret = sistEndret,
        )
        TestRepository.insert(deltakerListe.arrangor)
        TestRepository.insert(deltakerListe.tiltakstype)
        TestRepository.insert(deltaker.navBruker)
        val importertFraArena = DeltakerHistorikk.ImportertFraArena(
            importertFraArena = ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = deltaker.toDeltakerVedImport(LocalDate.now()),
            ),
        )
        coEvery { arrangorService.hentArrangor(deltakerListe.arrangor.organisasjonsnummer) } returns deltakerListe.arrangor
        coEvery { tiltakstypeRepository.get(deltakerListe.tiltakstype.tiltakskode) } returns Result.success(
            deltakerListe.tiltakstype,
        )
        coEvery { mulighetsrommetApiClient.hentGjennomforingV2(deltakerListe.id) } returns deltakerListe.toV2Response()
        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns true
        every { unleashToggle.erKometMasterForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false
        coEvery { deltakerKafkaPayloadMapperService.tilDeltakerPayload(any()) } returns DeltakerKafkaPayloadBuilder(
            deltaker = deltaker,
            deltakerhistorikk = listOf(importertFraArena),
            vurderinger = emptyList(),
            navAnsatt = null,
            navEnhet = null,
            forcedUpdate = null,
        )
        every { deltakerProducer.produce(any()) } just Runs
        coEvery { navBrukerService.get(deltaker.navBruker.personident) } returns Result.success(deltaker.navBruker)

        runBlocking {
            consumer.consumeDeltaker(toPayload(deltaker))
        }

        coVerify(exactly = 1) {
            deltakerService.upsertDeltaker(any(), false, null)
        }
        coVerify(exactly = 1) {
            deltakerProducerService.produce(any(), any(), any())
        }
        verify { deltakerProducer.produce(any()) }

        coVerify(exactly = 1) { mulighetsrommetApiClient.hentGjennomforingV2(deltakerListe.id) }

        val deltakerFromDb = deltakerService.get(deltaker.id).getOrThrow()
        deltakerFromDb.shouldNotBeNull()
        val importertFraArenaFromDb = importertFraArenaRepository.getForDeltaker(deltaker.id)
        importertFraArenaFromDb.shouldNotBeNull()

        val expectedDeltaker = deltaker.copy(
            status = deltaker.status.copy(id = deltakerFromDb.status.id, opprettet = deltakerFromDb.status.opprettet),
            bakgrunnsinformasjon = null,
            sistEndret = deltakerFromDb.sistEndret,
            opprettet = deltakerFromDb.opprettet,
        )

        deltakerFromDb shouldBe expectedDeltaker

        importertFraArenaFromDb.deltakerId shouldBe importertFraArena.importertFraArena.deltakerId
        importertFraArenaFromDb.deltakerVedImport.status.type shouldBe importertFraArena.importertFraArena.deltakerVedImport.status.type
        importertFraArenaFromDb.importertDato.truncatedTo(ChronoUnit.MINUTES) shouldBe
            importertFraArena.importertFraArena.importertDato.truncatedTo(ChronoUnit.MINUTES)
    }

    @Test
    fun `consumeDeltaker - Deltaker eksisterer allerede - lagrer deltaker og importertFraArenaData med ny status`() {
        val deltakerListe = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )

        val statusOpprettet = LocalDateTime.now().minusWeeks(1)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerListe,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR, opprettet = statusOpprettet),
            sistEndret = sistEndret,
        )
        val nyStatus = TestData
            .lagDeltakerStatus(type = DeltakerStatus.Type.FULLFORT, opprettet = LocalDate.now().atStartOfDay())
        val deltakerMedNyStatus = deltaker.copy(
            status = nyStatus,
            sluttdato = LocalDate.now().minusDays(1),
        )

        TestRepository.insert(deltaker)
        TestRepository.insert(deltakerListe.tiltakstype)
        val importertFraArena = DeltakerHistorikk.ImportertFraArena(
            importertFraArena = ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = deltakerMedNyStatus.toDeltakerVedImport(LocalDate.now()),
            ),
        )

        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns true
        every { unleashToggle.erKometMasterForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false
        coEvery { deltakerKafkaPayloadMapperService.tilDeltakerPayload(any()) } returns DeltakerKafkaPayloadBuilder(
            deltaker = deltakerMedNyStatus,
            deltakerhistorikk = listOf(importertFraArena),
            vurderinger = emptyList(),
            navAnsatt = null,
            navEnhet = null,
            forcedUpdate = null,
        )
        every { deltakerProducer.produce(any()) } just Runs
        coEvery { navBrukerService.get(deltaker.navBruker.personident) } returns Result.success(deltaker.navBruker)

        runBlocking {
            consumer.consumeDeltaker(toPayload(deltakerMedNyStatus))
        }

        coVerify(exactly = 1) {
            deltakerService.upsertDeltaker(any(), false, null)
        }
        coVerify(exactly = 1) {
            deltakerProducerService.produce(any(), any(), any())
        }
        verify { deltakerProducer.produce(any()) }

        val deltakerFromDb = deltakerService.get(deltaker.id).getOrThrow()
        deltakerFromDb.shouldNotBeNull()
        val importertFraArenaFromDb = importertFraArenaRepository.getForDeltaker(deltaker.id)
        importertFraArenaFromDb.shouldNotBeNull()

        val expectedDeltaker = deltakerMedNyStatus.copy(
            status = deltakerMedNyStatus.status.copy(id = deltakerFromDb.status.id, opprettet = deltakerFromDb.status.opprettet),
            bakgrunnsinformasjon = null,
            sistEndret = deltakerFromDb.sistEndret,
            opprettet = deltakerFromDb.opprettet,
        )

        expectedDeltaker shouldBe deltakerFromDb

        importertFraArenaFromDb.deltakerId shouldBe importertFraArena.importertFraArena.deltakerId
        importertFraArenaFromDb.deltakerVedImport.status.type shouldBe importertFraArena.importertFraArena.deltakerVedImport.status.type
        importertFraArenaFromDb.importertDato.truncatedTo(ChronoUnit.MINUTES) shouldBe
            importertFraArena.importertFraArena.importertDato.truncatedTo(ChronoUnit.MINUTES)
    }

    @Test
    fun `consumeDeltaker - Deltaker eksisterer, lik status, andre endringer på deltaker - lagrer deltaker`() {
        val deltakerListe = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )

        val statusOpprettet = LocalDateTime.now().minusWeeks(1)
        val sistEndret = LocalDateTime.now().minusDays(1)
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerListe,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR, opprettet = statusOpprettet),
            sistEndret = sistEndret,
        )

        val endretDeltaker = deltaker.copy(
            sluttdato = LocalDate.now().plusDays(1),
        )

        TestRepository.insert(deltaker)
        TestRepository.insert(deltakerListe.tiltakstype)
        val importertFraArena = DeltakerHistorikk.ImportertFraArena(
            importertFraArena = ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now(),
                deltakerVedImport = endretDeltaker.toDeltakerVedImport(LocalDate.now()),
            ),
        )

        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns true
        every { unleashToggle.erKometMasterForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns false
        coEvery { deltakerKafkaPayloadMapperService.tilDeltakerPayload(any()) } returns DeltakerKafkaPayloadBuilder(
            deltaker = endretDeltaker,
            deltakerhistorikk = listOf(importertFraArena),
            vurderinger = emptyList(),
            navAnsatt = null,
            navEnhet = null,
            forcedUpdate = null,
        )
        every { deltakerProducer.produce(any()) } just Runs
        coEvery { navBrukerService.get(deltaker.navBruker.personident) } returns Result.success(deltaker.navBruker)

        runBlocking {
            consumer.consumeDeltaker(toPayload(endretDeltaker))
        }

        coVerify(exactly = 1) {
            deltakerService.upsertDeltaker(any(), false, null)
        }
        coVerify(exactly = 1) {
            deltakerProducerService.produce(any(), any(), any())
        }
        verify { deltakerProducer.produce(any()) }

        val deltakerFromDb = deltakerService.get(deltaker.id).getOrThrow()
        deltakerFromDb.shouldNotBeNull()
        val importertFraArenaFromDb = importertFraArenaRepository.getForDeltaker(deltaker.id)
        importertFraArenaFromDb.shouldNotBeNull()

        val expectedDeltaker = endretDeltaker.copy(
            status = endretDeltaker.status.copy(opprettet = deltakerFromDb.status.opprettet),
            bakgrunnsinformasjon = null,
            sistEndret = deltakerFromDb.sistEndret,
            opprettet = deltakerFromDb.opprettet,
        )

        expectedDeltaker shouldBe deltakerFromDb

        importertFraArenaFromDb.deltakerId shouldBe importertFraArena.importertFraArena.deltakerId
        importertFraArenaFromDb.deltakerVedImport.status.type shouldBe importertFraArena.importertFraArena.deltakerVedImport.status.type
        importertFraArenaFromDb.importertDato.truncatedTo(ChronoUnit.MINUTES) shouldBe
            importertFraArena.importertFraArena.importertDato.truncatedTo(ChronoUnit.MINUTES)
    }

    private fun Deltakerliste.toV2Response() = GjennomforingV2Response(
        id = id,
        tiltakstype = GjennomforingV2Response.Tiltakstype(tiltakstype.tiltakskode.toString()),
        navn = navn,
        startDato = startDato,
        sluttDato = sluttDato,
        status = status.toString(),
        oppstart = oppstart,
        apentForPamelding = apentForPamelding,
        arrangor = GjennomforingV2Response.Arrangor(arrangor.organisasjonsnummer),
    )
}
