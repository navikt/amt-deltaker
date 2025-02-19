package no.nav.amt.deltaker.deltakerliste.kafka

import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate

class DeltakerlisteConsumerTest {
    companion object {
        lateinit var deltakerlisteRepository: DeltakerlisteRepository
        lateinit var tiltakstypeRepository: TiltakstypeRepository
        lateinit var deltakerService: DeltakerService

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgres16Container
            deltakerlisteRepository = DeltakerlisteRepository()
            tiltakstypeRepository = TiltakstypeRepository()
            deltakerService = mockk<DeltakerService>(relaxed = true)
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
        clearMocks(deltakerService)
    }

    @Test
    fun `consumeDeltakerliste - ny liste og arrangor - lagrer deltakerliste`() {
        val arrangor = TestData.lagArrangor()
        val tiltakstype = TestData.lagTiltakstype()
        TestRepository.insert(tiltakstype)
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient(arrangor))
        val consumer =
            DeltakerlisteConsumer(deltakerlisteRepository, tiltakstypeRepository, arrangorService, deltakerService)

        runBlocking {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(TestData.lagDeltakerlisteDto(arrangor, deltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - ny sluttdato - oppdaterer deltakerliste`() {
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        TestRepository.insert(deltakerliste)

        val consumer =
            DeltakerlisteConsumer(deltakerlisteRepository, tiltakstypeRepository, arrangorService, deltakerService)

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now())

        runBlocking {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(TestData.lagDeltakerlisteDto(arrangor, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - avbrutt - oppdaterer deltakerliste og avslutter deltakere`() {
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        TestRepository.insert(deltakerliste)

        val consumer =
            DeltakerlisteConsumer(deltakerlisteRepository, tiltakstypeRepository, arrangorService, deltakerService)

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now(), status = Deltakerliste.Status.AVBRUTT)

        runBlocking {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(TestData.lagDeltakerlisteDto(arrangor, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }

        coVerify { deltakerService.avsluttDeltakelserPaaDeltakerliste(deltakerliste.id) }
    }

    @Test
    fun `consumeDeltakerliste - tombstone - sletter deltakerliste`() {
        val deltakerliste = TestData.lagDeltakerliste()
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())

        TestRepository.insert(deltakerliste)

        val consumer =
            DeltakerlisteConsumer(deltakerlisteRepository, tiltakstypeRepository, arrangorService, deltakerService)

        runBlocking {
            consumer.consume(deltakerliste.id, null)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe null
        }

        coVerify(exactly = 0) { deltakerService.avsluttDeltakelserPaaDeltakerliste(any()) }
    }

    @Test
    fun `consumeDeltakerliste - redusert sluttdato - oppdaterer deltakerliste og oppdaterer sluttdato på deltakere`() {
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        TestRepository.insert(deltakerliste)

        val consumer =
            DeltakerlisteConsumer(deltakerlisteRepository, tiltakstypeRepository, arrangorService, deltakerService)

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now())

        runBlocking {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(TestData.lagDeltakerlisteDto(arrangor, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }

        coVerify { deltakerService.avgrensSluttdatoerTil(oppdatertDeltakerliste) }
    }
}
