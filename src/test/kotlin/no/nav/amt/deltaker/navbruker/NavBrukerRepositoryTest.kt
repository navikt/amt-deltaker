package no.nav.amt.deltaker.navbruker

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.DatabaseTestExtension
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NavBrukerRepositoryTest {
    private val navBrukerRepository = NavBrukerRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `upsert - ny bruker - inserter`() {
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val bruker = TestData.lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)

        navBrukerRepository.upsert(bruker).getOrNull() shouldBe bruker
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
        navBrukerRepository.upsert(oppdatertBruker).getOrNull() shouldBe oppdatertBruker
        navBrukerRepository.get(bruker.personId).getOrNull() shouldBe oppdatertBruker
    }
}
