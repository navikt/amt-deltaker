package no.nav.amt.deltaker.vurdering

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.arrangormelding.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.BeforeClass
import org.junit.Test

class VurderingRepositoryTest {
    companion object {
        lateinit var repository: VurderingRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgres16Container
            repository = VurderingRepository()
        }
    }

    @Test
    fun `upsert - ny vurdering - inserter`() {
        val deltaker = TestData.lagDeltaker()
        val vurdering = TestData.lagVurdering(deltakerId = deltaker.id)
        TestRepository.insert(deltaker)

        repository.upsert(vurdering)

        repository.getForDeltaker(vurdering.deltakerId).size shouldBe 1
    }
}
