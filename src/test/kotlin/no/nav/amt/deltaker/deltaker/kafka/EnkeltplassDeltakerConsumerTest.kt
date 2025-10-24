package no.nav.amt.deltaker.deltaker.kafka

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.apiclients.mulighetsrommet.MulighetsrommetApiClient
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.sammenlignHistorikk
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlistePayload
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

// TODO: fiks tester her
class EnkeltplassDeltakerConsumerTest {
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
        lateinit var consumer: EnkeltplassDeltakerConsumer
        lateinit var deltakerEndringService: DeltakerEndringService
        lateinit var deltakerHistorikkService: DeltakerHistorikkService
        lateinit var deltakerEndringRepository: DeltakerEndringRepository
        lateinit var vedtakRepository: VedtakRepository
        lateinit var forslagRepository: ForslagRepository
        lateinit var endringFraArrangorRepository: EndringFraArrangorRepository
        lateinit var vurderingRepository: VurderingRepository
        lateinit var mulighetsrommetApiClient: MulighetsrommetApiClient
        lateinit var arrangorService: ArrangorService
        lateinit var tiltakstypeRepository: TiltakstypeRepository

        @JvmStatic
        @BeforeAll
        fun setup() {
            @Suppress("UnusedExpression")
            SingletonPostgres16Container
            deltakerRepository = DeltakerRepository()
            importertFraArenaRepository = ImportertFraArenaRepository()
            deltakerlisteRepository = DeltakerlisteRepository()
            navBrukerRepository = NavBrukerRepository()
            amtPersonServiceClient = mockk()
            navEnhetService = mockk()
            navAnsattService = NavAnsattService(mockk(), amtPersonServiceClient, navEnhetService)
            navBrukerService = NavBrukerService(navBrukerRepository, amtPersonServiceClient, navEnhetService, navAnsattService)
            unleashToggle = mockk()
            deltakerEndringRepository = mockk()
            vedtakRepository = mockk()
            forslagRepository = mockk()
            mulighetsrommetApiClient = mockk()
            arrangorService = mockk()
            tiltakstypeRepository = TiltakstypeRepository()

            endringFraArrangorRepository = mockk()
            vurderingRepository = VurderingRepository()
            deltakerHistorikkService = DeltakerHistorikkService(
                deltakerEndringRepository,
                vedtakRepository,
                forslagRepository,
                endringFraArrangorRepository,
                importertFraArenaRepository,
                InnsokPaaFellesOppstartRepository(),
                EndringFraTiltakskoordinatorRepository(),
                vurderingService = VurderingService(vurderingRepository),
            )
            deltakerEndringService = mockk()
            consumer = EnkeltplassDeltakerConsumer(
                deltakerRepository,
                deltakerlisteRepository,
                navBrukerService,
                importertFraArenaRepository,
                unleashToggle,
                mulighetsrommetApiClient,
                arrangorService,
                tiltakstypeRepository,
            )
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
        clearMocks(deltakerEndringService)
    }

    private fun toPayload(
        deltaker: no.nav.amt.deltaker.deltaker.model.Deltaker,
        registrertDato: LocalDateTime = deltaker.opprettet,
        statusEndretDato: LocalDateTime = deltaker.status.gyldigFra,
        innsokBegrunnelse: String? = null,
    ) = no.nav.amt.deltaker.deltaker.kafka.dto.EnkeltplassDeltakerPayload(
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
    fun `consumeDeltaker - tombstone - gjør ingenting`(): Unit = runBlocking {
        val id = UUID.randomUUID()
        consumer.consume(id, null)
        // Ingenting skal settes in i databasen
        deltakerRepository.get(id).getOrNull().shouldBeNull()
        importertFraArenaRepository.getForDeltaker(id).shouldBeNull()
    }

    @Test
    fun `consumeDeltaker - unleash toggle off - lagrer ikke deltaker`(): Unit = runBlocking {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        TestRepository.insert(deltakerliste)
        val deltaker = TestData.lagDeltaker(kilde = Kilde.ARENA, deltakerliste = deltakerliste)

        val payload = toPayload(deltaker)

        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ARBEIDSFORBEREDENDE_TRENING) } returns false

        consumer.consume(deltaker.id, objectMapper.writeValueAsString(payload))

        val eventuallyConfig = eventuallyConfig {
            initialDelay = 1.seconds
            duration = 3.seconds
        }

        eventually(eventuallyConfig) {
            deltakerRepository.get(deltaker.id).getOrNull() shouldBe null
            importertFraArenaRepository.getForDeltaker(deltaker.id) shouldBe null
        }
    }

    @Test
    fun `consumeDeltaker - ny enkelplass ARENA deltaker - lagrer deltaker`(): Unit = runBlocking {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )

        TestRepository.insert(deltakerliste)
        val statusEndret = LocalDateTime.now().minusWeeks(1)
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerliste,
            innhold = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR, opprettet = statusEndret),
        )

        TestRepository.insert(deltaker.navBruker)
        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns true

        val payload = toPayload(deltaker, registrertDato = LocalDateTime.now().minusDays(2), statusEndretDato = statusEndret)

        consumer.consume(deltaker.id, objectMapper.writeValueAsString(payload))

        eventually<Unit>(5.seconds) {
            deltakerRepository.get(deltaker.id).getOrNull().shouldNotBeNull()
        }

        val insertedDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

        insertedDeltaker.deltakerliste.id shouldBe deltaker.deltakerliste.id
        insertedDeltaker.startdato shouldBe deltaker.startdato
        insertedDeltaker.sluttdato shouldBe deltaker.sluttdato
        insertedDeltaker.dagerPerUke shouldBe deltaker.dagerPerUke
        insertedDeltaker.deltakelsesprosent shouldBe deltaker.deltakelsesprosent
        insertedDeltaker.bakgrunnsinformasjon shouldBe null
        insertedDeltaker.deltakelsesinnhold shouldBe null
        insertedDeltaker.status.type shouldBe deltaker.status.type
        // status.gyldigFra blir satt fra payload.statusEndretDato
        insertedDeltaker.status.gyldigFra shouldBeCloseTo statusEndret
        insertedDeltaker.vedtaksinformasjon shouldBe null
        insertedDeltaker.kilde shouldBe Kilde.ARENA

        val importertFraArenaResult = importertFraArenaRepository.getForDeltaker(deltaker.id)
            ?: throw RuntimeException("Fant ikke importert fra arena")
        importertFraArenaResult.importertDato.toLocalDate() shouldBe LocalDate.now()
        importertFraArenaResult.deltakerVedImport.innsoktDato shouldBe payload.registrertDato.toLocalDate()
        importertFraArenaResult.deltakerVedImport.startdato shouldBe deltaker.startdato
        importertFraArenaResult.deltakerVedImport.sluttdato shouldBe deltaker.sluttdato
        importertFraArenaResult.deltakerVedImport.dagerPerUke shouldBe deltaker.dagerPerUke
        importertFraArenaResult.deltakerVedImport.deltakelsesprosent shouldBe deltaker.deltakelsesprosent
        importertFraArenaResult.deltakerVedImport.status.type shouldBe deltaker.status.type
    }

    @Test
    fun `consumeDeltaker - oppdatert ARENA enkelplass deltaker - lagrer deltaker`(): Unit = runBlocking {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
        )
        val statusEndret = LocalDateTime.now().minusWeeks(1)
        val innsoktDato = LocalDate.now().minusWeeks(2)
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerliste,
            innhold = null,
            bakgrunnsinformasjon = null,
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR, opprettet = statusEndret),
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(
            ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = LocalDateTime.now().minusMonths(3),
                deltakerVedImport = deltaker.toDeltakerVedImport(innsoktDato),
            ),
        )

        every { unleashToggle.skalLeseArenaDataForTiltakstype(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING) } returns true

        val oppdatertDeltaker = deltaker.copy(
            startdato = LocalDate.now().minusDays(2),
        )
        val payload = toPayload(
            oppdatertDeltaker,
            registrertDato = LocalDateTime.now().minusDays(10),
            statusEndretDato = statusEndret,
        )

        consumer.consume(deltaker.id, objectMapper.writeValueAsString(payload))
        io.kotest.assertions.nondeterministic.eventually<Unit>(5.seconds) {
            deltakerRepository.get(deltaker.id).getOrNull().shouldNotBeNull()
        }

        val insertedDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

        insertedDeltaker.deltakerliste.id shouldBe oppdatertDeltaker.deltakerliste.id
        insertedDeltaker.startdato shouldBe oppdatertDeltaker.startdato
        insertedDeltaker.sluttdato shouldBe oppdatertDeltaker.sluttdato
        insertedDeltaker.dagerPerUke shouldBe oppdatertDeltaker.dagerPerUke
        insertedDeltaker.deltakelsesprosent shouldBe oppdatertDeltaker.deltakelsesprosent
        insertedDeltaker.bakgrunnsinformasjon shouldBe null
        insertedDeltaker.deltakelsesinnhold shouldBe null
        insertedDeltaker.status.type shouldBe oppdatertDeltaker.status.type
        insertedDeltaker.status.gyldigFra shouldBeCloseTo statusEndret
        insertedDeltaker.vedtaksinformasjon shouldBe null
        insertedDeltaker.kilde shouldBe Kilde.ARENA

        val importertFraArena = importertFraArenaRepository.getForDeltaker(deltaker.id)
            ?: throw RuntimeException("Fant ikke importert fra arena")
        importertFraArena.importertDato.toLocalDate() shouldBe LocalDate.now()
        importertFraArena.deltakerVedImport.innsoktDato shouldBe payload.registrertDato.toLocalDate()
        importertFraArena.deltakerVedImport.startdato shouldBe oppdatertDeltaker.startdato
        importertFraArena.deltakerVedImport.sluttdato shouldBe oppdatertDeltaker.sluttdato
        importertFraArena.deltakerVedImport.dagerPerUke shouldBe oppdatertDeltaker.dagerPerUke
        importertFraArena.deltakerVedImport.deltakelsesprosent shouldBe oppdatertDeltaker.deltakelsesprosent
        importertFraArena.deltakerVedImport.status.type shouldBe oppdatertDeltaker.status.type
    }

    @Test
    fun `consumeDeltaker - fallback henter gjennomforing fra mulighetsrommet og upserter deltakerliste`() = runBlocking {
        val tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING
        val tiltakstype = lagTiltakstype(tiltakskode = tiltakskode)
        TestRepository.insert(tiltakstype)

        val deltakerlisteId = UUID.randomUUID()
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = lagDeltakerliste(tiltakstype = tiltakstype).copy(id = deltakerlisteId),
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
        )

        // Har ikke gjennomføring i db for å tvinge fallback api kall
        every { unleashToggle.skalLeseArenaDataForTiltakstype(tiltakskode) } returns true
        coEvery { arrangorService.hentArrangor(any<String>()) } returns
            Arrangor(id = UUID.randomUUID(), navn = "Arrangør AS", organisasjonsnummer = "123456789", overordnetArrangorId = null)

        val payloadV2 = DeltakerlistePayload(
            type = DeltakerlistePayload.ENKELTPLASS_V2_TYPE,
            id = deltakerlisteId,
            tiltakstype = DeltakerlistePayload.Tiltakstype(tiltakskode = tiltakskode.name),
            navn = null,
            startDato = null,
            sluttDato = null,
            status = null,
            oppstart = null,
            apentForPamelding = true,
            virksomhetsnummer = null,
            arrangor = DeltakerlistePayload.Arrangor(organisasjonsnummer = "123456789"),
        )

        coEvery { mulighetsrommetApiClient.hentGjennomforingV2(deltakerlisteId) } returns payloadV2

        val payload = toPayload(deltaker)

        consumer.consume(deltaker.id, objectMapper.writeValueAsString(payload))

        eventually<Unit>(5.seconds) {
            // Deltakerliste skal upsertes i fallback
            deltakerlisteRepository.get(deltakerlisteId).isSuccess shouldBe true
            // Deltaker skal finnes i db
            deltakerRepository.get(deltaker.id).isSuccess shouldBe true
        }
    }

    @Test
    fun `consumeDeltaker - toggle off hindrer fallback kall til mulighetsrommet og upsert av gjennomføring`() = runBlocking {
        val tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING
        val tiltakstype = lagTiltakstype(tiltakskode = tiltakskode)
        TestRepository.insert(tiltakstype)

        val deltakerlisteId = UUID.randomUUID()
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = lagDeltakerliste(tiltakstype = tiltakstype).copy(id = deltakerlisteId),
            navBruker = lagNavBruker(navEnhetId = null, navVeilederId = null),
        )

        every { unleashToggle.skalLeseArenaDataForTiltakstype(tiltakskode) } returns false
        val payload = toPayload(deltaker)
        consumer.consume(deltaker.id, objectMapper.writeValueAsString(payload))

        eventually(3.seconds) {
            // No upserts should happen
            deltakerlisteRepository.get(deltakerlisteId).isFailure shouldBe true
            deltakerRepository.get(deltaker.id).isFailure shouldBe true
            importertFraArenaRepository.getForDeltaker(deltaker.id).shouldBeNull()
        }
    }
}
