package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.db.sammenlignDeltakereVedVedtak
import no.nav.amt.deltaker.kafka.utils.sammenlignForslagStatus
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.testing.shouldBeCloseTo

object DeltakerTestUtils {
    fun sammenlignHistorikk(first: DeltakerHistorikk, second: DeltakerHistorikk) {
        when (first) {
            is DeltakerHistorikk.Endring -> {
                second as DeltakerHistorikk.Endring
                first.endring.id shouldBe second.endring.id
                first.endring.endring shouldBe second.endring.endring
                first.endring.endretAv shouldBe second.endring.endretAv
                first.endring.endretAvEnhet shouldBe second.endring.endretAvEnhet
                first.endring.endret shouldBeCloseTo second.endring.endret
            }

            is DeltakerHistorikk.Vedtak -> {
                second as DeltakerHistorikk.Vedtak
                first.vedtak.id shouldBe second.vedtak.id
                first.vedtak.deltakerId shouldBe second.vedtak.deltakerId
                first.vedtak.fattet shouldBeCloseTo second.vedtak.fattet
                first.vedtak.gyldigTil shouldBeCloseTo second.vedtak.gyldigTil
                sammenlignDeltakereVedVedtak(first.vedtak.deltakerVedVedtak, second.vedtak.deltakerVedVedtak)
                first.vedtak.opprettetAv shouldBe second.vedtak.opprettetAv
                first.vedtak.opprettetAvEnhet shouldBe second.vedtak.opprettetAvEnhet
                first.vedtak.opprettet shouldBeCloseTo second.vedtak.opprettet
            }

            is DeltakerHistorikk.Forslag -> {
                second as DeltakerHistorikk.Forslag
                first.forslag.id shouldBe second.forslag.id
                first.forslag.deltakerId shouldBe second.forslag.deltakerId
                first.forslag.opprettet shouldBeCloseTo second.forslag.opprettet
                first.forslag.begrunnelse shouldBe second.forslag.begrunnelse
                first.forslag.opprettetAvArrangorAnsattId shouldBe second.forslag.opprettetAvArrangorAnsattId
                first.forslag.endring shouldBe second.forslag.endring
                sammenlignForslagStatus(first.forslag.status, second.forslag.status)
            }

            is DeltakerHistorikk.EndringFraArrangor -> {
                second as DeltakerHistorikk.EndringFraArrangor
                first.endringFraArrangor.id shouldBe second.endringFraArrangor.id
                first.endringFraArrangor.deltakerId shouldBe second.endringFraArrangor.deltakerId
                first.endringFraArrangor.opprettet shouldBeCloseTo second.endringFraArrangor.opprettet
                first.endringFraArrangor.opprettetAvArrangorAnsattId shouldBe second.endringFraArrangor.opprettetAvArrangorAnsattId
                first.endringFraArrangor.endring shouldBe second.endringFraArrangor.endring
            }

            is DeltakerHistorikk.ImportertFraArena -> {
                second as DeltakerHistorikk.ImportertFraArena
                first.importertFraArena.deltakerId shouldBe second.importertFraArena.deltakerId
                first.importertFraArena.importertDato shouldBeCloseTo second.importertFraArena.importertDato
                first.importertFraArena.deltakerVedImport shouldBe second.importertFraArena.deltakerVedImport
            }

            is DeltakerHistorikk.VurderingFraArrangor -> {
                second as DeltakerHistorikk.VurderingFraArrangor
                first.data.begrunnelse shouldBe second.data.begrunnelse
                first.data.vurderingstype shouldBe second.data.vurderingstype
                first.data.deltakerId shouldBe second.data.deltakerId
                first.data.id shouldBe second.data.id
                first.data.opprettetAvArrangorAnsattId shouldBe second.data.opprettetAvArrangorAnsattId
            }

            is DeltakerHistorikk.EndringFraTiltakskoordinator -> {
                second as DeltakerHistorikk.EndringFraTiltakskoordinator
                first.endringFraTiltakskoordinator.id shouldBe second.endringFraTiltakskoordinator.id
                first.endringFraTiltakskoordinator.deltakerId shouldBe second.endringFraTiltakskoordinator.deltakerId
                first.endringFraTiltakskoordinator.endring shouldBe second.endringFraTiltakskoordinator.endring
                first.endringFraTiltakskoordinator.endretAv shouldBe second.endringFraTiltakskoordinator.endretAv
                first.endringFraTiltakskoordinator.endret shouldBeCloseTo second.endringFraTiltakskoordinator.endret
            }

            is DeltakerHistorikk.InnsokPaaFellesOppstart -> {
                second as DeltakerHistorikk.InnsokPaaFellesOppstart
                first.data.id shouldBe second.data.id
                first.data.deltakerId shouldBe second.data.deltakerId
                first.data.deltakelsesinnholdVedInnsok shouldBe second.data.deltakelsesinnholdVedInnsok
                first.data.innsokt shouldBeCloseTo second.data.innsokt
                first.data.innsoktAv shouldBe second.data.innsoktAv
                first.data.innsoktAvEnhet shouldBe second.data.innsoktAvEnhet
                first.data.utkastDelt shouldBeCloseTo second.data.utkastDelt
                first.data.utkastGodkjentAvNav shouldBe second.data.utkastGodkjentAvNav
            }
        }
    }
}
