package no.nav.amt.deltaker.job

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusMedDeltakerId
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerProgresjonTest {
    @BeforeEach
    fun setup() = mockkObject(DeltakerStatusRepository)

    @AfterEach
    fun teardown() = unmockkObject(DeltakerStatusRepository)

    @Test
    fun `getAvsluttendeStatusUtfall - deltar avbrutt deltakerliste - far riktig status og arsak`() {
        val deltakerliste = TestData.lagDeltakerlisteMedFellesOppstart().copy(status = GjennomforingStatusType.AVBRUTT)
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = deltakerliste.sluttDato,
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
        )

        every { DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(any()) } returns emptyList()

        val oppdatertDeltaker = DeltakerProgresjonHandler
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

        every { DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(any()) } returns emptyList()

        val oppdatertDeltaker = DeltakerProgresjonHandler
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

        every { DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(any()) } returns listOf(
            DeltakerStatusMedDeltakerId(fremtidigStatus, deltaker.id),
        )

        val oppdatertDeltaker = DeltakerProgresjonHandler
            .getAvsluttendeStatusUtfall(listOf(deltaker))
            .first()

        oppdatertDeltaker.startdato shouldBe deltaker.startdato
        oppdatertDeltaker.sluttdato shouldBe LocalDate.now()
        oppdatertDeltaker.status shouldBe fremtidigStatus
    }
}
