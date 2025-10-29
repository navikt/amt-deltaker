package no.nav.amt.deltaker.external.api

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.every
import no.nav.amt.deltaker.external.data.HentDeltakelserRequest
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.generateJWT
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DeltakelserTiltakspengerApiTest : RouteTestBase() {
    @BeforeEach
    fun setup() = unleashClient.enableAll()

    @Test
    fun `autentisering - ulike scenarioer`() {
        withTestApplicationContext { client ->
            client.postDeltakelser(token = null).status shouldBe HttpStatusCode.Unauthorized

            client.postDeltakelser(token = "ugyldig-token").status shouldBe HttpStatusCode.Unauthorized

            val feilToken = generateJWT(consumerClientId = "feil-app", audience = "amt-deltaker")
            client.postDeltakelser(token = feilToken).status shouldBe HttpStatusCode.Unauthorized

            val utenTilgangToken = generateJWT(consumerClientId = "amt-deltaker-bff", audience = "amt-deltaker")
            client.postDeltakelser(token = utenTilgangToken).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post deltaker personident - standard scenario`() {
        val navBrukerInTest = lagNavBruker(personident = PERSON_IDENT_IN_TEST)
        val deltakerInTest = lagDeltaker(navBruker = navBrukerInTest).copy(opprettet = LocalDateTime.now())
        val expectedResponse = listOf(deltakerInTest.toResponse())

        every { deltakerRepository.getFlereForPerson(any()) } returns listOf(deltakerInTest)

        withTestApplicationContext { client ->
            val response = client.postDeltakelser(PERSON_IDENT_IN_TEST)

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe objectMapper.writeValueAsString(expectedResponse)
        }
    }

    companion object {
        private const val PERSON_IDENT_IN_TEST = "~personIdent~"

        private val externalSystemToken = generateJWT(
            consumerClientId = "tiltakspenger-tiltak",
            audience = "amt-deltaker",
        )

        private suspend fun HttpClient.postDeltakelser(
            personIdent: String = PERSON_IDENT_IN_TEST,
            token: String? = externalSystemToken,
        ): HttpResponse = post("/external/deltakelser") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            token?.let { bearerAuth(it) }
            setBody(HentDeltakelserRequest(personIdent))
        }
    }
}
