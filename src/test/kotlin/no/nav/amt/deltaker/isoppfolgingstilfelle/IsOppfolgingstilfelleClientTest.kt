package no.nav.amt.deltaker.isoppfolgingstilfelle

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.navbruker.isoppfolgingstilfelle.OppfolgingstilfelleDTO
import no.nav.amt.deltaker.navbruker.isoppfolgingstilfelle.OppfolgingstilfellePersonDTO
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.mockIsOppfolgingstilfelleClient
import org.junit.Test
import java.time.LocalDate

class IsOppfolgingstilfelleClientTest {
    private val isOppfolgingstilfelleClient = mockIsOppfolgingstilfelleClient()
    private val personIdent = TestData.randomIdent()

    @Test
    fun `erSykmeldtMedArbeidsgiver - ingen oppfolgingstilfeller - returnerer false`(): Unit = runBlocking {
        MockResponseHandler.addOppfolgingstilfelleRespons(OppfolgingstilfellePersonDTO(emptyList()))

        isOppfolgingstilfelleClient.erSykmeldtMedArbeidsgiver(personIdent) shouldBe false
    }

    @Test
    fun `erSykmeldtMedArbeidsgiver - har oppfolgingstilfelle, ikke arbeidsgiver - returnerer false`(): Unit = runBlocking {
        MockResponseHandler.addOppfolgingstilfelleRespons(
            OppfolgingstilfellePersonDTO(
                listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = false,
                        start = LocalDate.now().minusMonths(3),
                        end = LocalDate.now().plusDays(1),
                    ),
                ),
            ),
        )

        isOppfolgingstilfelleClient.erSykmeldtMedArbeidsgiver(personIdent) shouldBe false
    }

    @Test
    fun `erSykmeldtMedArbeidsgiver - oppfolgingstilfelle avsluttet - returnerer false`(): Unit = runBlocking {
        MockResponseHandler.addOppfolgingstilfelleRespons(
            OppfolgingstilfellePersonDTO(
                listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusMonths(3),
                        end = LocalDate.now().minusDays(1),
                    ),
                ),
            ),
        )

        isOppfolgingstilfelleClient.erSykmeldtMedArbeidsgiver(personIdent) shouldBe false
    }

    @Test
    fun `erSykmeldtMedArbeidsgiver - har oppfolgingstilfelle og arbeidsgiver - returnerer true`(): Unit = runBlocking {
        MockResponseHandler.addOppfolgingstilfelleRespons(
            OppfolgingstilfellePersonDTO(
                listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusMonths(3),
                        end = LocalDate.now().plusWeeks(1),
                    ),
                ),
            ),
        )

        isOppfolgingstilfelleClient.erSykmeldtMedArbeidsgiver(personIdent) shouldBe true
    }
}
