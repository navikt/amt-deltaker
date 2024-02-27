package no.nav.amt.deltaker.deltaker.db

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.shouldBeCloseTo
import org.junit.BeforeClass
import org.junit.Test

class DeltakerEndringRepositoryTest {
    companion object {
        lateinit var repository: DeltakerEndringRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
            repository = DeltakerEndringRepository()
        }
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
        val deltaker = TestData.lagDeltaker(
            sistEndretAv = navAnsatt1.id,
            sistEndretAvEnhet = navEnhet1.id,
        )
        val deltakerEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt1.id,
            endretAvEnhet = navEnhet1.id,
        )
        val deltakerEndring2 = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endringstype = DeltakerEndring.Endringstype.INNHOLD,
            endring = DeltakerEndring.Endring.EndreInnhold(listOf(Innhold("tekst", "type", true, null))),
            endretAv = navAnsatt2.id,
            endretAvEnhet = navEnhet2.id,
        )
        TestRepository.insert(deltaker, navAnsatt1, navEnhet1)

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

    private fun sammenlignDeltakerEndring(a: DeltakerEndring, b: DeltakerEndring) {
        a.id shouldBe b.id
        a.deltakerId shouldBe b.deltakerId
        a.endringstype shouldBe b.endringstype
        a.endring shouldBe b.endring
        a.endretAv shouldBe b.endretAv
        a.endretAvEnhet shouldBe b.endretAvEnhet
        a.endret shouldBeCloseTo b.endret
    }
}
