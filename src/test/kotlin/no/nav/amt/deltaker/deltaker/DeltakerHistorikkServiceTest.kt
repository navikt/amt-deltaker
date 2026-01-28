package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.DatabaseTestExtension
import no.nav.amt.deltaker.deltaker.DeltakerTestUtils.sammenlignHistorikk
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.extensions.getInnsoktDato
import no.nav.amt.deltaker.deltaker.extensions.toVurderingFraArrangorData
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerHistorikkServiceTest {
    private val deltakerHistorikkService = DeltakerHistorikkService(
        DeltakerEndringRepository(),
        VedtakRepository(),
        ForslagRepository(),
        EndringFraArrangorRepository(),
        ImportertFraArenaRepository(),
        InnsokPaaFellesOppstartRepository(),
        EndringFraTiltakskoordinatorRepository(),
        VurderingRepository(),
    )

    @Test
    fun `getForDeltaker - ett vedtak flere endringer og forslag - returner liste riktig sortert`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val deltaker = TestData.lagDeltaker()
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now().minusMonths(1),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            sistEndret = LocalDateTime.now().minusMonths(1),
        )
        val gammelEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endret = LocalDateTime.now().minusDays(20),
        )
        val endringFraArrangor = TestData.lagEndringFraArrangor(
            deltakerId = deltaker.id,
            opprettet = LocalDateTime.now().minusDays(18),
        )
        val forslag = TestData.lagForslag(
            deltakerId = deltaker.id,
            status = Forslag.Status.Tilbakekalt(
                tilbakekaltAvArrangorAnsattId = UUID.randomUUID(),
                tilbakekalt = LocalDateTime.now().minusDays(15),
            ),
        )
        val forslagVenter = TestData.lagForslag(deltakerId = deltaker.id)
        val nyEndring = TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endret = LocalDateTime.now().minusDays(13),
        )
        val nyVurdering = TestData.lagVurdering(
            deltakerId = deltaker.id,
            gyldigFra = LocalDateTime.now().minusDays(10),
        )

        val ikkeFattetVedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = null,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            sistEndret = LocalDateTime.now().minusDays(4),
        )

        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)
        TestRepository.insert(ikkeFattetVedtak)
        TestRepository.insert(gammelEndring)
        TestRepository.insert(endringFraArrangor)
        TestRepository.insert(nyEndring)
        TestRepository.insert(forslag)
        TestRepository.insert(forslagVenter)
        TestRepository.insert(nyVurdering)

        val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id)

        historikk.size shouldBe 6
        sammenlignHistorikk(historikk[0], DeltakerHistorikk.VurderingFraArrangor(nyVurdering.toVurderingFraArrangorData()))
        sammenlignHistorikk(historikk[1], DeltakerHistorikk.Endring(nyEndring))
        sammenlignHistorikk(historikk[2], DeltakerHistorikk.Forslag(forslag))
        sammenlignHistorikk(historikk[3], DeltakerHistorikk.EndringFraArrangor(endringFraArrangor))
        sammenlignHistorikk(historikk[4], DeltakerHistorikk.Endring(gammelEndring))
        sammenlignHistorikk(historikk[5], DeltakerHistorikk.Vedtak(vedtak))
    }

    @Test
    fun `getForDeltaker - ingen endringer - returner tom liste`() {
        val deltaker = TestData.lagDeltaker()
        TestRepository.insert(deltaker)

        deltakerHistorikkService.getForDeltaker(deltaker.id) shouldBe emptyList()
    }

    @Test
    fun `getInnsoktDato - ingen vedtak - returnerer null`() {
        val deltakerhistorikk = listOf<DeltakerHistorikk>(DeltakerHistorikk.Endring(TestData.lagDeltakerEndring()))

        deltakerhistorikk.getInnsoktDato() shouldBe null
    }

    @Test
    fun `getInnsoktDato - to vedtak - returnerer tidligste opprettetdato`() {
        val deltakerhistorikk = listOf(
            DeltakerHistorikk.Endring(TestData.lagDeltakerEndring()),
            DeltakerHistorikk.Vedtak(
                TestData.lagVedtak(
                    opprettet = LocalDateTime.now().minusMonths(1),
                ),
            ),
            DeltakerHistorikk.Vedtak(
                TestData.lagVedtak(
                    opprettet = LocalDateTime.now().minusDays(4),
                ),
            ),
        )

        deltakerhistorikk.getInnsoktDato() shouldBe LocalDate.now().minusMonths(1)
    }

    @Test
    fun `getInnsoktDato - importert arenadeltaker - returnerer riktig dato`() {
        val innsoktDato = LocalDate.now().minusMonths(1)
        val deltakerhistorikk = listOf(
            DeltakerHistorikk.Endring(TestData.lagDeltakerEndring()),
            DeltakerHistorikk.ImportertFraArena(
                importertFraArena = ImportertFraArena(
                    deltakerId = UUID.randomUUID(),
                    importertDato = LocalDateTime.now(),
                    deltakerVedImport = TestData.lagDeltaker().toDeltakerVedImport(innsoktDato = innsoktDato),
                ),
            ),
        )

        deltakerhistorikk.getInnsoktDato() shouldBe innsoktDato
    }

    @Test
    fun `getInnsoktDato - har innsok - returnerer riktig dato`() {
        val innsoktDato = LocalDate.now().minusMonths(1)
        val deltakerhistorikk = listOf(
            DeltakerHistorikk.Endring(TestData.lagDeltakerEndring()),
            DeltakerHistorikk.InnsokPaaFellesOppstart(
                InnsokPaaFellesOppstart(
                    id = UUID.randomUUID(),
                    deltakerId = UUID.randomUUID(),
                    innsokt = innsoktDato.atStartOfDay(),
                    innsoktAv = UUID.randomUUID(),
                    innsoktAvEnhet = UUID.randomUUID(),
                    deltakelsesinnholdVedInnsok = null,
                    utkastDelt = null,
                    utkastGodkjentAvNav = true,
                ),
            ),
        )

        deltakerhistorikk.getInnsoktDato() shouldBe innsoktDato
    }

    companion object {
        @JvmField
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }
}
