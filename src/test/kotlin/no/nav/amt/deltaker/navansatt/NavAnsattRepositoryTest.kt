package no.nav.amt.deltaker.navansatt

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NavAnsattRepositoryTest {
    private val navAnsattRepository = NavAnsattRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `getMany - flere navidenter - returnerer flere ansatte`() {
        val ansatte = listOf(
            TestData.lagNavAnsatt(),
            TestData.lagNavAnsatt(),
            TestData.lagNavAnsatt(),
        )
        ansatte.forEach { TestRepository.insert(it) }

        val faktiskeAnsatte = navAnsattRepository.getMany(ansatte.map { it.navIdent })

        faktiskeAnsatte.size shouldBe ansatte.size
        faktiskeAnsatte.find { it == ansatte[0] } shouldNotBe null
        faktiskeAnsatte.find { it == ansatte[1] } shouldNotBe null
        faktiskeAnsatte.find { it == ansatte[2] } shouldNotBe null
    }

    @Test
    fun `slettNavAnsatt - navansatt blir slettet`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)

        navAnsattRepository.delete(navAnsatt.id)

        navAnsattRepository.get(navAnsatt.id) shouldBe null
    }
}
