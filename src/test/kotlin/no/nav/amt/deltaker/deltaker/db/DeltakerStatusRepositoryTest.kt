package no.nav.amt.deltaker.deltaker.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerStatusRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `deaktiverTidligereStatuser - skal deaktivere tidligere statuser`() {
        val gammelStatus = lagDeltakerStatus(
            statusType = DeltakerStatus.Type.HAR_SLUTTET,
            aarsakType = DeltakerStatus.Aarsak.Type.ANNET,
            gyldigFra = LocalDate.of(2024, 10, 5).atStartOfDay(),
        )

        val nyStatus = lagDeltakerStatus(
            statusType = DeltakerStatus.Type.HAR_SLUTTET,
            gyldigFra = LocalDate.of(2024, 10, 5).atStartOfDay(),
        )

        val deltaker = lagDeltaker(status = gammelStatus)
        TestRepository.insert(deltaker)

        DeltakerStatusRepository.deaktiverTidligereStatuser(
            deltakerId = deltaker.id,
            nyStatusId = nyStatus.id,
        )

        val opprinneligStatus = DeltakerStatusRepository.get(deltaker.status.id)
        opprinneligStatus.gyldigTil.shouldNotBeNull()
    }

    @Test
    fun `slettTidligereFremtidigeStatuser - skal slette fremtidige statuser`() {
        val eksisterendeFremtidigStatus = lagDeltakerStatus(
            statusType = DeltakerStatus.Type.HAR_SLUTTET,
            gyldigFra = LocalDateTime.now().plusDays(1),
        )

        val deltaker = lagDeltaker(status = eksisterendeFremtidigStatus)
        TestRepository.insert(deltaker)

        DeltakerStatusRepository.getFremtidige(deltaker.id).shouldNotBeEmpty()

        val nyFremtidigStatusId = UUID.randomUUID()

        DeltakerStatusRepository.slettTidligereFremtidigeStatuser(
            deltakerId = deltaker.id,
            nyStatusId = nyFremtidigStatusId,
        )

        DeltakerStatusRepository.getFremtidige(deltaker.id).shouldBeEmpty()
    }

    @Test
    fun `slett - skal slette status`() {
        val deltakerStatus = lagDeltakerStatus(DeltakerStatus.Type.DELTAR)

        val deltaker = lagDeltaker(status = deltakerStatus)
        TestRepository.insert(deltaker)

        DeltakerStatusRepository.get(deltakerStatus.id).shouldNotBeNull()

        DeltakerStatusRepository.slettStatus(deltakerId = deltaker.id)

        shouldThrow<NoSuchElementException> {
            DeltakerStatusRepository.get(deltakerStatus.id)
        }
    }
}
