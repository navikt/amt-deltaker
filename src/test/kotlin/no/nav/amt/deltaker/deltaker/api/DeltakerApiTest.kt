package no.nav.amt.deltaker.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.KladdService
import no.nav.amt.deltaker.deltaker.api.model.OppdaterDeltakerRequest
import no.nav.amt.deltaker.deltaker.api.model.OpprettKladdRequest
import no.nav.amt.deltaker.deltaker.api.model.toKladdResponse
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.utils.data.TestData
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerApiTest {
    private val kladdService = mockk<KladdService>()
    private val deltakerService = mockk<DeltakerService>()

    @Before
    fun setup() {
        configureEnvForAuthentication()
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/pamelding") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `post pamelding - har tilgang - returnerer deltaker`() = testApplication {
        val deltaker = TestData.lagDeltaker().toKladdResponse(TestData.lagNavAnsatt(), TestData.lagNavEnhet())

        coEvery { kladdService.opprettKladd(any(), any(), any(), any()) } returns deltaker

        setUpTestApplication()

        client.post("/pamelding") { postRequest(opprettKladdRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker)
        }
    }

    @Test
    fun `post pamelding - deltakerliste finnes ikke - reurnerer 404`() = testApplication {
        coEvery {
            kladdService.opprettKladd(
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

    @Test
    fun `post deltaker - har tilgang - returnerer 200`() = testApplication {
        coEvery { deltakerService.oppdaterDeltaker(any()) } just Runs

        setUpTestApplication()

        client.post("/deltaker") { postRequest(oppdaterDeltakerRequest) }.apply {
            status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `post deltaker - deltaker finnes ikke - returnerer 404`() = testApplication {
        coEvery { deltakerService.oppdaterDeltaker(any()) } throws NoSuchElementException("Fant ikke deltaker")

        setUpTestApplication()

        client.post("/deltaker") { postRequest(oppdaterDeltakerRequest) }.apply {
            status shouldBe HttpStatusCode.NotFound
        }
    }

    private fun ApplicationTestBuilder.setUpTestApplication() {
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                kladdService,
                deltakerService,
            )
        }
    }

    private val opprettKladdRequest = OpprettKladdRequest(UUID.randomUUID(), "1234", "Z123456", "0101")
    private val oppdaterDeltakerRequest = OppdaterDeltakerRequest(
        UUID.randomUUID(), LocalDate.now().minusDays(2),
        null, 3F, 50F, "Tekst", emptyList(), TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        null, "Z123456", "0101", LocalDateTime.now(),
        OppdaterDeltakerRequest.DeltakerEndring(
            UUID.randomUUID(),
            UUID.randomUUID(),
            DeltakerEndring.Endringstype.STARTDATO,
            DeltakerEndring.Endring.EndreStartdato(
                LocalDate.now(),
            ),
            "Z123456",
            "0101",
            LocalDateTime.now(),
        ),
    )
}
