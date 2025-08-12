package no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.toV2
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TiltakstypeConsumerTest {
    companion object {
        lateinit var repository: TiltakstypeRepository

        @JvmStatic
        @BeforeAll
        fun setup() {
            SingletonPostgres16Container
            repository = TiltakstypeRepository()
        }
    }

    @BeforeEach
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
            innsatsgrupper = tiltakstype.innsatsgrupper.map { it.toV2() }.toSet(),
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
