package no.nav.amt.deltaker.tiltakskoordinator

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import org.junit.Before
import org.junit.Test
import java.util.UUID

class TiltakskoordinatorApiTest {
    private val deltakerService = mockk<DeltakerService>()
    private val unleashToggle = mockk<UnleashToggle>()
    private val apiPath = "/tiltakskoordinator/deltakere"

    @Before
    fun setup() {
        configureEnvForAuthentication()
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("$apiPath/del-med-arrangor") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `del-med-arrangor - har tilgang - returnerer 200`() = testApplication {
        val deltaker = TestData.lagDeltaker()
        coEvery { deltakerService.upsertEndretDeltakere(any(), any(), any()) } returns listOf(deltaker)
        every { deltakerService.getHistorikk(deltaker.id) } returns emptyList()
        setUpTestApplication()
        client.post("$apiPath/del-med-arrangor") { postRequest(delMedArrangorRequest) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(listOf(deltaker.toDeltakerOppdatering(emptyList())))
        }
    }

    @Test
    fun `sett-paa-venteliste - har tilgang - returnerer 200`() = testApplication {
        val deltaker = TestData.lagDeltaker()
        val historikk = emptyList<DeltakerHistorikk>()
        coEvery { deltakerService.getDeltakelser(any()) } returns listOf(deltaker)
        coEvery { unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltakstype.arenaKode) } returns true
        coEvery { deltakerService.upsertEndretDeltakere(any(), any(), any()) } returns listOf(deltaker)
        every { deltakerService.getHistorikk(deltaker.id) } returns historikk
        val request = DeltakereRequest(
            deltakere = listOf(deltaker.id),
            endretAv = "Nav Veiledersen",
        )
        setUpTestApplication()
        client.post("$apiPath/sett-paa-venteliste") { postRequest(request) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(listOf(deltaker.toDeltakerOppdatering(historikk)))
        }
    }

    @Test
    fun `tildel plass - har tilgang - returnerer 200`() = testApplication {
        val deltaker = TestData.lagDeltaker()
        val historikk = emptyList<DeltakerHistorikk>()
        coEvery { deltakerService.getDeltakelser(any()) } returns listOf(deltaker)
        coEvery { unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltakstype.arenaKode) } returns true
        coEvery { deltakerService.endreDeltakere(any(), any(), any()) } returns listOf(deltaker)
        coEvery { deltakerService.upsertEndretDeltakere(any(), any(), any()) } returns listOf(deltaker)
        every { deltakerService.getHistorikk(deltaker.id) } returns historikk
        val request = DeltakereRequest(
            deltakere = listOf(deltaker.id),
            endretAv = "Nav Veiledersen",
        )
        setUpTestApplication()
        client.post("$apiPath/tildel-plass") { postRequest(request) }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe objectMapper.writeValueAsString(listOf(deltaker.toDeltakerOppdatering(historikk)))
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
                unleashToggle,
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
