package no.nav.amt.deltaker.navbruker

import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtPersonClient
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NavBrukerConsumerTest {
    companion object {
        lateinit var navBrukerRepository: NavBrukerRepository
        lateinit var navEnhetRepository: NavEnhetRepository
        private val deltakerService = mockk<DeltakerService>(relaxed = true)

        @JvmStatic
        @BeforeAll
        fun setup() {
            SingletonPostgres16Container
            navBrukerRepository = NavBrukerRepository()
            navEnhetRepository = NavEnhetRepository()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
        clearMocks(deltakerService)
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
            deltakerService,
        )

        runBlocking {
            navBrukerConsumer.consume(
                navBruker.personId,
                objectMapper.writeValueAsString(TestData.lagNavBrukerDto(navBruker, navEnhet)),
            )
        }

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe navBruker
        coVerify { deltakerService.produserDeltakereForPerson(navBruker.personident) }
    }

    @Test
    fun `consumeNavBruker - oppdatert navBruker - ulik personident - upserter - publiserer v1 og v2`() {
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)

        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navBruker = TestData.lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        navBrukerRepository.upsert(navBruker)

        val oppdatertNavBruker = navBruker.copy(fornavn = "Oppdatert NavBruker", personident = TestData.randomIdent())

        val navBrukerConsumer = NavBrukerConsumer(
            navBrukerRepository,
            NavEnhetService(navEnhetRepository, mockAmtPersonClient()),
            deltakerService,
        )

        runBlocking {
            navBrukerConsumer.consume(
                navBruker.personId,
                objectMapper.writeValueAsString(TestData.lagNavBrukerDto(oppdatertNavBruker, navEnhet)),
            )
        }

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe oppdatertNavBruker
        coVerify { deltakerService.produserDeltakereForPerson(oppdatertNavBruker.personident, true) }
    }

    @Test
    fun `consumeNavBruker - oppdatert navBruker - lik personident - upserter - publiserer kun v2`() {
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
            deltakerService,
        )

        runBlocking {
            navBrukerConsumer.consume(
                navBruker.personId,
                objectMapper.writeValueAsString(TestData.lagNavBrukerDto(oppdatertNavBruker, navEnhet)),
            )
        }

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe oppdatertNavBruker
        coVerify { deltakerService.produserDeltakereForPerson(oppdatertNavBruker.personident, false) }
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
            deltakerService,
        )

        runBlocking {
            navBrukerConsumer.consume(
                navBruker.personId,
                objectMapper.writeValueAsString(TestData.lagNavBrukerDto(navBruker, navEnhet)),
            )
        }

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe navBruker
        coVerify { deltakerService.produserDeltakereForPerson(navBruker.personident) }
    }

    @Test
    fun `consumeNavBruker - oppdatert navBruker, ingen endringer - republiserer ikke deltakere`() {
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)

        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navBruker = TestData.lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        navBrukerRepository.upsert(navBruker)

        val navBrukerConsumer = NavBrukerConsumer(
            navBrukerRepository,
            NavEnhetService(navEnhetRepository, mockAmtPersonClient()),
            deltakerService,
        )

        runBlocking {
            navBrukerConsumer.consume(
                navBruker.personId,
                objectMapper.writeValueAsString(TestData.lagNavBrukerDto(navBruker, navEnhet)),
            )
        }

        navBrukerRepository.get(navBruker.personId).getOrNull() shouldBe navBruker
        coVerify(exactly = 0) { deltakerService.produserDeltakereForPerson(navBruker.personident) }
    }
}
