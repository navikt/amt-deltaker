package no.nav.amt.deltaker.tiltakskoordinator.endring

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import org.junit.jupiter.api.Test

class EndringFraTiltakskoordinatorServiceTest {
    private val repository = EndringFraTiltakskoordinatorRepository()
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonClient())
    private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonClient(), navEnhetService)

    private val service = EndringFraTiltakskoordinatorService(repository, navAnsattService)

    @Test
    fun `sjekkEndringUtfall - del med arrangør - oppdaterer attributt`(): Unit = runBlocking {
        with(EndringFraTiltakskoordinatorCtx()) {
            val endretDeltaker = service
                .sjekkEndringUtfall(
                    deltaker,
                    EndringFraTiltakskoordinator.DelMedArrangor,
                )
                .getOrThrow()

            endretDeltaker.erManueltDeltMedArrangor shouldBe true
            endretDeltaker.status.type shouldBe deltaker.status.type
        }
    }

    @Test
    fun `sjekkEndringUtfall - del med arrangør - ugyldig endring - returnerer failure`(): Unit = runBlocking {
        with(EndringFraTiltakskoordinatorCtx()) {
            medStatusDeltar()
            val resultat = service
                .sjekkEndringUtfall(
                    deltaker,
                    EndringFraTiltakskoordinator.DelMedArrangor,
                )

            resultat.isFailure shouldBe true
        }
    }
}
