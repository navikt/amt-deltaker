package no.nav.amt.deltaker.deltakerliste.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate

class DeltakerlisteConsumerTest {
    companion object {
        lateinit var deltakerlisteRepository: DeltakerlisteRepository
        lateinit var tiltakstypeRepository: TiltakstypeRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
            deltakerlisteRepository = DeltakerlisteRepository()
            tiltakstypeRepository = TiltakstypeRepository()
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `consumeDeltakerliste - ny liste og arrangor - lagrer deltakerliste`() {
        val arrangor = TestData.lagArrangor()
        val tiltakstype = TestData.lagTiltakstype()
        TestRepository.insert(tiltakstype)
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient(arrangor))
        val consumer = DeltakerlisteConsumer(deltakerlisteRepository, tiltakstypeRepository, arrangorService)

        runBlocking {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(TestData.lagDeltakerlisteDto(arrangor, deltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe deltakerliste
        }
    }

    @Test
    fun `consumeDeltakerliste - ny sluttdato - oppdaterer deltakerliste`() {
        val arrangor = TestData.lagArrangor()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
        TestRepository.insert(deltakerliste)

        val consumer = DeltakerlisteConsumer(deltakerlisteRepository, tiltakstypeRepository, arrangorService)

        val oppdatertDeltakerliste = deltakerliste.copy(sluttDato = LocalDate.now())

        runBlocking {
            consumer.consume(
                deltakerliste.id,
                objectMapper.writeValueAsString(TestData.lagDeltakerlisteDto(arrangor, oppdatertDeltakerliste)),
            )

            deltakerlisteRepository.get(deltakerliste.id).getOrThrow() shouldBe oppdatertDeltakerliste
        }
    }

    @Test
    fun `consumeDeltakerliste - tombstone - sletter deltakerliste`() {
        val deltakerliste = TestData.lagDeltakerliste()
        val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())

        TestRepository.insert(deltakerliste)

        val consumer = DeltakerlisteConsumer(deltakerlisteRepository, tiltakstypeRepository, arrangorService)

        runBlocking {
            consumer.consume(deltakerliste.id, null)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe null
        }
    }
}
