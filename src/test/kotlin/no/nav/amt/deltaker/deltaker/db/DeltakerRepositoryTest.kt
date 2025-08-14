package no.nav.amt.deltaker.deltaker.db

import io.kotest.matchers.collections.shouldHaveSize
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
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakerRepositoryTest {
    companion object {
        lateinit var repository: DeltakerRepository

        @JvmStatic
        @BeforeAll
        fun setup() {
            @Suppress("UnusedExpression")
            SingletonPostgres16Container
            repository = DeltakerRepository()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `upsert - ny deltaker - insertes`() {
        val deltaker = TestData.lagDeltaker()
        TestRepository.insert(deltaker.deltakerliste)
        TestRepository.insert(deltaker.navBruker)

        repository.upsert(deltaker)
        sammenlignDeltakere(repository.get(deltaker.id).getOrThrow(), deltaker)
    }

    @Test
    fun `upsert - oppdatert deltaker - oppdaterer`() {
        val deltaker = TestData.lagDeltaker()
        TestRepository.insert(deltaker)

        val oppdatertDeltaker = deltaker.copy(
            startdato = LocalDate.now().plusWeeks(1),
            sluttdato = LocalDate.now().plusWeeks(5),
            dagerPerUke = 1F,
            deltakelsesprosent = 20F,
        )

        repository.upsert(oppdatertDeltaker)
        sammenlignDeltakere(repository.get(deltaker.id).getOrThrow(), oppdatertDeltaker)
    }

    @Test
    fun `upsert - ny status - inserter ny status og deaktiverer gammel`() {
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
        sammenlignDeltakere(repository.get(deltaker.id).getOrThrow(), oppdatertDeltaker)

        val statuser = repository.getDeltakerStatuser(deltaker.id)
        statuser.first { it.id == deltaker.status.id }.gyldigTil shouldNotBe null
        statuser.first { it.id == deltaker.status.id }.type shouldBe DeltakerStatus.Type.DELTAR
        statuser.first { it.id == oppdatertDeltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == oppdatertDeltaker.status.id }.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
    }

    @Test
    fun `upsert - ny status gyldig i fremtid - inserter ny status, deaktiverer ikke gammel`() {
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
        sammenlignDeltakere(repository.get(deltaker.id).getOrThrow(), deltaker)

        val statuser = repository.getDeltakerStatuser(deltaker.id)
        statuser.first { it.id == deltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == deltaker.status.id }.type shouldBe DeltakerStatus.Type.DELTAR
        statuser.first { it.id == oppdatertDeltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == oppdatertDeltaker.status.id }.gyldigFra shouldBeCloseTo gyldigFra
        statuser.first { it.id == oppdatertDeltaker.status.id }.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
    }

    @Test
    fun `upsert - har fremtidig status, mottar ny status - inserter ny status, deaktiverer fremtidig status`() {
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

        sammenlignDeltakere(repository.get(opprinneligDeltaker.id).getOrThrow(), oppdatertDeltakerForlenget)

        val statuser = repository.getDeltakerStatuser(opprinneligDeltaker.id)
        statuser.first { it.id == opprinneligDeltaker.status.id }.gyldigTil shouldNotBe null
        statuser.first { it.id == opprinneligDeltaker.status.id }.type shouldBe DeltakerStatus.Type.DELTAR
        statuser.first { it.id == oppdatertDeltakerHarSluttet.status.id }.gyldigTil shouldNotBe null
        statuser.first { it.id == oppdatertDeltakerHarSluttet.status.id }.gyldigFra shouldBeCloseTo gyldigFra
        statuser.first { it.id == oppdatertDeltakerHarSluttet.status.id }.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        statuser.first { it.id == oppdatertDeltakerForlenget.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == oppdatertDeltakerForlenget.status.id }.type shouldBe DeltakerStatus.Type.DELTAR
    }

    @Test
    fun `upsert - har fremtidig status, ny fremtidig status - insert ny fremtidig status, sletter gammel fremtidig status`() {
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

        sammenlignDeltakere(
            repository.get(opprinneligDeltaker.id).getOrThrow(),
            opprinneligDeltaker.copy(sluttdato = oppdatertDeltakerHarSluttetNyArsak.sluttdato),
        )

        val statuser = repository.getDeltakerStatuser(opprinneligDeltaker.id)
        statuser.first { it.id == opprinneligDeltaker.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == opprinneligDeltaker.status.id }.type shouldBe DeltakerStatus.Type.DELTAR
        statuser.firstOrNull { it.id == oppdatertDeltakerHarSluttet.status.id } shouldBe null
        statuser.first { it.id == oppdatertDeltakerHarSluttetNyArsak.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == oppdatertDeltakerHarSluttetNyArsak.status.id }.gyldigFra shouldBeCloseTo gyldigFra
        statuser.first { it.id == oppdatertDeltakerHarSluttetNyArsak.status.id }.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        statuser.first { it.id == oppdatertDeltakerHarSluttetNyArsak.status.id }.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.UTDANNING
    }

    @Test
    fun `upsert - har sluttet til deltar, angitt neste status - oppdaterer status, insert neste fremtidige status`() {
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

        sammenlignDeltakere(
            repository.get(opprinneligDeltaker.id).getOrThrow(),
            oppdatertDeltakerDeltar,
        )

        val statuser = repository.getDeltakerStatuser(opprinneligDeltaker.id)
        statuser.size shouldBe 3
        statuser.first { it.id == opprinneligDeltaker.status.id }.gyldigTil shouldBeCloseTo LocalDateTime.now()
        statuser.first { it.id == opprinneligDeltaker.status.id }.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        statuser.first { it.id == oppdatertDeltakerDeltar.status.id }.gyldigTil shouldBe null
        statuser.first { it.id == oppdatertDeltakerDeltar.status.id }.gyldigFra shouldBeCloseTo LocalDateTime.now()
        statuser.first { it.id == oppdatertDeltakerDeltar.status.id }.type shouldBe DeltakerStatus.Type.DELTAR
        statuser.first { it.id == nesteStatus.id }.gyldigTil shouldBe null
        statuser.first { it.id == nesteStatus.id }.gyldigFra shouldBeCloseTo nySluttdato
        statuser.first { it.id == nesteStatus.id }.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        statuser.first { it.id == nesteStatus.id }.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.UTDANNING
    }

    @Test
    fun `skalHaStatusDeltar - venter pa oppstart, startdato passer - returnerer deltaker`() {
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
    fun `skalHaStatusDeltar - venter pa oppstart, mangler startdato - returnerer ikke deltaker`() {
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

    @Test
    fun `skalHaAvsluttendeStatus - deltar, sluttdato passert - returnerer deltaker`() {
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
    fun `skalHaAvsluttendeStatus - venter pa oppstart, sluttdato mangler - returnerer ikke deltaker`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().minusDays(10),
            sluttdato = null,
        )
        TestRepository.insert(deltaker)

        val deltakereSomSkalHaAvsluttendeStatus = repository.skalHaAvsluttendeStatus()

        deltakereSomSkalHaAvsluttendeStatus.size shouldBe 0
    }

    @Test
    fun `deltarPaAvsluttetDeltakerliste - deltar, dl-sluttdato passert - returnerer deltaker`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusDays(10),
            sluttdato = LocalDate.now().plusDays(2),
            deltakerliste = TestData.lagDeltakerListe(status = Deltakerliste.Status.AVSLUTTET),
        )
        TestRepository.insert(deltaker)

        val deltakerePaAvsluttetDeltakerliste = repository.deltarPaAvsluttetDeltakerliste()

        deltakerePaAvsluttetDeltakerliste.size shouldBe 1
        deltakerePaAvsluttetDeltakerliste.first().id shouldBe deltaker.id
    }

    @Test
    fun `deltarPaAvsluttetDeltakerliste - har sluttet, dl-sluttdato passert - returnerer ikke deltaker`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.HAR_SLUTTET),
            startdato = LocalDate.now().minusDays(10),
            sluttdato = LocalDate.now(),
            deltakerliste = TestData.lagDeltakerListe(status = Deltakerliste.Status.AVSLUTTET),
        )
        TestRepository.insert(deltaker)

        val deltakerePaAvsluttetDeltakerliste = repository.deltarPaAvsluttetDeltakerliste()

        deltakerePaAvsluttetDeltakerliste.size shouldBe 0
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
}

fun sammenlignDeltakere(a: Deltaker, b: Deltaker) {
    a.id shouldBe b.id
    a.navBruker shouldBe b.navBruker
    a.startdato shouldBe b.startdato
    a.sluttdato shouldBe b.sluttdato
    a.dagerPerUke shouldBe b.dagerPerUke
    a.deltakelsesprosent shouldBe b.deltakelsesprosent
    a.bakgrunnsinformasjon shouldBe b.bakgrunnsinformasjon
    a.deltakelsesinnhold shouldBe b.deltakelsesinnhold
    a.status.id shouldBe b.status.id
    a.status.type shouldBe b.status.type
    a.status.aarsak shouldBe b.status.aarsak
    a.status.gyldigFra shouldBeCloseTo b.status.gyldigFra
    a.status.gyldigTil shouldBeCloseTo b.status.gyldigTil
    a.status.opprettet shouldBeCloseTo b.status.opprettet
    a.sistEndret shouldBeCloseTo b.sistEndret
}
