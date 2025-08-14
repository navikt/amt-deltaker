package no.nav.amt.deltaker.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.requestvalidation.ValidationResult
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
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.OpprettKladdRequestValidator
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.api.model.request.AvbrytUtkastRequest
import no.nav.amt.deltaker.deltaker.api.model.request.OpprettKladdRequest
import no.nav.amt.deltaker.deltaker.api.model.request.UtkastRequest
import no.nav.amt.deltaker.deltaker.api.model.toDeltakerEndringResponse
import no.nav.amt.deltaker.deltaker.api.model.toKladdResponse
import no.nav.amt.deltaker.deltaker.api.utils.noBodyRequest
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class PameldingApiTest {
    private val opprettKladdRequestValidator = mockk<OpprettKladdRequestValidator>()
    private val pameldingService = mockk<PameldingService>()
    private val historikkService: DeltakerHistorikkService = mockk()

    @BeforeEach
    fun setup() {
        configureEnvForAuthentication()
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/pamelding") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/pamelding/${UUID.randomUUID()}") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/pamelding/${UUID.randomUUID()}/innbygger/godkjenn-utkast") { setBody("foo") }.status shouldBe
            HttpStatusCode.Unauthorized
        client.post("/pamelding/${UUID.randomUUID()}/avbryt") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.delete("/pamelding/${UUID.randomUUID()}").status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `post pamelding - har tilgang - returnerer deltaker`() = testApplication {
        val deltaker = lagDeltaker()

        coEvery {
            opprettKladdRequestValidator.validateRequest(any())
        } returns ValidationResult.Valid

        coEvery {
            pameldingService.opprettDeltaker(any(), any())
        } returns deltaker

        setUpTestApplication()

        val response = client.post("/pamelding") { postRequest(opprettKladdRequest) }

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toKladdResponse())
    }

    @Test
    fun `post pamelding - deltakerliste finnes ikke - returnerer 404`() = testApplication {
        coEvery {
            opprettKladdRequestValidator.validateRequest(any())
        } returns ValidationResult.Valid

        coEvery {
            pameldingService.opprettDeltaker(any(), any())
        } throws NoSuchElementException("Fant ikke deltakerliste")

        setUpTestApplication()

        val response = client.post("/pamelding") {
            postRequest(opprettKladdRequest)
        }

        response.status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `post pamelding utkast - har tilgang - returnerer 200`() = testApplication {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING))
        val historikk: List<DeltakerHistorikk> = listOf(DeltakerHistorikk.Vedtak(TestData.lagVedtak(deltakerVedVedtak = deltaker)))

        coEvery { historikkService.getForDeltaker(deltaker.id) } returns historikk

        coEvery { pameldingService.upsertUtkast(deltaker.id, any()) } returns deltaker

        setUpTestApplication()

        client.post("/pamelding/${deltaker.id}") { postRequest(utkastRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(deltaker.toDeltakerEndringResponse(historikk))
        }
    }

    @Test
    fun `post pamelding utkast - deltaker finnes ikke - returnerer 404`() = testApplication {
        val deltakerId = UUID.randomUUID()
        coEvery { pameldingService.upsertUtkast(deltakerId, any()) } throws NoSuchElementException("Fant ikke deltaker")

        setUpTestApplication()

        client.post("/pamelding/$deltakerId") { postRequest(utkastRequest) }.apply {
            status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun `post avbryt utkast - har tilgang - returnerer 200`() = testApplication {
        val deltakerId = UUID.randomUUID()
        coEvery { pameldingService.avbrytUtkast(deltakerId, any()) } just Runs

        setUpTestApplication()

        client.post("/pamelding/$deltakerId/avbryt") { postRequest(avbrytUtkastRequest) }.apply {
            status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `delete kladd - har tilgang - returnerer 200`() = testApplication {
        val deltakerId = UUID.randomUUID()
        coEvery { pameldingService.slettKladd(deltakerId) } just Runs

        setUpTestApplication()

        client.delete("/pamelding/$deltakerId") { noBodyRequest() }.apply {
            status shouldBe HttpStatusCode.OK
        }
    }

    private fun ApplicationTestBuilder.setUpTestApplication() {
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                opprettKladdRequestValidator = opprettKladdRequestValidator,
                pameldingService = pameldingService,
                deltakerService = mockk(),
                deltakerHistorikkService = historikkService,
                tilgangskontrollService = mockk(),
                deltakelserResponseMapper = mockk(),
                deltakerProducerService = mockk(),
                vedtakService = mockk(),
                unleashToggle = mockk(),
                innsokPaaFellesOppstartService = mockk(),
                vurderingService = mockk(),
                hendelseService = mockk(),
                endringFraTiltakskoordinatorService = mockk(),
            )
        }
    }

    private val opprettKladdRequest = OpprettKladdRequest(UUID.randomUUID(), "1234")

    private val utkastRequest = UtkastRequest(
        Deltakelsesinnhold("test", listOf(Innhold("Tekst", "kode", true, null))),
        "bakgrunn og sånn",
        50F,
        3F,
        "Z123456",
        "0101",
        false,
    )
    private val avbrytUtkastRequest = AvbrytUtkastRequest("Z123456", "0101")
}
