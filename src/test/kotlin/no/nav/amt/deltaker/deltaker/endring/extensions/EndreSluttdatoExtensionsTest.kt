package no.nav.amt.deltaker.deltaker.endring.extensions

import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.deltaker.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttdatoRequest
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EndreSluttdatoExtensionsTest {
    @Test
    fun `oppdaterDeltaker - endret sluttdato`() = runTest {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = SluttdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            forslagId = null,
            sluttdato = LocalDate.now().minusWeeks(1),
            begrunnelse = "begrunnelse",
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                deltakelsemengdeProvider = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        val oppdatertDeltaker = resultat.deltaker
        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
    }

    @Test
    fun `oppdaterDeltaker - endret sluttdato frem i tid - endrer status og sluttdato`() = runTest {
        val deltaker = TestData.lagDeltaker()
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = SluttdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            forslagId = null,
            sluttdato = LocalDate.now().plusWeeks(1),
            begrunnelse = "begrunnelse",
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                deltakelsemengdeProvider = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        val oppdatertDeltaker = resultat.deltaker

        oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
    }
}
