package no.nav.amt.deltaker.navbruker

import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.DatabaseTestExtension
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.utils.MockResponseHandler
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockPersonServiceClient
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NavBrukerConsumerTest {
    private val navBrukerRepository = NavBrukerRepository()
    private val navEnhetRepository = NavEnhetRepository()
    private val deltakerService = mockk<DeltakerService>(relaxed = true)

    companion object {
        @JvmField
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() = clearMocks(deltakerService)

    @Test
    fun `consumeNavBruker - ny navBruker - upserter`() {
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navBruker = TestData.lagNavBruker(navEnhetId = navEnhet.id, navVeilederId = navAnsatt.id)
        val navBrukerConsumer = NavBrukerConsumer(
            navBrukerRepository,
            NavEnhetService(navEnhetRepository, mockPersonServiceClient()),
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
            NavEnhetService(navEnhetRepository, mockPersonServiceClient()),
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
            NavEnhetService(navEnhetRepository, mockPersonServiceClient()),
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
            NavEnhetService(navEnhetRepository, mockPersonServiceClient()),
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
            NavEnhetService(navEnhetRepository, mockPersonServiceClient()),
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
