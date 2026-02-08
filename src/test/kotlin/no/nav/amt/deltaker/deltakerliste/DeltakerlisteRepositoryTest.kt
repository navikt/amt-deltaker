package no.nav.amt.deltaker.deltakerliste

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData.lagArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerlisteRepositoryTest {
    private val deltakerlisteRepository = DeltakerlisteRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class Upsert {
        @Test
        fun `ny minimal deltakerliste - inserter`() {
            val arrangor = lagArrangor()
            TestRepository.insert(arrangor)

            val tiltakstype = lagTiltakstype()
            TestRepository.insert(tiltakstype)

            val deltakerliste = lagDeltakerliste(
                arrangor = arrangor,
                tiltakstype = tiltakstype,
            ).copy(
                status = null,
                startDato = null,
                sluttDato = null,
                oppstart = null,
            )

            deltakerlisteRepository.upsert(deltakerliste)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe deltakerliste
        }

        @Test
        fun `ny deltakerliste - inserter`() {
            val arrangor = lagArrangor()
            val tiltakstype = lagTiltakstype()
            val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
            TestRepository.insert(arrangor)
            TestRepository.insert(tiltakstype)

            deltakerlisteRepository.upsert(deltakerliste)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe deltakerliste
        }

        @Test
        fun `deltakerliste ny sluttdato - oppdaterer`() {
            val arrangor = lagArrangor()
            val tiltakstype = lagTiltakstype()
            val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
            TestRepository.insert(arrangor)
            TestRepository.insert(tiltakstype)

            deltakerlisteRepository.upsert(deltakerliste)

            val oppdatertListe = deltakerliste.copy(sluttDato = LocalDate.now())

            deltakerlisteRepository.upsert(oppdatertListe)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe oppdatertListe
        }
    }

    @Test
    fun `delete - sletter deltakerliste`() {
        val arrangor = lagArrangor()
        val tiltakstype = lagTiltakstype()
        val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        TestRepository.insert(arrangor)
        TestRepository.insert(tiltakstype)

        deltakerlisteRepository.upsert(deltakerliste)

        deltakerlisteRepository.delete(deltakerliste.id)

        deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe null
    }

    @Test
    fun `get - deltakerliste og arrangor finnes - henter deltakerliste`() {
        val arrangor = lagArrangor()
        val tiltakstype = lagTiltakstype()
        val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        TestRepository.insert(arrangor)
        TestRepository.insert(tiltakstype)
        deltakerlisteRepository.upsert(deltakerliste)

        val deltakerlisteMedArrangor = deltakerlisteRepository.get(deltakerliste.id).getOrThrow()

        deltakerlisteMedArrangor.navn shouldBe deltakerliste.navn
        deltakerlisteMedArrangor.arrangor.navn shouldBe arrangor.navn
    }
}
