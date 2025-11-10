package no.nav.amt.deltaker.deltakerliste.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlistePayload.Arrangor
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlistePayload.Companion.ENKELTPLASS_V2_TYPE
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlistePayload.Companion.GRUPPE_V2_TYPE
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.data.TestData.lagArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlistePayload
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakerlisteConsumerTest {
    companion object {
        private val deltakerlisteRepository = DeltakerlisteRepository()
        private val tiltakstypeRepository = TiltakstypeRepository()
        private val deltakerService: DeltakerService = mockk(relaxed = true)
        private val unleashToggle: UnleashToggle = mockk()

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
        every { unleashToggle.skipProsesseringAvGjennomforing(any<String>()) } returns false
    }

    @Test
    fun `unleashToggle er ikke enabled for tiltakstype - lagrer ikke deltakerliste`() {
        every { unleashToggle.skipProsesseringAvGjennomforing(any<String>()) } returns true

        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING)
        TestRepository.insert(tiltakstype)

        val arrangor = lagArrangor()
        val expectedDeltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient(arrangor))

        val consumer =
            DeltakerlisteConsumer(
                deltakerlisteRepository = deltakerlisteRepository,
                tiltakstypeRepository = tiltakstypeRepository,
                arrangorService = arrangorService,
                deltakerService = deltakerService,
                unleashToggle = unleashToggle,
            )

        val deltakerlistePayload = lagDeltakerlistePayload(arrangor, expectedDeltakerliste).copy(
            type = GRUPPE_V2_TYPE,
            arrangor = Arrangor(arrangor.organisasjonsnummer),
        )

        runBlocking {
            consumer.consume(
                deltakerlistePayload.id,
                objectMapper.writeValueAsString(deltakerlistePayload),
            )

            val thrown = shouldThrow<NoSuchElementException> {
                deltakerlisteRepository.get(expectedDeltakerliste.id).getOrThrow()
            }

            thrown.message shouldBe "Fant ikke deltakerliste med id ${expectedDeltakerliste.id}"
        }
    }

    @Test
    fun `ny liste v2 gruppe - lagrer deltakerliste`() {
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING)
        TestRepository.insert(tiltakstype)

        val arrangor = lagArrangor()
        val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient(arrangor))

        val consumer =
            DeltakerlisteConsumer(
                deltakerlisteRepository = deltakerlisteRepository,
                tiltakstypeRepository = tiltakstypeRepository,
                arrangorService = arrangorService,
                deltakerService = deltakerService,
                unleashToggle = unleashToggle,
            )

        val deltakerlistePayload = lagDeltakerlistePayload(arrangor, deltakerliste).copy(
            type = GRUPPE_V2_TYPE,
            arrangor = Arrangor(arrangor.organisasjonsnummer),
        )

        runBlocking {
            consumer.consume(
                deltakerlistePayload.id,
                objectMapper.writeValueAsString(deltakerlistePayload),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `ny liste v2 enkeltplass - lagrer deltakerliste`() {
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING)
        TestRepository.insert(tiltakstype)

        val arrangor = lagArrangor()
        val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient(arrangor))

        val consumer =
            DeltakerlisteConsumer(
                deltakerlisteRepository,
                tiltakstypeRepository,
                arrangorService,
                deltakerService,
                unleashToggle = unleashToggle,
            )

        val deltakerlistePayload = lagDeltakerlistePayload(arrangor, deltakerliste).copy(
            type = ENKELTPLASS_V2_TYPE,
            navn = null,
            startDato = null,
            sluttDato = null,
            status = null,
            oppstart = null,
            arrangor = Arrangor(arrangor.organisasjonsnummer),
        )

        runBlocking {
            consumer.consume(
                deltakerlistePayload.id,
                objectMapper.writeValueAsString(deltakerlistePayload),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste.copy(
                navn = "Test tiltak ENKFAGYRKE",
                status = null,
                startDato = null,
                sluttDato = null,
                oppstart = null,
            )
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - ny liste og arrangor - lagrer deltakerliste`() {
        val tiltakstype = lagTiltakstype()
        val arrangor = lagArrangor()
        TestRepository.insert(tiltakstype)
        val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient(arrangor))

        val consumer =
            DeltakerlisteConsumer(
                deltakerlisteRepository,
                tiltakstypeRepository,
                arrangorService,
                deltakerService,
                unleashToggle = unleashToggle,
            )

        runBlocking {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangor, deltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - ny sluttdato - oppdaterer deltakerliste`() {
        val arrangor = lagArrangor()
        val deltakerliste = lagDeltakerliste(arrangor = arrangor)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        TestRepository.insert(deltakerliste)

        val consumer =
            DeltakerlisteConsumer(
                deltakerlisteRepository,
                tiltakstypeRepository,
                arrangorService,
                deltakerService,
                unleashToggle = unleashToggle,
            )

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now())

        runBlocking {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangor, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - avbrutt - oppdaterer deltakerliste og avslutter deltakere`() {
        val arrangor = lagArrangor()
        val deltakerliste = lagDeltakerliste(arrangor = arrangor)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        TestRepository.insert(deltakerliste)

        val consumer =
            DeltakerlisteConsumer(
                deltakerlisteRepository,
                tiltakstypeRepository,
                arrangorService,
                deltakerService,
                unleashToggle = unleashToggle,
            )

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now(), status = Deltakerliste.Status.AVBRUTT)

        runBlocking {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangor, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }

        coVerify { deltakerService.avsluttDeltakelserPaaDeltakerliste(oppdatertDeltakerliste) }
    }

    @Test
    fun `consumeDeltakerliste - tombstone - sletter deltakerliste`() {
        val deltakerliste = lagDeltakerliste()
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())

        TestRepository.insert(deltakerliste)

        val consumer =
            DeltakerlisteConsumer(
                deltakerlisteRepository,
                tiltakstypeRepository,
                arrangorService,
                deltakerService,
                unleashToggle = unleashToggle,
            )

        runBlocking {
            consumer.consume(deltakerliste.id, null)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe null
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - redusert sluttdato - oppdaterer deltakerliste og oppdaterer sluttdato pa deltakere`() {
        val arrangor = lagArrangor()
        val deltakerliste = lagDeltakerliste(arrangor = arrangor)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        TestRepository.insert(deltakerliste)

        val consumer =
            DeltakerlisteConsumer(
                deltakerlisteRepository,
                tiltakstypeRepository,
                arrangorService,
                deltakerService,
                unleashToggle = unleashToggle,
            )

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now())

        runBlocking {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(lagDeltakerlistePayload(arrangor, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }

        coVerify { deltakerService.avgrensSluttdatoerTil(oppdatertDeltakerliste) }
    }
}
