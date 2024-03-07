package no.nav.amt.deltaker

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import org.junit.Test

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        configureEnvForAuthentication()
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(mockk(), mockk())
        }
        client.get("/internal/health/liveness").apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "I'm alive!"
        }
    }
}
