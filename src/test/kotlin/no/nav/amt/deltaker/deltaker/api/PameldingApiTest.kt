package no.nav.amt.deltaker.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.TestCase
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.api.model.OpprettKladdRequest
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.utils.data.TestData
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PameldingApiTest {
    private val pameldingService = mockk<PameldingService>()

    @Before
    fun setup() {
        configureEnvForAuthentication()
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/pamelding") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `post pamelding - har tilgang - returnerer deltaker`() = testApplication {
        val deltaker = TestData.lagDeltaker()

        coEvery { pameldingService.opprettKladd(any(), any(), any(), any()) } returns deltaker

        setUpTestApplication()

        client.post("/pamelding") { postRequest(opprettKladdRequest) }.apply {
            TestCase.assertEquals(HttpStatusCode.OK, status)
            TestCase.assertEquals(
                objectMapper.writeValueAsString(deltaker),
                bodyAsText(),
            )
        }
    }

    @Test
    fun `post pamelding - deltakerliste finnes ikke - reurnerer 404`() = testApplication {
        coEvery {
            pameldingService.opprettKladd(
                any(),
                any(),
                any(),
                any(),
            )
        } throws NoSuchElementException("Fant ikke deltakerliste")
        setUpTestApplication()
        client.post("/pamelding") { postRequest(opprettKladdRequest) }.apply {
            status shouldBe HttpStatusCode.NotFound
        }
    }

    private fun ApplicationTestBuilder.setUpTestApplication() {
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                pameldingService,
            )
        }
    }

    private val opprettKladdRequest = OpprettKladdRequest(UUID.randomUUID(), "1234", "Z123456", "0101")
}
