package no.nav.amt.deltaker.navbruker

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import org.junit.BeforeClass
import org.junit.Test

class NavBrukerConsumerTest {
    companion object {
        lateinit var repository: NavBrukerRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
            repository = NavBrukerRepository()
        }
    }

    @Test
    fun `consumeNavBruker - ny navBruker - upserter`() {
        val navBruker = TestData.lagNavBruker()
        val navBrukerConsumer = NavBrukerConsumer(repository)

        runBlocking {
            navBrukerConsumer.consume(navBruker.personId, objectMapper.writeValueAsString(navBruker))
        }

        repository.get(navBruker.personId).getOrNull() shouldBe navBruker
    }

    @Test
    fun `consumeNavBruker - oppdatert navBruker - upserter`() {
        val navBruker = TestData.lagNavBruker()
        repository.upsert(navBruker)

        val oppdatertNavBruker = navBruker.copy(fornavn = "Oppdatert NavBruker")

        val navBrukerConsumer = NavBrukerConsumer(repository)

        runBlocking {
            navBrukerConsumer.consume(navBruker.personId, objectMapper.writeValueAsString(oppdatertNavBruker))
        }

        repository.get(navBruker.personId).getOrNull() shouldBe oppdatertNavBruker
    }
}
