package no.nav.amt.deltaker.deltaker.db

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class DeltakerEndringRepositoryTest {
    companion object {
        lateinit var repository: DeltakerEndringRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgres16Container
            repository = DeltakerEndringRepository()
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `getForDeltaker - to endringer for deltaker, navansatt og enhet finnes - returnerer endring med navn for ansatt og enhet`() {
        val navAnsatt1 = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt1)
        val navAnsatt2 = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt2)
        val navEnhet1 = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet1)
        val navEnhet2 = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet2)
        val deltaker = TestData.lagDeltaker()
        val deltakerEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt1.id,
            endretAvEnhet = navEnhet1.id,
        )
        val deltakerEndring2 = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endring = DeltakerEndring.Endring.EndreInnhold("ledetekst", listOf(Innhold("tekst", "type", true, null))),
            endretAv = navAnsatt2.id,
            endretAvEnhet = navEnhet2.id,
        )
        TestRepository.insert(deltaker)

        repository.upsert(deltakerEndring)
        repository.upsert(deltakerEndring2)

        val endringFraDb = repository.getForDeltaker(deltaker.id)

        endringFraDb.size shouldBe 2
        sammenlignDeltakerEndring(
            endringFraDb.find { it.id == deltakerEndring.id }!!,
            deltakerEndring.copy(endretAv = navAnsatt1.id, endretAvEnhet = navEnhet1.id),
        )
        sammenlignDeltakerEndring(
            endringFraDb.find { it.id == deltakerEndring2.id }!!,
            deltakerEndring2.copy(endretAv = navAnsatt2.id, endretAvEnhet = navEnhet2.id),
        )
    }

    @Test
    fun `getForDeltaker - deltaker er feilregistrert - returnerer tom liste`() {
        val navAnsatt1 = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt1)
        val navAnsatt2 = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt2)
        val navEnhet1 = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet1)
        val navEnhet2 = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet2)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.FEILREGISTRERT),
        )
        val deltakerEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt1.id,
            endretAvEnhet = navEnhet1.id,
        )
        val deltakerEndring2 = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endring = DeltakerEndring.Endring.EndreInnhold("ledetekst", listOf(Innhold("tekst", "type", true, null))),
            endretAv = navAnsatt2.id,
            endretAvEnhet = navEnhet2.id,
        )
        TestRepository.insert(deltaker)

        repository.upsert(deltakerEndring)
        repository.upsert(deltakerEndring2)

        val endringFraDb = repository.getForDeltaker(deltaker.id)

        endringFraDb.size shouldBe 0
    }

    private fun sammenlignDeltakerEndring(a: DeltakerEndring, b: DeltakerEndring) {
        a.id shouldBe b.id
        a.deltakerId shouldBe b.deltakerId
        a.endring shouldBe b.endring
        a.endretAv shouldBe b.endretAv
        a.endretAvEnhet shouldBe b.endretAvEnhet
        a.endret shouldBeCloseTo b.endret
    }
}
