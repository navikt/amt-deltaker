package no.nav.amt.deltaker.navenhet

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class NavEnhetRepositoryTest {
    companion object {
        lateinit var repository: NavEnhetRepository

        @JvmStatic
        @BeforeAll
        fun setup() {
            SingletonPostgres16Container
            repository = NavEnhetRepository()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `upsert - ny nav enhet - inserter`() {
        val navEnhet = TestData.lagNavEnhet()

        val result = repository.upsert(navEnhet)

        result shouldBe navEnhet
        repository.get(navEnhet.id) shouldBe navEnhet
    }

    @Test
    fun `upsert - eksisterende nav enhet - oppdaterer`() {
        val navEnhet = TestData.lagNavEnhet()
        repository.upsert(navEnhet)

        val oppdatertNavEnhet = navEnhet.copy(
            navn = "Oppdatert NAV Enhet",
            enhetsnummer = "9999",
        )
        val result = repository.upsert(oppdatertNavEnhet)

        result shouldBe oppdatertNavEnhet
        repository.get(navEnhet.id) shouldBe oppdatertNavEnhet
    }

    @Test
    fun `get by enhetsnummer - eksisterende enhet - returnerer enhet`() {
        val navEnhet = TestData.lagNavEnhet(enhetsnummer = "1234", navn = "NAV Test")
        repository.upsert(navEnhet)

        val result = repository.get("1234")

        result shouldBe navEnhet
    }

    @Test
    fun `get by enhetsnummer - ikke eksisterende enhet - returnerer null`() {
        val result = repository.get("9999")

        result shouldBe null
    }

    @Test
    fun `get by id - eksisterende enhet - returnerer enhet`() {
        val navEnhet = TestData.lagNavEnhet()
        repository.upsert(navEnhet)

        val result = repository.get(navEnhet.id)

        result shouldBe navEnhet
    }

    @Test
    fun `get by id - ikke eksisterende enhet - returnerer null`() {
        val result = repository.get(UUID.randomUUID())

        result shouldBe null
    }

    @Test
    fun `getMany - flere nav enheter - returnerer alle enheter`() {
        val navEnheter = listOf(
            TestData.lagNavEnhet(enhetsnummer = "1111", navn = "NAV En"),
            TestData.lagNavEnhet(enhetsnummer = "2222", navn = "NAV To"),
            TestData.lagNavEnhet(enhetsnummer = "3333", navn = "NAV Tre"),
        )
        navEnheter.forEach { repository.upsert(it) }

        val result = repository.getMany(navEnheter.map { it.id }.toSet())

        result.size shouldBe navEnheter.size
        result.find { it == navEnheter[0] } shouldNotBe null
        result.find { it == navEnheter[1] } shouldNotBe null
        result.find { it == navEnheter[2] } shouldNotBe null
    }

    @Test
    fun `getMany - delvis eksisterende enheter - returnerer kun eksisterende`() {
        val eksisterendeNavEnhet = TestData.lagNavEnhet(enhetsnummer = "1234", navn = "NAV Eksisterende")
        repository.upsert(eksisterendeNavEnhet)
        val ikkeEksisterendeId = UUID.randomUUID()

        val result = repository.getMany(setOf(eksisterendeNavEnhet.id, ikkeEksisterendeId))

        result.size shouldBe 1
        result[0] shouldBe eksisterendeNavEnhet
    }

    @Test
    fun `getMany - tom liste - returnerer tom liste`() {
        val result = repository.getMany(emptySet())

        result.size shouldBe 0
    }

    @Test
    fun `getMany - ingen eksisterende enheter - returnerer tom liste`() {
        val ikkeEksisterendeIder = setOf(UUID.randomUUID(), UUID.randomUUID())

        val result = repository.getMany(ikkeEksisterendeIder)

        result.size shouldBe 0
    }
}
