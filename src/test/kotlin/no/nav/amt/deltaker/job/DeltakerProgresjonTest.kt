package no.nav.amt.deltaker.job

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakerProgresjonTest {
    @Test
    fun `getAvsluttendeStatusUtfall - deltar avbrutt deltakerliste - far riktig status og arsak`() {
        val deltakerliste = TestData.lagDeltakerlisteMedFellesOppstart().copy(status = GjennomforingStatusType.AVBRUTT)
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = deltakerliste.sluttDato,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )

        val oppdatertDeltaker = DeltakerProgresjonHandler().getAvsluttendeStatusUtfall(listOf(deltaker), emptyList()).first()

        oppdatertDeltaker.sluttdato shouldBe LocalDate.now()
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.AVBRUTT
        oppdatertDeltaker.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
    }

    @Test
    fun `getAvsluttendeStatusUtfall - venter avbrutt deltakerliste - far riktig status og arsak`() {
        val deltakerliste = TestData.lagDeltakerlisteMedFellesOppstart().copy(status = GjennomforingStatusType.AVBRUTT)
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = deltakerliste.sluttDato,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )

        val oppdatertDeltaker = DeltakerProgresjonHandler().getAvsluttendeStatusUtfall(listOf(deltaker), emptyList()).first()

        oppdatertDeltaker.startdato shouldBe null
        oppdatertDeltaker.sluttdato shouldBe null
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
        oppdatertDeltaker.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
    }
}
