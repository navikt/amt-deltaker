package no.nav.amt.deltaker.tiltakskoordinator.endring

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import org.junit.Test
import java.util.UUID

class EndringFraTiltakskoordinatorServiceTest {
    private val repository = EndringFraTiltakskoordinatorRepository()
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonClient())
    private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockAmtPersonClient(), navEnhetService)

    private val service = EndringFraTiltakskoordinatorService(repository, navAnsattService)

    @Test
    fun `insertEndringer(DelMedArrangor) - en deltaker - inserter endring og returnerer endret deltaker`(): Unit = runBlocking {
        with(EndringFraTiltakskoordinatorCtx()) {
            val endretDeltaker = service
                .upsertEndringPaaDeltakere(
                    listOf(deltaker),
                    EndringFraTiltakskoordinator.DelMedArrangor,
                    navAnsatt,
                    navEnhet,
                ).first()
                .getOrThrow()

            endretDeltaker.erManueltDeltMedArrangor shouldBe true

            repository.getForDeltaker(deltaker.id) shouldHaveSize 1
        }
    }

    @Test
    fun `insertEndringer(DelMedArrangor) - flere deltakere - inserter endring og returnerer endret deltakere`(): Unit = runBlocking {
        with(EndringFraTiltakskoordinatorCtx()) {
            val deltaker2 = deltaker.copy(id = UUID.randomUUID())
            TestRepository.insert(deltaker2)

            val endretDeltakere = service
                .upsertEndringPaaDeltakere(
                    listOf(deltaker, deltaker2),
                    EndringFraTiltakskoordinator.DelMedArrangor,
                    navAnsatt,
                    navEnhet,
                )

            endretDeltakere.forEach { it.getOrThrow().erManueltDeltMedArrangor shouldBe true }
            repository.getForDeltaker(deltaker.id) shouldHaveSize 1
            repository.getForDeltaker(deltaker2.id) shouldHaveSize 1
        }
    }

    @Test
    fun `insertEndringer(DelMedArrangor) - ugyldig endring - inserter ikke endring og returnerer failure`(): Unit = runBlocking {
        with(EndringFraTiltakskoordinatorCtx()) {
            medStatusDeltar()
            val resultat = service
                .upsertEndringPaaDeltakere(
                    listOf(deltaker),
                    EndringFraTiltakskoordinator.DelMedArrangor,
                    navAnsatt,
                    navEnhet,
                ).first()

            resultat.isFailure shouldBe true
            repository.getForDeltaker(deltaker.id) shouldHaveSize 0
        }
    }

    @Test
    fun `insertEndringer(DelMedArrangor) - flere deltakere, en gyldig en ugyldig - inserter riktig og returnerer endret deltakere`(): Unit =
        runBlocking {
            with(EndringFraTiltakskoordinatorCtx()) {
                val deltaker2 = deltaker.copy(id = UUID.randomUUID())
                TestRepository.insert(deltaker2)
                medStatusDeltar()

                val endretDeltakere = service
                    .upsertEndringPaaDeltakere(
                        listOf(deltaker, deltaker2),
                        EndringFraTiltakskoordinator.DelMedArrangor,
                        navAnsatt,
                        navEnhet,
                    )

                endretDeltakere.count { it.isFailure } shouldBe 1
                endretDeltakere.count { it.isSuccess } shouldBe 1

                repository.getForDeltaker(deltaker.id) shouldHaveSize 0
                repository.getForDeltaker(deltaker2.id) shouldHaveSize 1
            }
        }
}
