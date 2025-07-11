package no.nav.amt.deltaker.navbruker

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class NavBrukerRepositoryTest {
    companion object {
        lateinit var repository: NavBrukerRepository

        @JvmStatic
        @BeforeAll
        fun setup() {
            SingletonPostgres16Container
            repository = NavBrukerRepository()
        }
    }

    @Test
    fun `upsert - ny bruker - inserter`() {
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val bruker = TestData.lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)

        repository.upsert(bruker).getOrNull() shouldBe bruker
    }

    @Test
    fun `upsert - oppdatert bruker - oppdaterer`() {
        val bruker = TestData.lagNavBruker()
        TestRepository.insert(bruker)

        val oppdatertBruker = bruker.copy(
            personident = TestData.randomIdent(),
            fornavn = "Nytt Fornavn",
            mellomnavn = null,
            etternavn = "Nytt Etternavn",
        )
        repository.upsert(oppdatertBruker).getOrNull() shouldBe oppdatertBruker
        repository.get(bruker.personId).getOrNull() shouldBe oppdatertBruker
    }
}
