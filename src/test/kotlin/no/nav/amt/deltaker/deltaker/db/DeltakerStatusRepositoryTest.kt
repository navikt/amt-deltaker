package no.nav.amt.deltaker.deltaker.db

import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerStatusRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `slettTidligereStatuser - skal slette alle andre statuser`() {
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

        DeltakerStatusRepository.deaktiverTidligereStatuser(deltaker.id, nyStatus.id)

        val opprinneligStatus = DeltakerStatusRepository.get(deltaker.status.id)
        opprinneligStatus.gyldigTil.shouldNotBeNull()
    }
}
