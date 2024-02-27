package no.nav.amt.deltaker.deltaker.db

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.shouldBeCloseTo
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate

class DeltakerRepositoryTest {
    companion object {
        lateinit var repository: DeltakerRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
            repository = DeltakerRepository()
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `upsert - ny deltaker - insertes`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        val deltaker = TestData.lagDeltaker(
            sistEndretAv = sistEndretAv.id,
            sistEndretAvEnhet = sistEndretAvEnhet.id,
        )
        TestRepository.insert(deltaker.deltakerliste)
        TestRepository.insert(deltaker.navBruker)
        TestRepository.insert(sistEndretAv)
        TestRepository.insert(sistEndretAvEnhet)

        repository.upsert(deltaker)
        sammenlignDeltakere(repository.get(deltaker.id).getOrThrow(), deltaker)
    }

    @Test
    fun `upsert - oppdatert deltaker - oppdaterer`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        val deltaker = TestData.lagDeltaker(
            sistEndretAv = sistEndretAv.id,
            sistEndretAvEnhet = sistEndretAvEnhet.id,
        )
        TestRepository.insert(deltaker, sistEndretAv, sistEndretAvEnhet)
        val annenAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(annenAnsatt)

        val oppdatertDeltaker = deltaker.copy(
            startdato = LocalDate.now().plusWeeks(1),
            sluttdato = LocalDate.now().plusWeeks(5),
            dagerPerUke = 1F,
            deltakelsesprosent = 20F,
            sistEndretAv = annenAnsatt.id,
        )

        repository.upsert(oppdatertDeltaker)
        sammenlignDeltakere(repository.get(deltaker.id).getOrThrow(), oppdatertDeltaker)
    }

    @Test
    fun `upsert - ny status - inserter ny status og deaktiverer gammel`() {
        val sistEndretAv = TestData.lagNavAnsatt()
        val sistEndretAvEnhet = TestData.lagNavEnhet()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
            sistEndretAv = sistEndretAv.id,
            sistEndretAvEnhet = sistEndretAvEnhet.id,
        )
        TestRepository.insert(deltaker, sistEndretAv, sistEndretAvEnhet)

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
        statuser.first { it.id == oppdatertDeltaker.status.id }.gyldigTil shouldBe null
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
    a.innhold shouldBe b.innhold
    a.status.id shouldBe b.status.id
    a.status.type shouldBe b.status.type
    a.status.aarsak shouldBe b.status.aarsak
    a.status.gyldigFra shouldBeCloseTo b.status.gyldigFra
    a.status.gyldigTil shouldBeCloseTo b.status.gyldigTil
    a.status.opprettet shouldBeCloseTo b.status.opprettet
    a.sistEndretAv shouldBe b.sistEndretAv
    a.sistEndretAvEnhet shouldBe b.sistEndretAvEnhet
    a.sistEndret shouldBeCloseTo b.sistEndret
    a.opprettet shouldBeCloseTo b.opprettet
}
