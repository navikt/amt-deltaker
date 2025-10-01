package no.nav.amt.deltaker.deltaker.db

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.SingletonPostgres16Container
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakerRepositoryTest {
    @BeforeEach
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Nested
    inner class Upsert {
        @Test
        fun `ny deltaker - insertes`() {
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker.deltakerliste)
            TestRepository.insert(deltaker.navBruker)

            repository.upsert(deltaker)

            assertDeltakereAreEqual(repository.get(deltaker.id).getOrThrow(), deltaker)
        }

        @Test
        fun `oppdatert deltaker - oppdaterer`() {
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                startdato = LocalDate.now().plusWeeks(1),
                sluttdato = LocalDate.now().plusWeeks(5),
                dagerPerUke = 1F,
                deltakelsesprosent = 20F,
            )

            repository.upsert(oppdatertDeltaker)

            assertDeltakereAreEqual(repository.get(deltaker.id).getOrThrow(), oppdatertDeltaker)
        }

        @Test
        fun `ny status - inserter ny status og deaktiverer gammel`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = TestData.lagDeltakerStatus(
                    type = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsak = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                ),
            )

            repository.upsert(oppdatertDeltaker)

            assertDeltakereAreEqual(repository.get(deltaker.id).getOrThrow(), oppdatertDeltaker)

            val statuser = repository.getDeltakerStatuser(deltaker.id)

            statuser.size shouldBe 2

            assertSoftly(statuser.first { it.id == deltaker.status.id }) {
                gyldigTil shouldNotBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(statuser.first { it.id == oppdatertDeltaker.status.id }) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }
        }

        @Test
        fun `ny status gyldig i fremtid - inserter ny status, deaktiverer ikke gammel`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker)

            val gyldigFra = LocalDateTime.now().plusDays(3)
            val oppdatertDeltaker = deltaker.copy(
                status = TestData.lagDeltakerStatus(
                    type = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsak = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                    gyldigFra = gyldigFra,
                ),
            )

            repository.upsert(oppdatertDeltaker)
            assertDeltakereAreEqual(repository.get(deltaker.id).getOrThrow(), deltaker)

            val statuser = repository.getDeltakerStatuser(deltaker.id)

            statuser.size shouldBe 2

            assertSoftly(statuser.first { it.id == deltaker.status.id }) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(statuser.first { it.id == oppdatertDeltaker.status.id }) {
                gyldigTil shouldBe null
                gyldigFra shouldBeCloseTo gyldigFra
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }
        }

        @Test
        fun `har fremtidig status, mottar ny status - inserter ny status, deaktiverer fremtidig status`() {
            val opprinneligDeltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusWeeks(2),
            )
            TestRepository.insert(opprinneligDeltaker)

            val gyldigFra = LocalDateTime.now().plusDays(3)
            val oppdatertDeltakerHarSluttet = opprinneligDeltaker.copy(
                status = TestData.lagDeltakerStatus(
                    type = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsak = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                    gyldigFra = gyldigFra,
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )
            repository.upsert(oppdatertDeltakerHarSluttet)

            val oppdatertDeltakerForlenget = opprinneligDeltaker.copy(
                status = TestData.lagDeltakerStatus(
                    type = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now(),
                ),
                sluttdato = LocalDate.now().plusWeeks(8),
            )
            repository.upsert(oppdatertDeltakerForlenget)

            assertDeltakereAreEqual(repository.get(opprinneligDeltaker.id).getOrThrow(), oppdatertDeltakerForlenget)

            val statuser = repository.getDeltakerStatuser(opprinneligDeltaker.id)

            statuser.size shouldBe 3

            assertSoftly(statuser.first { it.id == opprinneligDeltaker.status.id }) {
                gyldigTil shouldNotBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(statuser.first { it.id == oppdatertDeltakerHarSluttet.status.id }) {
                gyldigTil shouldNotBe null
                gyldigFra shouldBeCloseTo gyldigFra
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }

            assertSoftly(statuser.first { it.id == oppdatertDeltakerForlenget.status.id }) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }
        }

        @Test
        fun `har fremtidig status, ny fremtidig status - insert ny fremtidig status, sletter gammel fremtidig status`() {
            val opprinneligDeltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusWeeks(2),
            )
            TestRepository.insert(opprinneligDeltaker)

            val gyldigFra = LocalDateTime.now().plusDays(3)
            val oppdatertDeltakerHarSluttet = opprinneligDeltaker.copy(
                status = TestData.lagDeltakerStatus(
                    type = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsak = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                    gyldigFra = gyldigFra,
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )
            repository.upsert(oppdatertDeltakerHarSluttet)

            val oppdatertDeltakerHarSluttetNyArsak = opprinneligDeltaker.copy(
                status = TestData.lagDeltakerStatus(
                    type = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsak = DeltakerStatus.Aarsak.Type.UTDANNING,
                    gyldigFra = gyldigFra,
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )
            repository.upsert(oppdatertDeltakerHarSluttetNyArsak)

            assertDeltakereAreEqual(
                repository.get(opprinneligDeltaker.id).getOrThrow(),
                opprinneligDeltaker.copy(sluttdato = oppdatertDeltakerHarSluttetNyArsak.sluttdato),
            )

            val statuser = repository.getDeltakerStatuser(opprinneligDeltaker.id)
            statuser.size shouldBe 2

            statuser.map { it.id } shouldNotContain oppdatertDeltakerHarSluttet.status.id

            assertSoftly(statuser.first { it.id == opprinneligDeltaker.status.id }) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(statuser.first { it.id == oppdatertDeltakerHarSluttetNyArsak.status.id }) {
                gyldigTil shouldBe null
                gyldigFra shouldBeCloseTo gyldigFra
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.UTDANNING
            }
        }

        @Test
        fun `har sluttet til deltar, angitt neste status - oppdaterer status, insert neste fremtidige status`() {
            val opprinneligDeltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
                sluttdato = LocalDate.now().minusDays(2),
            )
            TestRepository.insert(opprinneligDeltaker)

            val nySluttdato = LocalDateTime.now().plusDays(3)
            val oppdatertDeltakerDeltar = opprinneligDeltaker.copy(
                status = TestData.lagDeltakerStatus(
                    type = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now(),
                ),
                sluttdato = nySluttdato.toLocalDate(),
            )

            val nesteStatus = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.HAR_SLUTTET,
                aarsak = DeltakerStatus.Aarsak.Type.UTDANNING,
                gyldigFra = nySluttdato,
            )

            repository.upsert(oppdatertDeltakerDeltar, nesteStatus)

            assertDeltakereAreEqual(
                repository.get(opprinneligDeltaker.id).getOrThrow(),
                oppdatertDeltakerDeltar,
            )

            val statuser = repository.getDeltakerStatuser(opprinneligDeltaker.id)

            statuser.size shouldBe 3

            assertSoftly(statuser.first { it.id == opprinneligDeltaker.status.id }) {
                gyldigTil shouldBeCloseTo LocalDateTime.now()
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }

            assertSoftly(statuser.first { it.id == oppdatertDeltakerDeltar.status.id }) {
                gyldigTil shouldBe null
                gyldigFra shouldBeCloseTo LocalDateTime.now()
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(statuser.first { it.id == nesteStatus.id }) {
                gyldigTil shouldBe null
                gyldigFra shouldBeCloseTo nySluttdato
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.UTDANNING
            }
        }
    }

    @Nested
    inner class GetAvsluttendeDeltakerStatuserForOppdatering {
        @Test
        fun `returnerer tom liste nar ingen deltaker har aktiv DELTAR-status`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(
                    type = DeltakerStatus.Type.HAR_SLUTTET,
                    gyldigTil = null,
                ),
            )
            TestRepository.insert(deltaker)

            val statuser = repository.getAvsluttendeDeltakerStatuserForOppdatering()
            statuser shouldBe emptyList()
        }

        @Test
        fun `fremtidig HAR_SLUTTET-status skal ikke inkluderes`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker)

            val fremtidigStatus = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().plusDays(5),
            )
            repository.upsert(deltaker, fremtidigStatus)

            val statuser = repository.getAvsluttendeDeltakerStatuserForOppdatering()
            statuser shouldBe emptyList()
        }

        @Test
        fun `returnerer kun deltakerstatus for deltakere med aktiv DELTAR og gyldig avsluttende status`() {
            val deltaker1 = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            )
            val deltaker2 = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)

            val status1 = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusDays(1),
            )
            repository.upsert(deltaker1, status1)

            val statuser = repository.getAvsluttendeDeltakerStatuserForOppdatering()
            statuser.size shouldBe 1
            statuser.first().deltakerId shouldBe deltaker1.id
        }

        @Test
        fun `henter avsluttende deltakerstatus for deltaker som har aktiv DELTAR-status og kommende HAR_SLUTTET-status`() {
            val opprinneligDeltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
                sluttdato = LocalDate.now().minusDays(2),
            )
            TestRepository.insert(opprinneligDeltaker)

            val oppdatertDeltakerDeltar = opprinneligDeltaker.copy(
                status = TestData.lagDeltakerStatus(
                    type = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now(),
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )

            val nesteStatus = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.HAR_SLUTTET,
                aarsak = DeltakerStatus.Aarsak.Type.UTDANNING,
                gyldigFra = LocalDateTime.now().minusDays(1),
            )

            repository.upsert(oppdatertDeltakerDeltar, nesteStatus)

            val statuser: List<DeltakerStatusMedDeltakerId> = repository.getAvsluttendeDeltakerStatuserForOppdatering()
            statuser.size shouldBe 1

            assertSoftly(statuser.first()) {
                deltakerId shouldBe opprinneligDeltaker.id

                assertSoftly(deltakerStatus) {
                    type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                    aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.UTDANNING
                    gyldigFra.toLocalDate() shouldBe LocalDate.now().minusDays(1)
                    gyldigTil shouldBe null
                }
            }
        }
    }

    @Nested
    inner class SkalHaStatusDeltar {
        @Test
        fun `venter pa oppstart, startdato passer - returnerer deltaker`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                startdato = null,
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(1),
                sluttdato = LocalDate.now().plusWeeks(2),
            )
            repository.upsert(oppdatertDeltaker)

            val deltakereSomSkalHaStatusDeltar = repository.skalHaStatusDeltar()

            deltakereSomSkalHaStatusDeltar.size shouldBe 1
            deltakereSomSkalHaStatusDeltar.first().id shouldBe deltaker.id
        }

        @Test
        fun `venter pa oppstart, mangler startdato - returnerer ikke deltaker`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                startdato = null,
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = null,
                sluttdato = null,
            )
            repository.upsert(oppdatertDeltaker)

            val deltakereSomSkalHaStatusDeltar = repository.skalHaStatusDeltar()

            deltakereSomSkalHaStatusDeltar.size shouldBe 0
        }
    }

    @Nested
    inner class SkalHaAvsluttendeStatus {
        @Test
        fun `deltar, sluttdato passert - returnerer deltaker`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusWeeks(2),
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().minusDays(1),
            )
            repository.upsert(oppdatertDeltaker)

            val deltakereSomSkalHaAvsluttendeStatus = repository.skalHaAvsluttendeStatus()

            deltakereSomSkalHaAvsluttendeStatus.size shouldBe 1
            deltakereSomSkalHaAvsluttendeStatus.first().id shouldBe deltaker.id
        }

        @Test
        fun `venter pa oppstart, sluttdato mangler - returnerer ikke deltaker`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val deltakereSomSkalHaAvsluttendeStatus = repository.skalHaAvsluttendeStatus()

            deltakereSomSkalHaAvsluttendeStatus.size shouldBe 0
        }
    }

    @Nested
    inner class DeltarPaAvsluttetDeltakerliste {
        @Test
        fun `deltar, dl-sluttdato passert - returnerer deltaker`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusDays(2),
                deltakerliste = TestData.lagDeltakerliste(status = Deltakerliste.Status.AVSLUTTET),
            )
            TestRepository.insert(deltaker)

            val deltakerePaAvsluttetDeltakerliste = repository.deltarPaAvsluttetDeltakerliste()

            deltakerePaAvsluttetDeltakerliste.size shouldBe 1
            deltakerePaAvsluttetDeltakerliste.first().id shouldBe deltaker.id
        }

        @Test
        fun `har sluttet, dl-sluttdato passert - returnerer ikke deltaker`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now(),
                deltakerliste = TestData.lagDeltakerliste(status = Deltakerliste.Status.AVSLUTTET),
            )
            TestRepository.insert(deltaker)

            val deltakerePaAvsluttetDeltakerliste = repository.deltarPaAvsluttetDeltakerliste()

            deltakerePaAvsluttetDeltakerliste.size shouldBe 0
        }
    }

    @Test
    fun `get - deltaker er feilregistrert - fjerner informasjon`() {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.FEILREGISTRERT))
        TestRepository.insert(deltaker)

        val deltakerFraDb = repository.get(deltaker.id).getOrThrow()

        deltakerFraDb.startdato shouldBe null
        deltakerFraDb.sluttdato shouldBe null
        deltakerFraDb.dagerPerUke shouldBe null
        deltakerFraDb.deltakelsesprosent shouldBe null
        deltakerFraDb.bakgrunnsinformasjon shouldBe null
        deltakerFraDb.deltakelsesinnhold shouldBe null
    }

    @Test
    fun `getMany(list) - henter mange deltakere`() {
        val deltaker1 = TestData.lagDeltaker()
        val deltaker2 = TestData.lagDeltaker()

        TestRepository.insertAll(deltaker1, deltaker2)

        val deltakere = repository.getMany(listOf(deltaker1.id, deltaker2.id))
        deltakere shouldHaveSize 2
        deltakere.contains(deltaker1)
        deltakere.contains(deltaker2)
    }

    companion object {
        lateinit var repository: DeltakerRepository

        @JvmStatic
        @BeforeAll
        fun setup() {
            @Suppress("UnusedExpression")
            SingletonPostgres16Container
            repository = DeltakerRepository()
        }

        private fun assertDeltakereAreEqual(first: Deltaker, second: Deltaker) {
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
