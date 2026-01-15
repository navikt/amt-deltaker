package no.nav.amt.deltaker.job

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusMedDeltakerId
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerProgresjonTest {
    val deltakerRepository = mockk<DeltakerRepository>()
    val deltakerProgresjonHandler = DeltakerProgresjonHandler(deltakerRepository)

    @Test
    fun `getAvsluttendeStatusUtfall - deltar avbrutt deltakerliste - far riktig status og arsak`() {
        val deltakerliste = TestData.lagDeltakerlisteMedFellesOppstart().copy(status = GjennomforingStatusType.AVBRUTT)
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = deltakerliste.sluttDato,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )

        every { deltakerRepository.getAvsluttendeDeltakerStatuserForOppdatering(any()) } returns emptyList()
        val oppdatertDeltaker = deltakerProgresjonHandler
            .getAvsluttendeStatusUtfall(listOf(deltaker))
            .first()

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
        every { deltakerRepository.getAvsluttendeDeltakerStatuserForOppdatering(any()) } returns emptyList()

        val oppdatertDeltaker = deltakerProgresjonHandler
            .getAvsluttendeStatusUtfall(listOf(deltaker))
            .first()

        oppdatertDeltaker.startdato shouldBe null
        oppdatertDeltaker.sluttdato shouldBe null
        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
        oppdatertDeltaker.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
    }

    @Test
    fun `getAvsluttendeStatusUtfall - fremtig avsluttende status - returnerer deltaker med neste status`() {
        val deltakerliste = TestData.lagDeltakerlisteMedLopendeOppstart().copy(status = GjennomforingStatusType.AVBRUTT)
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = deltakerliste.sluttDato,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val fremtidigStatus =
            DeltakerStatus(
                UUID.randomUUID(),
                DeltakerStatus.Type.HAR_SLUTTET,
                aarsak = null,
                gyldigFra = LocalDateTime.now().minusHours(1),
                gyldigTil = null,
                opprettet = LocalDateTime.now().minusDays(2),
            )
        every { deltakerRepository.getAvsluttendeDeltakerStatuserForOppdatering(any()) } returns listOf(
            DeltakerStatusMedDeltakerId(fremtidigStatus, deltaker.id),
        )

        val oppdatertDeltaker = deltakerProgresjonHandler
            .getAvsluttendeStatusUtfall(listOf(deltaker))
            .first()

        oppdatertDeltaker.startdato shouldBe deltaker.startdato
        oppdatertDeltaker.sluttdato shouldBe LocalDate.now()
        oppdatertDeltaker.status shouldBe fremtidigStatus
    }
}
