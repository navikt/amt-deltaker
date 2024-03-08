package no.nav.amt.deltaker.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.configureAuthentication
import no.nav.amt.deltaker.application.plugins.configureRouting
import no.nav.amt.deltaker.application.plugins.configureSerialization
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.api.model.BakgrunnsinformasjonRequest
import no.nav.amt.deltaker.deltaker.api.model.DeltakelsesmengdeRequest
import no.nav.amt.deltaker.deltaker.api.model.InnholdRequest
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.utils.data.TestData
import org.junit.Before
import org.junit.Test
import java.util.UUID

class DeltakerApiTest {
    private val deltakerService = mockk<DeltakerService>(relaxUnitFun = true)

    @Before
    fun setup() {
        configureEnvForAuthentication()
    }

    @Test
    fun `skal teste autentisering - mangler token - returnerer 401`() = testApplication {
        setUpTestApplication()
        client.post("/deltaker/${UUID.randomUUID()}/bakgrunnsinformasjon") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/innhold") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
        client.post("/deltaker/${UUID.randomUUID()}/deltakelsesmengde") { setBody("foo") }.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `post bakgrunnsinformasjon - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        client.post("/deltaker/${UUID.randomUUID()}/bakgrunnsinformasjon") {
            postRequest(
                BakgrunnsinformasjonRequest(
                    TestData.randomIdent(),
                    TestData.randomEnhetsnummer(),
                    "bakgrunnsinformasjon",
                ),
            )
        }.apply {
            status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `post innhold - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        client.post("/deltaker/${UUID.randomUUID()}/innhold") {
            postRequest(
                InnholdRequest(
                    TestData.randomIdent(),
                    TestData.randomEnhetsnummer(),
                    listOf(Innhold("Tekst", "kode", true, null)),
                ),
            )
        }.apply {
            status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `post deltakelsesmengde - har tilgang - returnerer 200`() = testApplication {
        setUpTestApplication()

        client.post("/deltaker/${UUID.randomUUID()}/deltakelsesmengde") {
            postRequest(
                DeltakelsesmengdeRequest(
                    TestData.randomIdent(),
                    TestData.randomEnhetsnummer(),
                    50,
                    2,
                ),
            )
        }.apply {
            status shouldBe HttpStatusCode.OK
        }
    }

    private fun ApplicationTestBuilder.setUpTestApplication() {
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                mockk(),
                deltakerService,
            )
        }
    }
}
