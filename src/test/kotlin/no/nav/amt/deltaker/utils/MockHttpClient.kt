package no.nav.amt.deltaker.utils

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
import no.nav.amt.deltaker.application.plugins.applicationConfig
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.auth.AzureAdTokenClient
import no.nav.amt.deltaker.navansatt.AmtPersonServiceClient
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.NavEnhetDto
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.utils.data.TestData

fun mockHttpClient(response: String): HttpClient {
    val mockEngine = MockEngine {
        respond(
            content = ByteReadChannel(response),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    return HttpClient(mockEngine) {
        install(ContentNegotiation) {
            jackson { applicationConfig() }
        }
    }
}

fun mockAmtPersonServiceClientNavAnsatt(navAnsatt: NavAnsatt = TestData.lagNavAnsatt()): AmtPersonServiceClient {
    return AmtPersonServiceClient(
        baseUrl = "https://amt-person-service",
        scope = "amt.person-service.scope",
        httpClient = mockHttpClient(objectMapper.writeValueAsString(navAnsatt)),
        azureAdTokenClient = mockAzureAdClient(),
    )
}

fun mockAmtPersonServiceClientNavEnhet(navEnhet: NavEnhet = TestData.lagNavEnhet()): AmtPersonServiceClient {
    return AmtPersonServiceClient(
        baseUrl = "https://amt-person-service",
        scope = "amt.person-service.scope",
        httpClient = mockHttpClient(objectMapper.writeValueAsString(NavEnhetDto(id = navEnhet.id, enhetId = navEnhet.enhetsnummer, navn = navEnhet.navn))),
        azureAdTokenClient = mockAzureAdClient(),
    )
}

fun mockAzureAdClient() = AzureAdTokenClient(
    azureAdTokenUrl = "http://azure",
    clientId = "clientId",
    clientSecret = "secret",
    httpClient = mockHttpClient(
        """
            {
                "token_type":"Bearer",
                "access_token":"XYZ",
                "expires_in": 3599
            }
        """.trimIndent(),
    ),
)
