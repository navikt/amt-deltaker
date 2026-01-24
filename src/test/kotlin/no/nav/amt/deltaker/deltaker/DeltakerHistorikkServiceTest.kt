package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.DatabaseTestExtension
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.db.sammenlignDeltakereVedVedtak
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.extensions.getInnsoktDato
import no.nav.amt.deltaker.deltaker.extensions.toVurderingFraArrangorData
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.kafka.utils.sammenlignForslagStatus
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import no.nav.amt.lib.testing.shouldBeCloseTo
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

    companion object {
        @JvmField
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

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

        historikk.size shouldBe 7
        sammenlignHistorikk(historikk[0], DeltakerHistorikk.Vedtak(ikkeFattetVedtak))
        sammenlignHistorikk(historikk[1], DeltakerHistorikk.VurderingFraArrangor(nyVurdering.toVurderingFraArrangorData()))
        sammenlignHistorikk(historikk[2], DeltakerHistorikk.Endring(nyEndring))
        sammenlignHistorikk(historikk[3], DeltakerHistorikk.Forslag(forslag))
        sammenlignHistorikk(historikk[4], DeltakerHistorikk.EndringFraArrangor(endringFraArrangor))
        sammenlignHistorikk(historikk[5], DeltakerHistorikk.Endring(gammelEndring))
        sammenlignHistorikk(historikk[6], DeltakerHistorikk.Vedtak(vedtak))
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
}

fun sammenlignHistorikk(a: DeltakerHistorikk, b: DeltakerHistorikk) {
    when (a) {
        is DeltakerHistorikk.Endring -> {
            b as DeltakerHistorikk.Endring
            a.endring.id shouldBe b.endring.id
            a.endring.endring shouldBe b.endring.endring
            a.endring.endretAv shouldBe b.endring.endretAv
            a.endring.endretAvEnhet shouldBe b.endring.endretAvEnhet
            a.endring.endret shouldBeCloseTo b.endring.endret
        }

        is DeltakerHistorikk.Vedtak -> {
            b as DeltakerHistorikk.Vedtak
            a.vedtak.id shouldBe b.vedtak.id
            a.vedtak.deltakerId shouldBe b.vedtak.deltakerId
            a.vedtak.fattet shouldBeCloseTo b.vedtak.fattet
            a.vedtak.gyldigTil shouldBeCloseTo b.vedtak.gyldigTil
            sammenlignDeltakereVedVedtak(a.vedtak.deltakerVedVedtak, b.vedtak.deltakerVedVedtak)
            a.vedtak.opprettetAv shouldBe b.vedtak.opprettetAv
            a.vedtak.opprettetAvEnhet shouldBe b.vedtak.opprettetAvEnhet
            a.vedtak.opprettet shouldBeCloseTo b.vedtak.opprettet
        }

        is DeltakerHistorikk.Forslag -> {
            b as DeltakerHistorikk.Forslag
            a.forslag.id shouldBe b.forslag.id
            a.forslag.deltakerId shouldBe b.forslag.deltakerId
            a.forslag.opprettet shouldBeCloseTo b.forslag.opprettet
            a.forslag.begrunnelse shouldBe b.forslag.begrunnelse
            a.forslag.opprettetAvArrangorAnsattId shouldBe b.forslag.opprettetAvArrangorAnsattId
            a.forslag.endring shouldBe b.forslag.endring
            sammenlignForslagStatus(a.forslag.status, b.forslag.status)
        }

        is DeltakerHistorikk.EndringFraArrangor -> {
            b as DeltakerHistorikk.EndringFraArrangor
            a.endringFraArrangor.id shouldBe b.endringFraArrangor.id
            a.endringFraArrangor.deltakerId shouldBe b.endringFraArrangor.deltakerId
            a.endringFraArrangor.opprettet shouldBeCloseTo b.endringFraArrangor.opprettet
            a.endringFraArrangor.opprettetAvArrangorAnsattId shouldBe b.endringFraArrangor.opprettetAvArrangorAnsattId
            a.endringFraArrangor.endring shouldBe b.endringFraArrangor.endring
        }

        is DeltakerHistorikk.ImportertFraArena -> {
            b as DeltakerHistorikk.ImportertFraArena
            a.importertFraArena.deltakerId shouldBe b.importertFraArena.deltakerId
            a.importertFraArena.importertDato shouldBeCloseTo b.importertFraArena.importertDato
            a.importertFraArena.deltakerVedImport shouldBe b.importertFraArena.deltakerVedImport
        }

        is DeltakerHistorikk.VurderingFraArrangor -> {
            b as DeltakerHistorikk.VurderingFraArrangor
            a.data.begrunnelse shouldBe b.data.begrunnelse
            a.data.vurderingstype shouldBe b.data.vurderingstype
            a.data.deltakerId shouldBe b.data.deltakerId
            a.data.id shouldBe b.data.id
            a.data.opprettetAvArrangorAnsattId shouldBe b.data.opprettetAvArrangorAnsattId
        }

        is DeltakerHistorikk.EndringFraTiltakskoordinator -> {
            b as DeltakerHistorikk.EndringFraTiltakskoordinator
            a.endringFraTiltakskoordinator.id shouldBe b.endringFraTiltakskoordinator.id
            a.endringFraTiltakskoordinator.deltakerId shouldBe b.endringFraTiltakskoordinator.deltakerId
            a.endringFraTiltakskoordinator.endring shouldBe b.endringFraTiltakskoordinator.endring
            a.endringFraTiltakskoordinator.endretAv shouldBe b.endringFraTiltakskoordinator.endretAv
            a.endringFraTiltakskoordinator.endret shouldBeCloseTo b.endringFraTiltakskoordinator.endret
        }

        is DeltakerHistorikk.InnsokPaaFellesOppstart -> {
            b as DeltakerHistorikk.InnsokPaaFellesOppstart
            a.data.id shouldBe b.data.id
            a.data.deltakerId shouldBe b.data.deltakerId
            a.data.deltakelsesinnholdVedInnsok shouldBe b.data.deltakelsesinnholdVedInnsok
            a.data.innsokt shouldBeCloseTo b.data.innsokt
            a.data.innsoktAv shouldBe b.data.innsoktAv
            a.data.innsoktAvEnhet shouldBe b.data.innsoktAvEnhet
            a.data.utkastDelt shouldBeCloseTo b.data.utkastDelt
            a.data.utkastGodkjentAvNav shouldBe b.data.utkastGodkjentAvNav
        }
    }
}
