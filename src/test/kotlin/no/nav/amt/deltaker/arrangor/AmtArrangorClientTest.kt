package no.nav.amt.deltaker.arrangor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.utils.createMockHttpClient
import no.nav.amt.deltaker.utils.data.TestData.lagArrangor
import no.nav.amt.deltaker.utils.data.TestData.randomOrgnr
import no.nav.amt.deltaker.utils.mockAzureAdClient
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class AmtArrangorClientTest {
    @Nested
    inner class HentArrangorByOrgnummer {
        @Test
        fun `skal kaste feil nar respons med feilkode`() {
            val amtArrangorClient = createArrangorClient(
                "$ARRANGOR_BASE_URL/api/service/arrangor/organisasjonsnummer/$orgnrInTest",
                HttpStatusCode.BadRequest,
            )

            val thrown = runBlocking {
                shouldThrow<IllegalStateException> {
                    amtArrangorClient.hentArrangor(orgnrInTest)
                }
            }

            thrown.message shouldStartWith "Kunne ikke hente arrangør med orgnummer $orgnrInTest"
        }

        @Test
        fun `skal parse response riktig og returnere arrangor`(): Unit = runBlocking {
            val overordnetArrangor = lagArrangor()
            val arrangor = lagArrangor(overordnetArrangorId = overordnetArrangor.id)

            val expectedArrangor = ArrangorDto(arrangor.id, arrangor.navn, arrangor.organisasjonsnummer, overordnetArrangor)

            val amtArrangorClient = createArrangorClient(
                "$ARRANGOR_BASE_URL/api/service/arrangor/organisasjonsnummer/${expectedArrangor.organisasjonsnummer}",
                responseBody = expectedArrangor,
            )

            val actualArrangor = amtArrangorClient.hentArrangor(expectedArrangor.organisasjonsnummer)
            actualArrangor shouldBe expectedArrangor
        }
    }

    @Nested
    inner class HentArrangorById {
        @Test
        fun `skal kaste feil nar respons med feilkode`() {
            val amtArrangorClient = createArrangorClient(
                "$ARRANGOR_BASE_URL/api/service/arrangor/$arrangorIdInTest",
                HttpStatusCode.BadRequest,
            )

            val thrown = runBlocking {
                shouldThrow<IllegalStateException> {
                    amtArrangorClient.hentArrangor(arrangorIdInTest)
                }
            }

            thrown.message shouldStartWith "Kunne ikke hente arrangør med id $arrangorIdInTest"
        }

        @Test
        fun `skal parse response riktig og returnere arrangor`(): Unit = runBlocking {
            val overordnetArrangor = lagArrangor()
            val arrangor = lagArrangor(overordnetArrangorId = overordnetArrangor.id)

            val expectedArrangor = ArrangorDto(arrangor.id, arrangor.navn, arrangor.organisasjonsnummer, overordnetArrangor)

            val amtArrangorClient = createArrangorClient(
                "$ARRANGOR_BASE_URL/api/service/arrangor/${expectedArrangor.id}",
                responseBody = expectedArrangor,
            )

            val actualArrangor = amtArrangorClient.hentArrangor(arrangor.id)
            actualArrangor shouldBe expectedArrangor
        }
    }

    companion object {
        private fun createArrangorClient(
            expectedUrl: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            responseBody: ArrangorDto? = null,
        ) = AmtArrangorClient(
            baseUrl = ARRANGOR_BASE_URL,
            scope = "scope",
            httpClient = createMockHttpClient(expectedUrl, responseBody, statusCode),
            azureAdTokenClient = mockAzureAdClient(),
        )

        private val orgnrInTest = randomOrgnr()
        private val arrangorIdInTest: UUID = UUID.randomUUID()
        private const val ARRANGOR_BASE_URL = "http://amt-arrangor"
    }
}
