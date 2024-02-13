package no.nav.amt.deltaker.auth

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.application.plugins.applicationConfig
import org.junit.Test

class AzureAdTokenClientTest {
    @Test
    fun `getMachineToMachineToken - skal parse Azure AD token response riktig og lage token`(): Unit = runBlocking {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                        "token_type":"Bearer",
                        "access_token":"XYZ",
                        "expires_in": 3599
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                jackson { applicationConfig() }
            }
        }

        val azureAdTokenClient = AzureAdTokenClient(
            azureAdTokenUrl = "https://fake-url.com/token",
            clientId = "fake-client-id",
            clientSecret = "fake-client-secret",
            httpClient = httpClient,
        )

        val token = azureAdTokenClient.getMachineToMachineToken("fake-scope")

        token shouldBe "Bearer XYZ"
    }

    @Test
    fun `getMachineToMachineToken - skal skal bruke cachet token etter forste kall`(): Unit = runBlocking {
        var antallGangerKallt = 0
        val mockEngine = MockEngine {
            antallGangerKallt++

            respond(
                content = ByteReadChannel(
                    """
                    {
                        "token_type":"Bearer",
                        "access_token":"XYZ",
                        "expires_in": 3599
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                jackson { applicationConfig() }
            }
        }

        val azureAdTokenClient = AzureAdTokenClient(
            azureAdTokenUrl = "https://fake-url.com/token",
            clientId = "fake-client-id",
            clientSecret = "fake-client-secret",
            httpClient = httpClient,
        )

        val token1 = azureAdTokenClient.getMachineToMachineToken("fake-scope")
        val token2 = azureAdTokenClient.getMachineToMachineToken("fake-scope")

        token1 shouldBe "Bearer XYZ"
        token2 shouldBe "Bearer XYZ"

        antallGangerKallt shouldBe 1
    }
}
