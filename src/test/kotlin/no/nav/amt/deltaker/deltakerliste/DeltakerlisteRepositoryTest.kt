package no.nav.amt.deltaker.deltakerliste

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate

class DeltakerlisteRepositoryTest {
    companion object {
        lateinit var repository: DeltakerlisteRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
            repository = DeltakerlisteRepository()
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `upsert - ny deltakerliste - inserter`() {
        val arrangor = TestData.lagArrangor()
        val tiltakstype = TestData.lagTiltakstype()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        TestRepository.insert(arrangor)
        TestRepository.insert(tiltakstype)

        repository.upsert(deltakerliste)

        repository.get(deltakerliste.id).getOrNull() shouldBe deltakerliste
    }

    @Test
    fun `upsert - deltakerliste ny sluttdato - oppdaterer`() {
        val arrangor = TestData.lagArrangor()
        val tiltakstype = TestData.lagTiltakstype()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        TestRepository.insert(arrangor)
        TestRepository.insert(tiltakstype)

        repository.upsert(deltakerliste)

        val oppdatertListe = deltakerliste.copy(sluttDato = LocalDate.now())

        repository.upsert(oppdatertListe)

        repository.get(deltakerliste.id).getOrNull() shouldBe oppdatertListe
    }

    @Test
    fun `delete - sletter deltakerliste`() {
        val arrangor = TestData.lagArrangor()
        val tiltakstype = TestData.lagTiltakstype()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        TestRepository.insert(arrangor)
        TestRepository.insert(tiltakstype)

        repository.upsert(deltakerliste)

        repository.delete(deltakerliste.id)

        repository.get(deltakerliste.id).getOrNull() shouldBe null
    }

    @Test
    fun `get - deltakerliste og arrangor finnes - henter deltakerliste`() {
        val arrangor = TestData.lagArrangor()
        val tiltakstype = TestData.lagTiltakstype()
        val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        TestRepository.insert(arrangor)
        TestRepository.insert(tiltakstype)
        repository.upsert(deltakerliste)

        val deltakerlisteMedArrangor = repository.get(deltakerliste.id).getOrThrow()

        deltakerlisteMedArrangor shouldNotBe null
        deltakerlisteMedArrangor.navn shouldBe deltakerliste.navn
        deltakerlisteMedArrangor.arrangor.navn shouldBe arrangor.navn
    }
}
