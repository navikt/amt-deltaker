package no.nav.amt.deltaker.navansatt

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import org.junit.BeforeClass
import org.junit.Test

class NavAnsattRepositoryTest {
    companion object {
        lateinit var repository: NavAnsattRepository

        @JvmStatic
        @BeforeClass
        fun setup() {
            SingletonPostgresContainer.start()
            repository = NavAnsattRepository()
        }
    }

    @Test
    fun `getMany - flere navidenter - returnerer flere ansatte`() {
        val ansatte = listOf(
            TestData.lagNavAnsatt(),
            TestData.lagNavAnsatt(),
            TestData.lagNavAnsatt(),
        )
        ansatte.forEach { TestRepository.insert(it) }

        val faktiskeAnsatte = repository.getMany(ansatte.map { it.navIdent })

        faktiskeAnsatte.size shouldBe ansatte.size
        faktiskeAnsatte.find { it == ansatte[0] } shouldNotBe null
        faktiskeAnsatte.find { it == ansatte[1] } shouldNotBe null
        faktiskeAnsatte.find { it == ansatte[2] } shouldNotBe null
    }
}
