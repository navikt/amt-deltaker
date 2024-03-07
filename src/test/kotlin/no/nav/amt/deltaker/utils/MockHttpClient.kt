package no.nav.amt.deltaker.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import io.ktor.utils.io.ByteReadChannel
import no.nav.amt.deltaker.amtperson.AmtPersonServiceClient
import no.nav.amt.deltaker.amtperson.dto.NavEnhetDto
import no.nav.amt.deltaker.application.plugins.applicationConfig
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.arrangor.AmtArrangorClient
import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.deltaker.arrangor.ArrangorDto
import no.nav.amt.deltaker.auth.AzureAdTokenClient
import no.nav.amt.deltaker.navansatt.NavAnsatt
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhet
import no.nav.amt.deltaker.navbruker.model.NavBruker
import no.nav.amt.deltaker.utils.data.TestData

const val AMT_PERSON_URL = "http://amt-person-service"

fun mockHttpClient(defaultResponse: Any? = null): HttpClient {
    val mockEngine = MockEngine {
        val api = Pair(it.url.toString(), it.method)
        if (defaultResponse != null) MockResponseHandler.addResponse(it.url.toString(), it.method, defaultResponse)
        val response = MockResponseHandler.responses[api]!!

        respond(
            content = ByteReadChannel(response.content),
            status = response.status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    return HttpClient(mockEngine) {
        install(ContentNegotiation) {
            jackson { applicationConfig() }
        }
    }
}

fun mockAmtArrangorClient(arrangor: Arrangor = TestData.lagArrangor()): AmtArrangorClient {
    val overordnetArrangor = arrangor.overordnetArrangorId?.let {
        TestData.lagArrangor(id = arrangor.overordnetArrangorId!!)
    }

    val response = ArrangorDto(arrangor.id, arrangor.navn, arrangor.organisasjonsnummer, overordnetArrangor)
    return AmtArrangorClient(
        baseUrl = "https://amt-arrangor",
        scope = "amt.arrangor.scope",
        httpClient = mockHttpClient(objectMapper.writeValueAsString(response)),
        azureAdTokenClient = mockAzureAdClient(),
    )
}

fun mockAmtPersonClient() = AmtPersonServiceClient(
    baseUrl = AMT_PERSON_URL,
    scope = "amt.person-service.scope",
    httpClient = mockHttpClient(),
    azureAdTokenClient = mockAzureAdClient(),
)

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

object MockResponseHandler {
    data class Response(
        val content: String,
        val status: HttpStatusCode,
    )

    val responses = mutableMapOf<Pair<String, HttpMethod>, Response>()

    fun addResponse(
        url: String,
        method: HttpMethod,
        responseBody: Any,
        responseCode: HttpStatusCode = HttpStatusCode.OK,
    ) {
        val api = Pair(url, method)
        responses[api] = Response(
            if (responseBody is String) responseBody else objectMapper.writeValueAsString(responseBody),
            responseCode,
        )
    }

    fun addNavAnsattResponse(navAnsatt: NavAnsatt) {
        val url = "$AMT_PERSON_URL/api/nav-ansatt/${navAnsatt.id}"
        addResponse(url, HttpMethod.Get, navAnsatt)
    }

    fun addNavEnhetResponse(navEnhet: NavEnhet) {
        val url = "$AMT_PERSON_URL/api/nav-enhet"
        addResponse(url, HttpMethod.Post, NavEnhetDto(navEnhet.id, navEnhet.enhetsnummer, navEnhet.navn))
    }

    fun addNavBrukerResponse(navBruker: NavBruker) {
        val url = "$AMT_PERSON_URL/api/nav-bruker"
        addResponse(url, HttpMethod.Post, navBruker)
    }
}
