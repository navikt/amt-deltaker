package no.nav.amt.deltaker.navansatt

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockPersonServiceClient
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NavAnsattServiceTest {
    private val navAnsattRepository = NavAnsattRepository()
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockPersonServiceClient())
    private val navAnsattService = NavAnsattService(navAnsattRepository, mockPersonServiceClient(), navEnhetService)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `hentEllerOpprettNavAnsatt - navansatt finnes i db - henter fra db`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)

        runTest {
            val navAnsattFraDb = navAnsattService.hentEllerOpprettNavAnsatt(navAnsatt.navIdent)
            navAnsattFraDb shouldBe navAnsatt
        }
    }

    @Test
    fun `hentEllerOpprettNavAnsatt - navansatt finnes ikke i db - henter fra personservice og lagrer`() {
        val navAnsattResponse = TestData.lagNavAnsatt()

        MockResponseHandler.addNavAnsattPostResponse(navAnsattResponse)
        MockResponseHandler.addNavEnhetGetResponse(TestData.lagNavEnhet(navAnsattResponse.navEnhetId!!))

        runTest {
            val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(navAnsattResponse.navIdent)

            navAnsatt shouldBe navAnsattResponse
            navAnsattRepository.get(navAnsattResponse.id) shouldBe navAnsattResponse
        }
    }

    @Test
    fun `oppdaterNavAnsatt - navansatt finnes - blir oppdatert`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val oppdatertNavAnsatt = navAnsatt.copy(navn = "Nytt Navn")

        runTest {
            navAnsattService.oppdaterNavAnsatt(oppdatertNavAnsatt)
        }

        navAnsattRepository.get(navAnsatt.id) shouldBe oppdatertNavAnsatt
    }
}
