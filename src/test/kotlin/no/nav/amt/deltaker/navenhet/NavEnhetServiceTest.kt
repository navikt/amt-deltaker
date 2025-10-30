package no.nav.amt.deltaker.navenhet

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.mockAzureAdClient
import no.nav.amt.deltaker.utils.mockHttpClient
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class NavEnhetServiceTest {
    companion object {
        lateinit var repository: NavEnhetRepository

        @JvmStatic
        @BeforeAll
        fun setup() {
            @Suppress("UnusedExpression")
            SingletonPostgres16Container
            repository = NavEnhetRepository()
        }
    }

    @Test
    fun `hentEllerOpprettNavEnhet - navenhet finnes i db - henter fra db`() {
        val navEnhet = TestData.lagNavEnhet()
        repository.upsert(navEnhet)
        val navEnhetService = NavEnhetService(repository, mockk())

        runBlocking {
            val navEnhetFraDb = navEnhetService.hentEllerOpprettNavEnhet(navEnhet.enhetsnummer)
            navEnhetFraDb shouldBe navEnhet
        }
    }

    @Test
    fun `hentEllerOpprettNavEnhet - navenhet finnes ikke i db - henter fra personservice og lagrer`() {
        val navEnhetResponse = TestData.lagNavEnhet()
        val httpClient = mockHttpClient(objectMapper.writeValueAsString(TestData.lagNavEnhetDto(navEnhetResponse)))
        val amtPersonServiceClient = AmtPersonServiceClient(
            baseUrl = "http://amt-person-service",
            scope = "scope",
            httpClient = httpClient,
            azureAdTokenClient = mockAzureAdClient(),
        )
        val navEnhetService = NavEnhetService(repository, amtPersonServiceClient)

        runBlocking {
            val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(navEnhetResponse.enhetsnummer)

            navEnhet shouldBe navEnhetResponse
            repository.get(navEnhetResponse.enhetsnummer) shouldBe navEnhetResponse
        }
    }
}
