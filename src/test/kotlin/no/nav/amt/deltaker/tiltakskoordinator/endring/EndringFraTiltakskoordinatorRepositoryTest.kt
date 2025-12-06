package no.nav.amt.deltaker.tiltakskoordinator.endring

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class EndringFraTiltakskoordinatorRepositoryTest {
    private val repository = EndringFraTiltakskoordinatorRepository()

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            @Suppress("UnusedExpression")
            SingletonPostgres16Container
        }
    }

    @Test
    fun `insert - ingen endring - feiler ikke`() {
        repository.insert(emptyList()) shouldBe Unit
    }

    @Test
    fun `insert - en endring - inserter`() {
        with(EndringFraTiltakskoordinatorCtx()) {
            repository.insert(listOf(endring))
            assertEndringLagret(this)
        }
    }

    @Test
    fun `insert - flere endringer - inserter`() {
        val endring1Ctx = EndringFraTiltakskoordinatorCtx()
        val endring2Ctx = EndringFraTiltakskoordinatorCtx()

        repository.insert(listOf(endring1Ctx.endring, endring2Ctx.endring))

        assertEndringLagret(endring1Ctx)
        assertEndringLagret(endring2Ctx)
    }

    private fun assertEndringLagret(ctx: EndringFraTiltakskoordinatorCtx) {
        val lagretEndring1 = repository.getForDeltaker(ctx.deltaker.id).first()
        sammenlignEndringFraTiltakskoordinator(ctx.endring, lagretEndring1)
    }
}

fun sammenlignEndringFraTiltakskoordinator(a: EndringFraTiltakskoordinator, b: EndringFraTiltakskoordinator) {
    a.id shouldBe b.id
    a.deltakerId shouldBe b.deltakerId
    a.endring shouldBe b.endring
    a.endretAv shouldBe b.endretAv
    a.endret shouldBeCloseTo b.endret
}

data class EndringFraTiltakskoordinatorCtx(
    val navAnsatt: NavAnsatt = TestData.lagNavAnsatt(),
    val navEnhet: NavEnhet = TestData.lagNavEnhet(navAnsatt.navEnhetId!!),
    val deltakerliste: Deltakerliste = TestData.lagDeltakerlisteMedFellesOppstart(),
    val navBruker: NavBruker = lagNavBruker(),
    var deltaker: Deltaker = TestData.lagDeltaker(
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = null,
        sluttdato = null,
        status = lagDeltakerStatus(type = DeltakerStatus.Type.SOKT_INN),
    ),
    val endring: EndringFraTiltakskoordinator = TestData.lagEndringFraTiltakskoordinator(
        deltakerId = deltaker.id,
        endretAv = navAnsatt.id,
        endretAvEnhet = navEnhet.id,
    ),
) {
    private val deltakerRepository = DeltakerRepository()

    init {
        @Suppress("UnusedExpression")
        SingletonPostgres16Container
        TestRepository.insert(navEnhet)
        TestRepository.insert(navAnsatt)
        TestRepository.insert(deltakerliste)
        TestRepository.insert(deltaker)
    }

    fun medStatusDeltar() {
        deltaker = deltaker.copy(status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR))
        deltakerRepository.upsert(deltaker)
    }

    fun medInnsok() {
        val innsok = TestData.lagInnsoktPaaKurs(
            deltakerId = deltaker.id,
            innsoktAv = navAnsatt.id,
            innsoktAvEnhet = navEnhet.id,
        )
        TestRepository.insert(innsok)
    }
}
