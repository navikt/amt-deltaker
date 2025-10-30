package no.nav.amt.deltaker.deltaker.innsok

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerContext
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import no.nav.amt.lib.testing.SingletonPostgres16Container
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class InnsokPaaFellesOppstartRepositoryTest {
    init {
        @Suppress("UnusedExpression")
        SingletonPostgres16Container
    }

    val repository = InnsokPaaFellesOppstartRepository()

    @Test
    fun `insert - ny innsok - inserter`() {
        with(DeltakerContext()) {
            medVedtak()
            val innsok = TestData.lagInnsok(deltaker)
            repository.insert(innsok)
            repository.get(innsok.id).isSuccess shouldBe true
        }
    }
}

fun TestData.lagInnsok(
    deltaker: Deltaker = lagDeltaker(),
    id: UUID = UUID.randomUUID(),
    innsokt: LocalDateTime = LocalDateTime.now(),
    innsoktAv: UUID = deltaker.vedtaksinformasjon!!.sistEndretAv,
    innsoktAvEnhet: UUID = deltaker.vedtaksinformasjon!!.sistEndretAvEnhet,
    utkastGodkjentAvNav: Boolean = false,
    utkastDelt: LocalDateTime? = LocalDateTime.now(),
    deltakelsesinnholdVedInnsok: Deltakelsesinnhold? = deltaker.deltakelsesinnhold,
) = InnsokPaaFellesOppstart(
    id,
    deltaker.id,
    innsokt,
    innsoktAv,
    innsoktAvEnhet,
    deltakelsesinnholdVedInnsok,
    utkastDelt,
    utkastGodkjentAvNav,
)
