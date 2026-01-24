package no.nav.amt.deltaker

import no.nav.amt.deltaker.utils.data.TestRepository
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class DatabaseTestExtension :
    BeforeAllCallback,
    BeforeEachCallback {
    override fun beforeAll(context: ExtensionContext) = TestPostgres.bootstrap()

    override fun beforeEach(context: ExtensionContext) = TestRepository.cleanDatabase()
}
