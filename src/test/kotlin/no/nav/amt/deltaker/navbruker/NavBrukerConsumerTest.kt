package no.nav.amt.deltaker.navbruker

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class NavBrukerConsumerTest {
    companion object {
        lateinit var navBrukerRepository: NavBrukerRepository
        lateinit var navEnhetRepository: NavEnhetRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
            navBrukerRepository = NavBrukerRepository()
            navEnhetRepository = NavEnhetRepository()
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `consumeNavBruker - ny navBruker - upserter`() {
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navBruker = TestData.lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        val navBrukerConsumer = NavBrukerConsumer(
            navBrukerRepository,
            NavEnhetService(navEnhetRepository, mockAmtPersonClient()),
        )

        runBlocking {
            navBrukerConsumer.consume(
                navBruker.personId,
                objectMapper.writeValueAsString(TestData.lagNavBrukerDto(navBruker, navEnhet)),
            )
        }

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe navBruker
    }

    @Test
    fun `consumeNavBruker - oppdatert navBruker - upserter`() {
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)

        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navBruker = TestData.lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        navBrukerRepository.upsert(navBruker)

        val oppdatertNavBruker = navBruker.copy(fornavn = "Oppdatert NavBruker")

        val navBrukerConsumer = NavBrukerConsumer(
            navBrukerRepository,
            NavEnhetService(navEnhetRepository, mockAmtPersonClient()),
        )

        runBlocking {
            navBrukerConsumer.consume(
                navBruker.personId,
                objectMapper.writeValueAsString(TestData.lagNavBrukerDto(oppdatertNavBruker, navEnhet)),
            )
        }

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe oppdatertNavBruker
    }

    @Test
    fun `consumeNavBruker - ny navBruker, enhet mangler - henter enhet og lagrer`() {
        val navEnhet = TestData.lagNavEnhet()
        MockResponseHandler.addNavEnhetResponse(navEnhet)

        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navBruker = TestData.lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        val navBrukerConsumer = NavBrukerConsumer(
            navBrukerRepository,
            NavEnhetService(navEnhetRepository, mockAmtPersonClient()),
        )

        runBlocking {
            navBrukerConsumer.consume(
                navBruker.personId,
                objectMapper.writeValueAsString(TestData.lagNavBrukerDto(navBruker, navEnhet)),
            )
        }

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe navBruker
    }
}
