package no.nav.amt.deltaker.vurdering

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class VurderingRepositoryTest {
    companion object {
        lateinit var repository: VurderingRepository

        @JvmStatic
        @BeforeAll
        fun setup() {
            @Suppress("UnusedExpression")
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
