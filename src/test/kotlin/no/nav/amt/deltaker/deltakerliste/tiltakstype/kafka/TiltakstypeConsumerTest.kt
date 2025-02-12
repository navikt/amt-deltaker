package no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class TiltakstypeConsumerTest {
    companion object {
        lateinit var repository: TiltakstypeRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgres16Container
            repository = TiltakstypeRepository()
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `consumeTiltakstype - ny, aktiv tiltakstype - lagrer tiltakstype`() {
        val tiltakstype = TestData.lagTiltakstype()
        val tiltakstypeDto = TiltakstypeDto(
            id = tiltakstype.id,
            navn = tiltakstype.navn,
            tiltakskode = tiltakstype.tiltakskode,
            arenaKode = tiltakstype.arenaKode.name,
            innsatsgrupper = tiltakstype.innsatsgrupper,
            deltakerRegistreringInnhold = tiltakstype.innhold,
        )
        val consumer = TiltakstypeConsumer(repository)

        runBlocking {
            consumer.consume(
                tiltakstype.id,
                objectMapper.writeValueAsString(tiltakstypeDto),
            )

            repository.get(tiltakstype.arenaKode).getOrThrow() shouldBe tiltakstype
        }
    }
}
