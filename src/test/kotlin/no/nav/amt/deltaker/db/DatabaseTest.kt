package no.nav.amt.deltaker.db

import io.kotest.matchers.shouldBe
import org.junit.Test

class DatabaseTest {

    @Test
    fun `toPGObject - value er null - skal skrive null ikke en string`() {
        val result = toPGObject(null)
        result.isNull shouldBe true
        result.value shouldBe null
    }
}
