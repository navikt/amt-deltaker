package no.nav.amt.deltaker.deltaker.kafka

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.amtperson.AmtPersonServiceClient
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.model.Kilde
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestData.toDeltakerV2
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.awaitility.Awaitility
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
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
            consumer = DeltakerConsumer(
                deltakerRepository,
                deltakerlisteRepository,
                navBrukerService,
                importertFraArenaRepository,
                unleashToggle,
            )
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
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
        val deltaker = TestData.lagDeltaker(
            kilde = Kilde.ARENA,
            deltakerliste = deltakerliste,
            innhold = null,
        )

        every { unleashToggle.erKometMasterForTiltakstype(Tiltakstype.ArenaKode.ARBFORB) } returns false

        TestRepository.insert(deltaker.navBruker)

        val deltakerV2Dto = deltaker.toDeltakerV2()
        consumer.consume(deltaker.id, objectMapper.writeValueAsString(deltakerV2Dto))

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
            deltakerRepository.get(deltaker.id).getOrNull() != null
        }
        val insertedDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

        val expectedDeltaker = deltaker.copy(
            bakgrunnsinformasjon = null,
            status = deltaker.status.copy(opprettet = insertedDeltaker.status.opprettet),
            sistEndret = insertedDeltaker.sistEndret,
        )

        insertedDeltaker shouldBe expectedDeltaker
        importertFraArenaRepository.getForDeltaker(deltaker.id) shouldNotBe null
    }
}
