package no.nav.amt.deltaker.application.plugins

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.utils.generateJWT
import org.junit.Before
import org.junit.Test

class AuthenticationTest {
    private val pameldingService = mockk<PameldingService>()

    @Before
    fun setup() {
        configureEnvForAuthentication()
    }

    @Test
    fun `testAuthentication - gyldig token, klient-app har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()
        client.get("/deltaker") {
            header(
                HttpHeaders.Authorization,
                "Bearer ${generateJWT(consumerClientId = "amt-deltaker-bff", audience = "amt-deltaker")}",
            )
        }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "System har tilgang!"
        }
    }

    @Test
    fun `testAuthentication - gyldig token, ikke maskin-til-maskin - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.get("/deltaker") {
            header(
                HttpHeaders.Authorization,
                "Bearer ${generateJWT(consumerClientId = "amt-deltaker-bff", audience = "amt-deltaker", oid = "ikke-subject")}",
            )
        }.apply {
            status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `testAuthentication - gyldig token, klient-app har ikke tilgang - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.get("/deltaker") {
            header(
                HttpHeaders.Authorization,
                "Bearer ${generateJWT(consumerClientId = "annen-consumer", audience = "amt-deltaker")}",
            )
        }.apply {
            status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `testAuthentication - gyldig token, feil audience - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.get("/deltaker") {
            header(
                HttpHeaders.Authorization,
                "Bearer ${generateJWT(consumerClientId = "amt-deltaker-bff", audience = "feil-aud")}",
            )
        }.apply {
            status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `testAuthentication - ugyldig tokenissuer - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.get("/deltaker") {
            header(
                HttpHeaders.Authorization,
                "Bearer ${
                    generateJWT(
                        consumerClientId = "amt-deltaker-bff",
                        audience = "amt-deltaker",
                        issuer = "annenIssuer",
                    )
                }",
            )
        }.apply {
            status shouldBe HttpStatusCode.Unauthorized
        }
    }

    private fun ApplicationTestBuilder.setUpTestApplication() {
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                pameldingService,
            )
            setUpTestRoute()
        }
    }

    private fun Application.setUpTestRoute() {
        routing {
            authenticate("SYSTEM") {
                get("/deltaker") {
                    call.respondText("System har tilgang!")
                }
            }
        }
    }
}
