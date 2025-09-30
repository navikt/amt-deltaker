package no.nav.amt.deltaker.job

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakerProgresjonTest {
    @Test
    fun `tilAvsluttendeStatusOgDatoer - deltar avbrutt deltakerliste - far riktig status og arsak`() {
        val deltakerliste = TestData.lagDeltakerlisteMedFellesOppstart().copy(status = Deltakerliste.Status.AVBRUTT)
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = deltakerliste.sluttDato,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )

        val oppdatertDeltaker = DeltakerProgresjon().tilAvsluttendeStatusOgDatoer(listOf(deltaker), emptyList()).first()

        oppdatertDeltaker.sluttdato shouldBe LocalDate.now()
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.AVBRUTT
        oppdatertDeltaker.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
    }

    @Test
    fun `tilAvsluttendeStatusOgDatoer - venter avbrutt deltakerliste - far riktig status og arsak`() {
        val deltakerliste = TestData.lagDeltakerlisteMedFellesOppstart().copy(status = Deltakerliste.Status.AVBRUTT)
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = deltakerliste.sluttDato,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )

        val oppdatertDeltaker = DeltakerProgresjon().tilAvsluttendeStatusOgDatoer(listOf(deltaker), emptyList()) .first()

        oppdatertDeltaker.startdato shouldBe null
        oppdatertDeltaker.sluttdato shouldBe null
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
        oppdatertDeltaker.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
    }
}
