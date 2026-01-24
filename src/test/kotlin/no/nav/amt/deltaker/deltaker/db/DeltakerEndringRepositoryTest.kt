package no.nav.amt.deltaker.deltaker.db

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.DatabaseTestExtension
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerEndringRepositoryTest {
    private val deltakerEndringRepository = DeltakerEndringRepository()

    companion object {
        @JvmField
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `getForDeltaker - to endringer for deltaker, navansatt og enhet finnes - returnerer endring med navn for ansatt og enhet`() {
        val navAnsatt1 = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt1)
        val navAnsatt2 = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt2)
        val navEnhet1 = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet1)
        val navEnhet2 = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet2)
        val deltaker = TestData.lagDeltaker()
        val deltakerEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt1.id,
            endretAvEnhet = navEnhet1.id,
        )
        val deltakerEndring2 = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endring = DeltakerEndring.Endring.EndreInnhold("ledetekst", listOf(Innhold("tekst", "type", true, null))),
            endretAv = navAnsatt2.id,
            endretAvEnhet = navEnhet2.id,
        )
        TestRepository.insert(deltaker)

        deltakerEndringRepository.upsert(deltakerEndring)
        deltakerEndringRepository.upsert(deltakerEndring2)

        val endringFraDb = deltakerEndringRepository.getForDeltaker(deltaker.id)

        endringFraDb.size shouldBe 2
        sammenlignDeltakerEndring(
            endringFraDb.find { it.id == deltakerEndring.id }!!,
            deltakerEndring.copy(endretAv = navAnsatt1.id, endretAvEnhet = navEnhet1.id),
        )
        sammenlignDeltakerEndring(
            endringFraDb.find { it.id == deltakerEndring2.id }!!,
            deltakerEndring2.copy(endretAv = navAnsatt2.id, endretAvEnhet = navEnhet2.id),
        )
    }

    @Test
    fun `getForDeltaker - deltaker er feilregistrert - returnerer tom liste`() {
        val navAnsatt1 = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt1)
        val navAnsatt2 = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt2)
        val navEnhet1 = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet1)
        val navEnhet2 = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet2)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.FEILREGISTRERT),
        )
        val deltakerEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt1.id,
            endretAvEnhet = navEnhet1.id,
        )
        val deltakerEndring2 = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endring = DeltakerEndring.Endring.EndreInnhold("ledetekst", listOf(Innhold("tekst", "type", true, null))),
            endretAv = navAnsatt2.id,
            endretAvEnhet = navEnhet2.id,
        )
        TestRepository.insert(deltaker)

        deltakerEndringRepository.upsert(deltakerEndring)
        deltakerEndringRepository.upsert(deltakerEndring2)

        val endringFraDb = deltakerEndringRepository.getForDeltaker(deltaker.id)

        endringFraDb.size shouldBe 0
    }

    @Test
    fun `getUbehandletDeltakelsesmengder - returnerer endringer som skal behandles i dag`() {
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )
        TestRepository.insertAll(navEnhet, navAnsatt, deltaker)

        val behandlet = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                deltakelsesprosent = 100F,
                dagerPerUke = null,
                gyldigFra = LocalDate.now().plusMonths(1),
                begrunnelse = null,
            ),
        )

        val skalBehandles = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                deltakelsesprosent = 42F,
                dagerPerUke = null,
                gyldigFra = LocalDate.now(),
                begrunnelse = null,
            ),
        )

        val skalBehandlesSenere = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                deltakelsesprosent = 100F,
                dagerPerUke = null,
                gyldigFra = LocalDate.now().plusMonths(1),
                begrunnelse = null,
            ),
        )

        deltakerEndringRepository.upsert(behandlet)
        deltakerEndringRepository.upsert(skalBehandles, null)
        deltakerEndringRepository.upsert(skalBehandlesSenere, null)

        val endringer = deltakerEndringRepository.getUbehandletDeltakelsesmengder()

        endringer.size shouldBe 1
        sammenlignDeltakerEndring(endringer.first(), skalBehandles)
    }
}

fun sammenlignDeltakerEndring(first: DeltakerEndring, second: DeltakerEndring) {
    first.id shouldBe second.id
    first.deltakerId shouldBe second.deltakerId
    first.endring shouldBe second.endring
    first.endretAv shouldBe second.endretAv
    first.endretAvEnhet shouldBe second.endretAvEnhet
    first.endret shouldBeCloseTo second.endret
}
