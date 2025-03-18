package no.nav.amt.deltaker.tiltakskoordinator

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import no.nav.amt.lib.models.tiltakskoordinator.response.EndringFraTiltakskoordinatorResponse
import org.junit.Before
import org.junit.Test
import java.util.UUID

class TiltakskoordinatorApiTest {
    private val deltakerService = mockk<DeltakerService>()

    @Before
    fun setup() {
        configureEnvForAuthentication()
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/tiltakskoordinator/deltakere/del-med-arrangor") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `del-med-arrangor - har tilgang - returnerer 200`() = testApplication {
        val deltaker = TestData.lagDeltaker()
        coEvery { deltakerService.upsertEndretDeltakere(any()) } returns listOf(deltaker)

        setUpTestApplication()
        client.post("/tiltakskoordinator/deltakere/del-med-arrangor") { postRequest(delMedArrangorRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(listOf(deltaker.toResponse()))
        }
    }

    private fun ApplicationTestBuilder.setUpTestApplication() {
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                mockk(),
                deltakerService,
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
            )
        }
    }

    private val delMedArrangorRequest = DelMedArrangorRequest(
        endretAv = "koordinator",
        deltakerIder = listOf(UUID.randomUUID()),
    )
}

private fun Deltaker.toResponse() = EndringFraTiltakskoordinatorResponse(id, erManueltDeltMedArrangor, sistEndret)
