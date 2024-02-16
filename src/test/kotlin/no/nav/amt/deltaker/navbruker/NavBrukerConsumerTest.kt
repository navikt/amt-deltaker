package no.nav.amt.deltaker.navbruker

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
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
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navBruker = TestData.lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        val navBrukerConsumer = NavBrukerConsumer(repository)

        runBlocking {
            navBrukerConsumer.consume(navBruker.personId, objectMapper.writeValueAsString(TestData.lagNavBrukerDto(navBruker)))
        }

        repository.get(navBruker.personId).getOrNull() shouldBe navBruker
    }

    @Test
    fun `consumeNavBruker - oppdatert navBruker - upserter`() {
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navBruker = TestData.lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        repository.upsert(navBruker)

        val oppdatertNavBruker = navBruker.copy(fornavn = "Oppdatert NavBruker")

        val navBrukerConsumer = NavBrukerConsumer(repository)

        runBlocking {
            navBrukerConsumer.consume(navBruker.personId, objectMapper.writeValueAsString(TestData.lagNavBrukerDto(oppdatertNavBruker)))
        }

        repository.get(navBruker.personId).getOrNull() shouldBe oppdatertNavBruker
    }
}
