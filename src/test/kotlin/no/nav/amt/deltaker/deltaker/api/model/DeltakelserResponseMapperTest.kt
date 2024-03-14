package no.nav.amt.deltaker.deltaker.api.model

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.deltaker.utils.SingletonPostgresContainer
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakelserResponseMapperTest {
    companion object {
        private val deltakerHistorikkService = DeltakerHistorikkService(
            DeltakerEndringRepository(),
            VedtakRepository(),
        )
        private val arrangorService = ArrangorService(ArrangorRepository(), mockk())
        private val deltakelserResponseMapper = DeltakelserResponseMapper(deltakerHistorikkService, arrangorService)

        @BeforeClass
        @JvmStatic
        fun setup() {
            SingletonPostgresContainer.start()
        }
    }

    @Before
    fun cleanDatabase() {
        TestRepository.cleanDatabase()
    }

    @Test
    fun `toDeltakelserResponse - kladd - returnerer riktig aktiv deltakelse`() {
        val arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null)
        TestRepository.insert(arrangor)

        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                arrangor = arrangor,
                tiltakstype = TestData.lagTiltakstype(type = Tiltakstype.Type.INDOPPFAG, navn = "Oppfølging"),
            ),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.KLADD),
        )
        TestRepository.insert(deltaker)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.historikk.size shouldBe 0
        deltakelserResponse.aktive.size shouldBe 1
        val aktivDeltakelse = deltakelserResponse.aktive.first()
        aktivDeltakelse.deltakerId shouldBe deltaker.id
        aktivDeltakelse.innsoktDato shouldBe null
        aktivDeltakelse.sistEndretdato shouldBe deltaker.sistEndret.toLocalDate()
        aktivDeltakelse.aktivStatus shouldBe AktivDeltakelse.AktivStatusType.KLADD
        aktivDeltakelse.tittel shouldBe "Oppfølging hos Arrangør"
        aktivDeltakelse.tiltakstype shouldBe DeltakelserResponse.Tiltakstype("Oppfølging", Tiltakstype.Type.INDOPPFAG)
    }

    @Test
    fun `toDeltakelserResponse - utkast, har overordnet arrangor - returnerer riktig aktiv deltakelse`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val overordnetArrangor = TestData.lagArrangor(navn = "OVERORDNET ARRANGØR")
        TestRepository.insert(overordnetArrangor)
        val arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = overordnetArrangor.id)
        TestRepository.insert(arrangor)

        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                arrangor = arrangor,
                tiltakstype = TestData.lagTiltakstype(type = Tiltakstype.Type.INDOPPFAG, navn = "Oppfølging"),
            ),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = null,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            opprettet = LocalDateTime.now().minusDays(4),
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.historikk.size shouldBe 0
        deltakelserResponse.aktive.size shouldBe 1
        val aktivDeltakelse = deltakelserResponse.aktive.first()
        aktivDeltakelse.deltakerId shouldBe deltaker.id
        aktivDeltakelse.innsoktDato shouldBe LocalDate.now().minusDays(4)
        aktivDeltakelse.sistEndretdato shouldBe deltaker.sistEndret.toLocalDate()
        aktivDeltakelse.aktivStatus shouldBe AktivDeltakelse.AktivStatusType.UTKAST_TIL_PAMELDING
        aktivDeltakelse.tittel shouldBe "Oppfølging hos Overordnet Arrangør"
        aktivDeltakelse.tiltakstype shouldBe DeltakelserResponse.Tiltakstype("Oppfølging", Tiltakstype.Type.INDOPPFAG)
    }

    @Test
    fun `toDeltakelserResponse - ikke aktuell - returnerer riktig historisk deltakelse`() {
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)
        val arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null)
        TestRepository.insert(arrangor)

        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                arrangor = arrangor,
                tiltakstype = TestData.lagTiltakstype(type = Tiltakstype.Type.INDOPPFAG, navn = "Oppfølging"),
            ),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.IKKE_AKTUELL, aarsak = DeltakerStatus.Aarsak.Type.ANNET, beskrivelse = "flyttet til Spania"),
        )
        val vedtak = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            opprettet = LocalDateTime.now().minusDays(4),
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.aktive.size shouldBe 0
        deltakelserResponse.historikk.size shouldBe 1
        val historiskDeltakelse = deltakelserResponse.historikk.first()
        historiskDeltakelse.deltakerId shouldBe deltaker.id
        historiskDeltakelse.innsoktDato shouldBe LocalDate.now().minusDays(4)
        historiskDeltakelse.periode shouldBe null
        historiskDeltakelse.historiskStatus shouldBe HistoriskDeltakelse.HistoriskStatus(HistoriskDeltakelse.HistoriskStatusType.IKKE_AKTUELL, "flyttet til Spania")
        historiskDeltakelse.tittel shouldBe "Oppfølging hos Arrangør"
        historiskDeltakelse.tiltakstype shouldBe DeltakelserResponse.Tiltakstype("Oppfølging", Tiltakstype.Type.INDOPPFAG)
    }
}
