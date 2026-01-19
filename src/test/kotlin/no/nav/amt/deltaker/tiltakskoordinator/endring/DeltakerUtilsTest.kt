package no.nav.amt.deltaker.tiltakskoordinator.endring

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.deltaker.DeltakerUtils.sjekkEndringUtfall
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import org.junit.jupiter.api.Test

class DeltakerUtilsTest {
    @Test
    fun `sjekkEndringUtfall - del med arrangør - oppdaterer attributt`(): Unit = runBlocking {
        with(EndringFraTiltakskoordinatorCtx()) {
            val endretDeltaker = sjekkEndringUtfall(
                deltaker,
                EndringFraTiltakskoordinator.DelMedArrangor,
            ).getOrThrow()

            endretDeltaker.erManueltDeltMedArrangor shouldBe true
            endretDeltaker.status.type shouldBe deltaker.status.type
        }
    }

    @Test
    fun `sjekkEndringUtfall - del med arrangør - ugyldig endring - returnerer failure`(): Unit = runBlocking {
        with(EndringFraTiltakskoordinatorCtx()) {
            medStatusDeltar()
            val resultat = sjekkEndringUtfall(
                deltaker,
                EndringFraTiltakskoordinator.DelMedArrangor,
            )

            resultat.isFailure shouldBe true
        }
    }

    @Test
    fun `sjekkEndringUtfall - mangler oppfolgingsperiode - returnerer failure`(): Unit = runBlocking {
        with(
            EndringFraTiltakskoordinatorCtx(
                navBruker = lagNavBruker().copy(oppfolgingsperioder = emptyList()),
            ),
        ) {
            val resultat = sjekkEndringUtfall(
                deltaker,
                EndringFraTiltakskoordinator.DelMedArrangor,
            )

            resultat.isFailure shouldBe true
        }
    }
}
