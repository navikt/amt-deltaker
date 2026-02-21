package no.nav.amt.deltaker.deltaker.endring.extensions

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.deltaker.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ReaktiverDeltakelseRequest
import org.junit.jupiter.api.Test

class DeltakerEndringExtensionsTest {
    @Test
    fun `oppdaterDeltaker - reaktiver deltakelse lopende oppstart`() = runTest {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            deltakerliste = TestData.lagDeltakerlisteMedDirekteVedtak(),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = ReaktiverDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            begrunnelse = "begrunnelse",
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                deltakelsemengdeProvider = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            startdato.shouldBeNull()
            sluttdato.shouldBeNull()
        }
    }

    @Test
    fun `oppdaterDeltaker - reaktiver deltakelse felles oppstart`() = runTest {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            deltakerliste = TestData.lagDeltakerlisteMedTrengerGodkjenning(),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = ReaktiverDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            begrunnelse = "begrunnelse",
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                deltakelsemengdeProvider = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.SOKT_INN
            startdato.shouldBeNull()
            sluttdato.shouldBeNull()
        }
    }
}
