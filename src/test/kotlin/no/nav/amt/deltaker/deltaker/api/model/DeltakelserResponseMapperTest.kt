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
        val deltakerKort = deltakelserResponse.aktive.first()
        deltakerKort.deltakerId shouldBe deltaker.id
        deltakerKort.tittel shouldBe "Oppfølging hos Arrangør"
        deltakerKort.tiltakstype shouldBe DeltakelserResponse.Tiltakstype("Oppfølging", Tiltakstype.Type.INDOPPFAG)
        deltakerKort.status.status shouldBe DeltakerStatus.Type.KLADD
        deltakerKort.status.statustekst shouldBe "Kladden er ikke delt"
        deltakerKort.status.aarsak shouldBe null
        deltakerKort.innsoktDato shouldBe null
        deltakerKort.sistEndretdato shouldBe deltaker.sistEndret.toLocalDate()
        deltakerKort.periode shouldBe null
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
        val deltakerKort = deltakelserResponse.aktive.first()
        deltakerKort.deltakerId shouldBe deltaker.id
        deltakerKort.tittel shouldBe "Oppfølging hos Overordnet Arrangør"
        deltakerKort.tiltakstype shouldBe DeltakelserResponse.Tiltakstype("Oppfølging", Tiltakstype.Type.INDOPPFAG)
        deltakerKort.status.status shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
        deltakerKort.status.statustekst shouldBe "Utkastet er delt og venter på godkjenning"
        deltakerKort.status.aarsak shouldBe null
        deltakerKort.innsoktDato shouldBe null
        deltakerKort.sistEndretdato shouldBe deltaker.sistEndret.toLocalDate()
        deltakerKort.periode shouldBe null
    }

    @Test
    fun `toDeltakelserResponse - venter pa oppstart - returnerer riktig aktiv deltakelse`() {
        val arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null)
        TestRepository.insert(arrangor)
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)

        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                arrangor = arrangor,
                tiltakstype = TestData.lagTiltakstype(type = Tiltakstype.Type.INDOPPFAG, navn = "Oppfølging"),
            ),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().plusWeeks(1),
            sluttdato = null,
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

        deltakelserResponse.historikk.size shouldBe 0
        deltakelserResponse.aktive.size shouldBe 1
        val deltakerKort = deltakelserResponse.aktive.first()
        deltakerKort.deltakerId shouldBe deltaker.id
        deltakerKort.tittel shouldBe "Oppfølging hos Arrangør"
        deltakerKort.tiltakstype shouldBe DeltakelserResponse.Tiltakstype("Oppfølging", Tiltakstype.Type.INDOPPFAG)
        deltakerKort.status.status shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        deltakerKort.status.statustekst shouldBe "Venter på oppstart"
        deltakerKort.status.aarsak shouldBe null
        deltakerKort.innsoktDato shouldBe LocalDate.now().minusDays(4)
        deltakerKort.sistEndretdato shouldBe null
        deltakerKort.periode shouldBe Periode(deltaker.startdato, deltaker.sluttdato)
    }

    @Test
    fun `toDeltakelserResponse - deltar - returnerer riktig aktiv deltakelse`() {
        val arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null)
        TestRepository.insert(arrangor)
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(navEnhet)

        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                arrangor = arrangor,
                tiltakstype = TestData.lagTiltakstype(type = Tiltakstype.Type.INDOPPFAG, navn = "Oppfølging"),
            ),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.DELTAR),
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

        deltakelserResponse.historikk.size shouldBe 0
        deltakelserResponse.aktive.size shouldBe 1
        val deltakerKort = deltakelserResponse.aktive.first()
        deltakerKort.deltakerId shouldBe deltaker.id
        deltakerKort.tittel shouldBe "Oppfølging hos Arrangør"
        deltakerKort.tiltakstype shouldBe DeltakelserResponse.Tiltakstype("Oppfølging", Tiltakstype.Type.INDOPPFAG)
        deltakerKort.status.status shouldBe DeltakerStatus.Type.DELTAR
        deltakerKort.status.statustekst shouldBe "Deltar"
        deltakerKort.status.aarsak shouldBe null
        deltakerKort.innsoktDato shouldBe LocalDate.now().minusDays(4)
        deltakerKort.sistEndretdato shouldBe null
        deltakerKort.periode shouldBe Periode(deltaker.startdato, deltaker.sluttdato)
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
            status = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.IKKE_AKTUELL,
                aarsak = DeltakerStatus.Aarsak.Type.ANNET,
                beskrivelse = "flyttet til Spania",
            ),
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
        val deltakerKort = deltakelserResponse.historikk.first()
        deltakerKort.deltakerId shouldBe deltaker.id
        deltakerKort.tittel shouldBe "Oppfølging hos Arrangør"
        deltakerKort.tiltakstype shouldBe DeltakelserResponse.Tiltakstype("Oppfølging", Tiltakstype.Type.INDOPPFAG)
        deltakerKort.status.status shouldBe DeltakerStatus.Type.IKKE_AKTUELL
        deltakerKort.status.statustekst shouldBe "Ikke aktuell"
        deltakerKort.status.aarsak shouldBe "flyttet til Spania"
        deltakerKort.innsoktDato shouldBe LocalDate.now().minusDays(4)
        deltakerKort.sistEndretdato shouldBe null
        deltakerKort.periode shouldBe null
    }

    @Test
    fun `toDeltakelserResponse - har sluttet - returnerer riktig historisk deltakelse`() {
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
            status = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.HAR_SLUTTET,
                aarsak = DeltakerStatus.Aarsak.Type.TRENGER_ANNEN_STOTTE,
                beskrivelse = null,
            ),
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
        val deltakerKort = deltakelserResponse.historikk.first()
        deltakerKort.deltakerId shouldBe deltaker.id
        deltakerKort.tittel shouldBe "Oppfølging hos Arrangør"
        deltakerKort.tiltakstype shouldBe DeltakelserResponse.Tiltakstype("Oppfølging", Tiltakstype.Type.INDOPPFAG)
        deltakerKort.status.status shouldBe DeltakerStatus.Type.HAR_SLUTTET
        deltakerKort.status.statustekst shouldBe "Har sluttet"
        deltakerKort.status.aarsak shouldBe "trenger annen støtte"
        deltakerKort.innsoktDato shouldBe LocalDate.now().minusDays(4)
        deltakerKort.sistEndretdato shouldBe null
        deltakerKort.periode shouldBe Periode(deltaker.startdato, deltaker.sluttdato)
    }

    @Test
    fun `toDeltakelserResponse - avbrutt utkast - returnerer riktig historisk deltakelse`() {
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
            status = TestData.lagDeltakerStatus(
                type = DeltakerStatus.Type.AVBRUTT_UTKAST,
                aarsak = null,
                beskrivelse = null,
            ),
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
        val deltakerKort = deltakelserResponse.historikk.first()
        deltakerKort.deltakerId shouldBe deltaker.id
        deltakerKort.tittel shouldBe "Oppfølging hos Arrangør"
        deltakerKort.tiltakstype shouldBe DeltakelserResponse.Tiltakstype("Oppfølging", Tiltakstype.Type.INDOPPFAG)
        deltakerKort.status.status shouldBe DeltakerStatus.Type.AVBRUTT_UTKAST
        deltakerKort.status.statustekst shouldBe "Avbrutt utkast"
        deltakerKort.status.aarsak shouldBe null
        deltakerKort.innsoktDato shouldBe null
        deltakerKort.sistEndretdato shouldBe deltaker.sistEndret.toLocalDate()
        deltakerKort.periode shouldBe null
    }

    @Test
    fun `toDeltakelserResponse - feilregistrert - returnerer tomme lister`() {
        val arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null)
        TestRepository.insert(arrangor)

        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                arrangor = arrangor,
                tiltakstype = TestData.lagTiltakstype(type = Tiltakstype.Type.INDOPPFAG, navn = "Oppfølging"),
            ),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.FEILREGISTRERT),
        )
        TestRepository.insert(deltaker)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.historikk.size shouldBe 0
        deltakelserResponse.aktive.size shouldBe 0
    }

    @Test
    fun `toDeltakelserResponse - pabegynt registrering - returnerer tomme lister`() {
        val arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null)
        TestRepository.insert(arrangor)

        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                arrangor = arrangor,
                tiltakstype = TestData.lagTiltakstype(type = Tiltakstype.Type.INDOPPFAG, navn = "Oppfølging"),
            ),
            status = TestData.lagDeltakerStatus(type = DeltakerStatus.Type.PABEGYNT_REGISTRERING),
        )
        TestRepository.insert(deltaker)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.historikk.size shouldBe 0
        deltakelserResponse.aktive.size shouldBe 0
    }
}
