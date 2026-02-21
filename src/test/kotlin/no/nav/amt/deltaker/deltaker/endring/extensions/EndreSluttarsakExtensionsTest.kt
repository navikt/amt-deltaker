package no.nav.amt.deltaker.deltaker.endring.extensions

import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.deltaker.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttarsakRequest
import org.junit.jupiter.api.Test

class EndreSluttarsakExtensionsTest {
    @Test
    fun `oppdaterDeltaker - endret sluttarsak`() = runTest {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.SYK,
            ),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()

        val endringsrequest = SluttarsakRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = null,
            forslagId = null,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                deltakelsemengdeProvider = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        val oppdatertDeltaker = resultat.deltaker

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        oppdatertDeltaker.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
    }
}
