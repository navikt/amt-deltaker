package no.nav.amt.deltaker.deltakerliste.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka.TiltakstypeConsumer
import no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka.Tiltakstypestatus
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import org.junit.BeforeClass
import org.junit.Test

class TiltakstypeConsumerTest {
    companion object {
        lateinit var repository: TiltakstypeRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
            repository = TiltakstypeRepository()
        }
    }

    @Test
    fun `consumeTiltakstype - ny, aktiv tiltakstype - lagrer tiltakstype`() {
        val tiltakstype = TestData.lagTiltakstype()
        val tiltakstypeDto = TiltakstypeDto(
            id = tiltakstype.id,
            navn = tiltakstype.navn,
            arenaKode = tiltakstype.type.name,
            status = Tiltakstypestatus.Aktiv,
            deltakerRegistreringInnhold = tiltakstype.innhold,
        )
        val consumer = TiltakstypeConsumer(repository)

        runBlocking {
            consumer.consume(
                tiltakstype.id,
                objectMapper.writeValueAsString(tiltakstypeDto),
            )

            repository.get(tiltakstype.type).getOrThrow() shouldBe tiltakstype
        }
    }
}
