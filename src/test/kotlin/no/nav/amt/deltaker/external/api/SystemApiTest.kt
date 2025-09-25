package no.nav.amt.deltaker.external.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import no.nav.amt.deltaker.external.data.DeltakerPersonaliaResponse
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.generateJWT
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SystemApiTest : RouteTestBase() {
    @BeforeEach
    fun setup() = unleashClient.enableAll()

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() {
        val deltakerIds = listOf(UUID.randomUUID())

        withTestApplicationContext { client ->
            client
                .post("/external/deltaker/personalia") {
                    setBody(objectMapper.writeValueAsString(deltakerIds))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal teste autentisering - ugyldig token - returnerer 401`() {
        val deltakerIds = listOf(UUID.randomUUID())

        withTestApplicationContext { client ->
            client
                .post("/external/deltaker/personalia") {
                    setBody(objectMapper.writeValueAsString(deltakerIds))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    bearerAuth("invalid-token")
                }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal teste autentisering - feil consumer app - returnerer 401`() {
        val deltakerIds = listOf(UUID.randomUUID())
        val token = generateJWT(
            consumerClientId = "wrong-app",
            audience = "amt-deltaker",
        )

        withTestApplicationContext { client ->
            client
                .post("/external/deltaker/personalia") {
                    setBody(objectMapper.writeValueAsString(deltakerIds))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    bearerAuth(token)
                }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post deltaker personalia - har tilgang - returnerer personalia`() {
        val navEnhet = TestData.lagNavEnhet(
            enhetsnummer = "1234",
            navn = "NAV Testheim",
        )
        val deltaker = TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(
                personident = "12345678901",
                fornavn = "Test",
                mellomnavn = "Midt",
                etternavn = "Testesen",
                navEnhetId = navEnhet.id,
                erSkjermet = false,
                adressebeskyttelse = null,
            ),
        )

        coEvery { deltakerService.getDeltakelser(listOf(deltaker.id)) } returns listOf(deltaker)
        coEvery { navEnhetService.getEnheter(setOf(navEnhet.id)) } returns mapOf(navEnhet.id to navEnhet)

        val forventetRespons = listOf(
            DeltakerPersonaliaResponse(
                deltakerId = deltaker.id,
                personident = "12345678901",
                fornavn = "Test",
                mellomnavn = "Midt",
                etternavn = "Testesen",
                navEnhetsnummer = "1234",
                erSkjermet = false,
                adressebeskyttelse = null,
            ),
        )

        withTestApplicationContext { client ->
            client
                .post("/external/deltaker/personalia") {
                    setBody(objectMapper.writeValueAsString(listOf(deltaker.id)))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    bearerAuth(mulighetsrommetSystemToken)
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
                }
        }
    }

    @Test
    fun `post deltaker personalia - deltaker med adressebeskyttelse - returnerer korrekt adressebeskyttelse`() {
        val navEnhet = TestData.lagNavEnhet(
            enhetsnummer = "5678",
            navn = "NAV Fortrolig",
        )
        val deltaker = TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(
                personident = "98765432109",
                fornavn = "Fortrolig",
                mellomnavn = null,
                etternavn = "Person",
                navEnhetId = navEnhet.id,
                erSkjermet = true,
                adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG,
            ),
        )

        coEvery { deltakerService.getDeltakelser(listOf(deltaker.id)) } returns listOf(deltaker)
        coEvery { navEnhetService.getEnheter(setOf(navEnhet.id)) } returns mapOf(navEnhet.id to navEnhet)

        val forventetRespons = listOf(
            DeltakerPersonaliaResponse(
                deltakerId = deltaker.id,
                personident = "98765432109",
                fornavn = "Fortrolig",
                mellomnavn = null,
                etternavn = "Person",
                navEnhetsnummer = "5678",
                erSkjermet = true,
                adressebeskyttelse = DeltakerPersonaliaResponse.AdressebeskyttelseResponse.STRENGT_FORTROLIG,
            ),
        )

        withTestApplicationContext { client ->
            client
                .post("/external/deltaker/personalia") {
                    setBody(objectMapper.writeValueAsString(listOf(deltaker.id)))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    bearerAuth(mulighetsrommetSystemToken)
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
                }
        }
    }

    @Test
    fun `post deltaker personalia - deltaker uten navEnhet - returnerer null for navEnhetsnummer`() {
        val deltaker = TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(
                personident = "11223344556",
                fornavn = "Uten",
                mellomnavn = null,
                etternavn = "NavEnhet",
                navEnhetId = null,
                erSkjermet = false,
                adressebeskyttelse = null,
            ),
        )

        coEvery { deltakerService.getDeltakelser(listOf(deltaker.id)) } returns listOf(deltaker)
        coEvery { navEnhetService.getEnheter(emptySet()) } returns emptyMap()

        val forventetRespons = listOf(
            DeltakerPersonaliaResponse(
                deltakerId = deltaker.id,
                personident = "11223344556",
                fornavn = "Uten",
                mellomnavn = null,
                etternavn = "NavEnhet",
                navEnhetsnummer = null,
                erSkjermet = false,
                adressebeskyttelse = null,
            ),
        )

        withTestApplicationContext { client ->
            client
                .post("/external/deltaker/personalia") {
                    setBody(objectMapper.writeValueAsString(listOf(deltaker.id)))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    bearerAuth(mulighetsrommetSystemToken)
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(forventetRespons)
                }
        }
    }

    @Test
    fun `post deltaker personalia - flere deltakere - returnerer alle personalia`() {
        val navEnhet1 = TestData.lagNavEnhet(enhetsnummer = "1111", navn = "NAV En")
        val navEnhet2 = TestData.lagNavEnhet(enhetsnummer = "2222", navn = "NAV To")

        val deltaker1 = TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(
                personident = "11111111111",
                fornavn = "Person",
                etternavn = "En",
                navEnhetId = navEnhet1.id,
            ),
        )
        val deltaker2 = TestData.lagDeltaker(
            navBruker = TestData.lagNavBruker(
                personident = "22222222222",
                fornavn = "Person",
                etternavn = "To",
                navEnhetId = navEnhet2.id,
            ),
        )

        coEvery { deltakerService.getDeltakelser(listOf(deltaker1.id, deltaker2.id)) } returns listOf(deltaker1, deltaker2)
        coEvery { navEnhetService.getEnheter(setOf(navEnhet1.id, navEnhet2.id)) } returns mapOf(
            navEnhet1.id to navEnhet1,
            navEnhet2.id to navEnhet2,
        )

        withTestApplicationContext { client ->
            client
                .post("/external/deltaker/personalia") {
                    setBody(objectMapper.writeValueAsString(listOf(deltaker1.id, deltaker2.id)))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    bearerAuth(mulighetsrommetSystemToken)
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    val response = objectMapper.readValue(bodyAsText(), Array<DeltakerPersonaliaResponse>::class.java).toList()
                    response.size shouldBe 2
                    response.find { it.deltakerId == deltaker1.id }?.navEnhetsnummer shouldBe "1111"
                    response.find { it.deltakerId == deltaker2.id }?.navEnhetsnummer shouldBe "2222"
                }
        }
    }

    @Test
    fun `post deltaker personalia - tom liste - returnerer tom liste`() {
        val deltakerIds = emptyList<UUID>()

        coEvery { deltakerService.getDeltakelser(deltakerIds) } returns emptyList()
        coEvery { navEnhetService.getEnheter(emptySet()) } returns emptyMap()

        withTestApplicationContext { client ->
            client
                .post("/external/deltaker/personalia") {
                    setBody(objectMapper.writeValueAsString(deltakerIds))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    bearerAuth(mulighetsrommetSystemToken)
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe "[ ]"
                }
        }
    }

    companion object {
        private val mulighetsrommetSystemToken = generateJWT(
            consumerClientId = "mulighetsrommet-api",
            audience = "amt-deltaker",
        )
    }
}
