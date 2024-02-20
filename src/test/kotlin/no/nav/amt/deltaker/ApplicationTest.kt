package no.nav.amt.deltaker

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import org.junit.Test

class ApplicationTest {
    private val pameldingService = mockk<PameldingService>()

    @Test
    fun testRoot() = testApplication {
        configureEnvForAuthentication()
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(pameldingService)
        }
        client.get("/internal/health/liveness").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("I'm alive!", bodyAsText())
        }
    }
}
