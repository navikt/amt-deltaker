package no.nav.amt.deltaker.navansatt

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.amtperson.AmtPersonServiceClient
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.mockAzureAdClient
import no.nav.amt.deltaker.utils.mockHttpClient
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.BeforeClass
import org.junit.Test

class NavAnsattServiceTest {
    companion object {
        lateinit var repository: NavAnsattRepository
        lateinit var service: NavAnsattService

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgres16Container
            repository = NavAnsattRepository()
            service = NavAnsattService(repository, mockk())
        }
    }

    @Test
    fun `hentEllerOpprettNavAnsatt - navansatt finnes i db - henter fra db`() {
        val navAnsatt = TestData.lagNavAnsatt()
        repository.upsert(navAnsatt)

        runBlocking {
            val navAnsattFraDb = service.hentEllerOpprettNavAnsatt(navAnsatt.navIdent)
            navAnsattFraDb shouldBe navAnsatt
        }
    }

    @Test
    fun `hentEllerOpprettNavAnsatt - navansatt finnes ikke i db - henter fra personservice og lagrer`() {
        val navAnsattResponse = TestData.lagNavAnsatt()
        val httpClient = mockHttpClient(objectMapper.writeValueAsString(navAnsattResponse))
        val amtPersonServiceClient = AmtPersonServiceClient(
            baseUrl = "http://amt-person-service",
            scope = "scope",
            httpClient = httpClient,
            azureAdTokenClient = mockAzureAdClient(),
        )
        val navAnsattService = NavAnsattService(repository, amtPersonServiceClient)

        runBlocking {
            val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(navAnsattResponse.navIdent)

            navAnsatt shouldBe navAnsattResponse
            repository.get(navAnsattResponse.id) shouldBe navAnsattResponse
        }
    }

    @Test
    fun `oppdaterNavAnsatt - navansatt finnes - blir oppdatert`() {
        val navAnsatt = TestData.lagNavAnsatt()
        repository.upsert(navAnsatt)
        val oppdatertNavAnsatt = navAnsatt.copy(navn = "Nytt Navn")

        service.oppdaterNavAnsatt(oppdatertNavAnsatt)

        repository.get(navAnsatt.id) shouldBe oppdatertNavAnsatt
    }

    @Test
    fun `slettNavAnsatt - navansatt blir slettet`() {
        val navAnsatt = TestData.lagNavAnsatt()
        repository.upsert(navAnsatt)

        service.slettNavAnsatt(navAnsatt.id)

        repository.get(navAnsatt.id) shouldBe null
    }
}
