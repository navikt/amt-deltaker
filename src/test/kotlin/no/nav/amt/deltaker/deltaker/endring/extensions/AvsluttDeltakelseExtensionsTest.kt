package no.nav.amt.deltaker.deltaker.endring.extensions

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.deltaker.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvsluttDeltakelseRequest
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AvsluttDeltakelseExtensionsTest {
    @Test
    fun `oppdaterDeltaker - avslutt deltakelse`() = runTest {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val forslag = TestData.lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.AvsluttDeltakelse(LocalDate.now(), EndringAarsak.FattJobb, null, null),
        )
        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now(),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            forslagId = forslag.id,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                deltakelsemengdeProvider = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        val oppdatertDeltaker = resultat.deltaker

        assertSoftly(oppdatertDeltaker) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltaker - avslutt deltakelse i fremtiden - deltaker får ny sluttdato, fremtidig status`() = runTest {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusWeeks(1),
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

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            status.gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltaker - har sluttet, avslutt deltakelse i fremtiden - ny sluttdato, fremtidig status`() = runTest {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().minusWeeks(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusWeeks(1),
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

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.DELTAR
            status.gyldigFra.toLocalDate() shouldBe LocalDate.now()
            sluttdato shouldBe endringsrequest.sluttdato
        }

        assertSoftly(resultat.nesteStatus.shouldNotBeNull()) {
            type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
            gyldigTil shouldBe null
        }
    }

    @Test
    fun `oppdaterDeltaker - har sluttet, avslutt deltakelse i fortid - returnerer deltaker med ny sluttdato`() = runTest {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().minusWeeks(1),
        )
        val endretAv = TestData.lagNavAnsatt()
        val endretAvEnhet = TestData.lagNavEnhet()
        val endringsrequest = AvsluttDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().minusDays(1),
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

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            status.gyldigFra.toLocalDate() shouldBe LocalDate.now()
            sluttdato shouldBe endringsrequest.sluttdato
        }

        resultat.nesteStatus.shouldBeNull()
    }
}
