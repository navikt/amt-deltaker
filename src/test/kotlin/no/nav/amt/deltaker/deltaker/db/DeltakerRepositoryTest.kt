package no.nav.amt.deltaker.deltaker.db

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.DatabaseTestExtension
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerRepositoryTest {
    private val deltakerRepository = DeltakerRepository()

    @Nested
    inner class Upsert {
        @Test
        fun `ny deltaker - insertes`() {
            val expectedDeltaker = lagDeltaker()
            TestRepository.insertAll(expectedDeltaker.deltakerliste, expectedDeltaker.navBruker)

            deltakerRepository.upsert(expectedDeltaker)
            DeltakerStatusRepository.lagreStatus(expectedDeltaker.id, expectedDeltaker.status)

            val deltakerFromDb = deltakerRepository.get(expectedDeltaker.id).getOrThrow()
            assertDeltakereAreEqual(deltakerFromDb, expectedDeltaker)
        }

        @Test
        fun `oppdatert deltaker - oppdaterer`() {
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                startdato = LocalDate.now().plusWeeks(1),
                sluttdato = LocalDate.now().plusWeeks(5),
                dagerPerUke = 1F,
                deltakelsesprosent = 20F,
            )

            deltakerRepository.upsert(oppdatertDeltaker)

            assertDeltakereAreEqual(deltakerRepository.get(deltaker.id).getOrThrow(), oppdatertDeltaker)
        }
    }

    @Nested
    inner class SkalHaAvsluttendeStatus {
        @Test
        fun `deltar, sluttdato passert - returnerer deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusWeeks(2),
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().minusDays(1),
            )
            deltakerRepository.upsert(oppdatertDeltaker)

            val deltakereSomSkalHaAvsluttendeStatus = deltakerRepository.skalHaAvsluttendeStatus()

            deltakereSomSkalHaAvsluttendeStatus.size shouldBe 1
            deltakereSomSkalHaAvsluttendeStatus.first().id shouldBe deltaker.id
        }

        @Test
        fun `venter pa oppstart, sluttdato mangler - returnerer ikke deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val deltakereSomSkalHaAvsluttendeStatus = deltakerRepository.skalHaAvsluttendeStatus()

            deltakereSomSkalHaAvsluttendeStatus.size shouldBe 0
        }
    }

    @Nested
    inner class DeltarPaAvsluttetDeltakerliste {
        @Test
        fun `deltar, dl-sluttdato passert - returnerer deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusDays(2),
                deltakerliste = lagDeltakerliste(status = GjennomforingStatusType.AVSLUTTET),
            )
            TestRepository.insert(deltaker)

            val deltakerePaAvsluttetDeltakerliste = deltakerRepository.deltarPaAvsluttetDeltakerliste()

            deltakerePaAvsluttetDeltakerliste.size shouldBe 1
            deltakerePaAvsluttetDeltakerliste.first().id shouldBe deltaker.id
        }

        @Test
        fun `har sluttet, dl-sluttdato passert - returnerer ikke deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now(),
                deltakerliste = lagDeltakerliste(status = GjennomforingStatusType.AVSLUTTET),
            )
            TestRepository.insert(deltaker)

            val deltakerePaAvsluttetDeltakerliste = deltakerRepository.deltarPaAvsluttetDeltakerliste()

            deltakerePaAvsluttetDeltakerliste.size shouldBe 0
        }
    }

    @Nested
    inner class GetTests {
        @Test
        fun `skal returnere eksisterende deltaker`() {
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()

            deltakerFraDb.shouldNotBeNull()
            deltakerFraDb.id shouldBe deltaker.id
        }

        @Test
        fun `deltaker er feilregistrert - fjerner informasjon`() {
            val deltaker = lagDeltaker(status = lagDeltakerStatus(type = DeltakerStatus.Type.FEILREGISTRERT))
            TestRepository.insert(deltaker)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            assertSoftly(deltakerFraDb) {
                startdato shouldBe null
                sluttdato shouldBe null
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                deltakelsesinnhold shouldBe null
            }
        }
    }

    @Test
    fun `getMany(list) - henter mange deltakere`() {
        val deltaker1 = lagDeltaker()
        val deltaker2 = lagDeltaker()

        TestRepository.insertAll(deltaker1, deltaker2)

        val deltakere = deltakerRepository.getMany(listOf(deltaker1.id, deltaker2.id))
        deltakere shouldHaveSize 2
        deltakere.contains(deltaker1)
        deltakere.contains(deltaker2)
    }

    companion object {
        @JvmField
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        fun assertDeltakereAreEqual(first: Deltaker, second: Deltaker) {
            assertSoftly(first) {
                id shouldBe second.id
                navBruker shouldBe second.navBruker
                startdato shouldBe second.startdato
                sluttdato shouldBe second.sluttdato
                dagerPerUke shouldBe second.dagerPerUke
                deltakelsesprosent shouldBe second.deltakelsesprosent
                bakgrunnsinformasjon shouldBe second.bakgrunnsinformasjon
                deltakelsesinnhold shouldBe second.deltakelsesinnhold
                status.id shouldBe second.status.id
                status.type shouldBe second.status.type
                status.aarsak shouldBe second.status.aarsak
                status.gyldigFra shouldBeCloseTo second.status.gyldigFra
                status.gyldigTil shouldBeCloseTo second.status.gyldigTil
                status.opprettet shouldBeCloseTo second.status.opprettet
                sistEndret shouldBeCloseTo second.sistEndret
            }
        }
    }
}
