package no.nav.amt.deltaker.navansatt

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockPersonServiceClient
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class NavAnsattServiceTest {
    companion object {
        private val repository: NavAnsattRepository = NavAnsattRepository()
        private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockPersonServiceClient())
        private val service: NavAnsattService = NavAnsattService(repository, mockPersonServiceClient(), navEnhetService)

        @JvmStatic
        @BeforeAll
        fun setup() {
            @Suppress("UnusedExpression")
            SingletonPostgres16Container
        }
    }

    @Test
    fun `hentEllerOpprettNavAnsatt - navansatt finnes i db - henter fra db`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)

        runBlocking {
            val navAnsattFraDb = service.hentEllerOpprettNavAnsatt(navAnsatt.navIdent)
            navAnsattFraDb shouldBe navAnsatt
        }
    }

    @Test
    fun `hentEllerOpprettNavAnsatt - navansatt finnes ikke i db - henter fra personservice og lagrer`() {
        val navAnsattResponse = TestData.lagNavAnsatt()

        MockResponseHandler.addNavAnsattPostResponse(navAnsattResponse)
        MockResponseHandler.addNavEnhetGetResponse(TestData.lagNavEnhet(navAnsattResponse.navEnhetId!!))

        runBlocking {
            val navAnsatt = service.hentEllerOpprettNavAnsatt(navAnsattResponse.navIdent)

            navAnsatt shouldBe navAnsattResponse
            repository.get(navAnsattResponse.id) shouldBe navAnsattResponse
        }
    }

    @Test
    fun `oppdaterNavAnsatt - navansatt finnes - blir oppdatert`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val oppdatertNavAnsatt = navAnsatt.copy(navn = "Nytt Navn")

        runBlocking {
            service.oppdaterNavAnsatt(oppdatertNavAnsatt)
        }

        repository.get(navAnsatt.id) shouldBe oppdatertNavAnsatt
    }
}
