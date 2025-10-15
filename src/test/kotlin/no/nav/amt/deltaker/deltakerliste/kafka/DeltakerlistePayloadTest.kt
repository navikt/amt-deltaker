package no.nav.amt.deltaker.deltakerliste.kafka

import com.fasterxml.jackson.annotation.JsonInclude
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.schema.shouldMatchSchema
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlistePayloadJsonSchemas.deltakerlistePayloadV1Schema
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlistePayloadJsonSchemas.deltakerlistePayloadV2Schema
import no.nav.amt.deltaker.utils.data.TestData.lagArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

class DeltakerlistePayloadTest {
    @Nested
    inner class Organisasjonsnummer {
        @Test
        fun `returnerer virksomhetsnummer for v1`() {
            val payload = DeltakerlistePayload(
                id = deltakerlisteIdInTest,
                tiltakstype = DeltakerlistePayload.Tiltakstype(Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK.name),
                virksomhetsnummer = "123456789",
            )

            payload.organisasjonsnummer shouldBe "123456789"
        }

        @Test
        fun `organisasjonsnummer - kaster feil hvis virksomhetsnummer mangler`() {
            val payload = DeltakerlistePayload(
                id = deltakerlisteIdInTest,
                tiltakstype = DeltakerlistePayload.Tiltakstype(Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK.name),
            )

            assertThrows<IllegalStateException> {
                payload.organisasjonsnummer
            }
        }

        @Test
        fun `returnerer arrangor-organisasjonsnummer for v2`() {
            val payload = DeltakerlistePayload(
                id = deltakerlisteIdInTest,
                type = DeltakerlistePayload.ENKELTPLASS_V2_TYPE,
                tiltakstype = DeltakerlistePayload.Tiltakstype(Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK.name),
                arrangor = DeltakerlistePayload.Arrangor("987654321"),
            )

            payload.organisasjonsnummer shouldBe "987654321"
        }

        @Test
        fun `organisasjonsnummer - kaster feil hvis arrangor-organisasjonsnummer mangler`() {
            val payload = DeltakerlistePayload(
                id = deltakerlisteIdInTest,
                type = DeltakerlistePayload.ENKELTPLASS_V2_TYPE,
                tiltakstype = DeltakerlistePayload.Tiltakstype(Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK.name),
            )

            assertThrows<IllegalStateException> {
                payload.organisasjonsnummer
            }
        }
    }

    @Nested
    inner class ErStottet {
        @Test
        fun `returnerer true for gyldig tiltakskode`() {
            val tiltakstype = DeltakerlistePayload.Tiltakstype(Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK.name)

            tiltakstype.erStottet().shouldBeTrue()
        }

        @Test
        fun `returnerer false for ugyldig tiltakskode`() {
            val tiltakstype = DeltakerlistePayload.Tiltakstype("UGYLDIG_KODE")

            tiltakstype.erStottet().shouldBeFalse()
        }
    }

    @Nested
    inner class ToModel {
        @Test
        fun `toModel - mapper felter korrekt`() {
            val payload = DeltakerlistePayload(
                id = deltakerlisteIdInTest,
                tiltakstype = DeltakerlistePayload.Tiltakstype(Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING.name),
                navn = "Testliste",
                startDato = LocalDate.of(2024, 1, 1),
                sluttDato = LocalDate.of(2024, 6, 1),
                status = "GJENNOMFORES",
                oppstart = Oppstartstype.LOPENDE,
                apentForPamelding = true,
                virksomhetsnummer = "123456789",
            )

            val arrangor = lagArrangor()
            val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING)

            val model = payload.toModel(arrangor, tiltakstype)

            assertSoftly(model) {
                tiltakstype shouldBe tiltakstype
                arrangor shouldBe arrangor

                id shouldBe id
                navn shouldBe "Testliste"
                startDato shouldBe LocalDate.of(2024, 1, 1)
                sluttDato shouldBe LocalDate.of(2024, 6, 1)
                status shouldBe Deltakerliste.Status.GJENNOMFORES
                oppstart shouldBe Oppstartstype.LOPENDE
                apentForPamelding.shouldBeTrue()
            }
        }

        @Test
        fun `toModel - bruker tiltakstype-navn hvis navn er null`() {
            val payload = DeltakerlistePayload(
                id = deltakerlisteIdInTest,
                tiltakstype = DeltakerlistePayload.Tiltakstype(Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING.name),
                virksomhetsnummer = "123456789",
            )

            val arrangor = lagArrangor()
            val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING)

            val model = payload.toModel(arrangor, tiltakstype)

            model.navn shouldBe "Test tiltak ENKFAGYRKE"
            model.status shouldBe null
        }
    }

    @Nested
    inner class Validate {
        @Test
        fun `fullt populert V2 skal matche skjema`() {
            val json = objectMapper
                .copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .writeValueAsString(fullyPopulatedV2PayloadInTest.copy())

            json.shouldMatchSchema(deltakerlistePayloadV2Schema)
        }

        @Test
        fun `fullt populert V1 skal matche skjema`() {
            val json = objectMapper
                .copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .writeValueAsString(fullyPopulatedV1PayloadInTest.copy())

            json.shouldMatchSchema(deltakerlistePayloadV1Schema)
        }
    }

    companion object {
        private val deltakerlisteIdInTest = UUID.randomUUID()

        private val fullyPopulatedV1PayloadInTest = DeltakerlistePayload(
            id = deltakerlisteIdInTest,
            tiltakstype = DeltakerlistePayload.Tiltakstype(Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING.name),
            navn = "Testliste",
            startDato = LocalDate.of(2024, 1, 1),
            sluttDato = LocalDate.of(2024, 6, 1),
            status = "GJENNOMFORES",
            oppstart = Oppstartstype.LOPENDE,
            apentForPamelding = true,
            virksomhetsnummer = "123456789",
        )

        private val fullyPopulatedV2PayloadInTest = DeltakerlistePayload(
            type = DeltakerlistePayload.ENKELTPLASS_V2_TYPE,
            id = deltakerlisteIdInTest,
            tiltakstype = DeltakerlistePayload.Tiltakstype(Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING.name),
            navn = "Testliste",
            startDato = LocalDate.of(2024, 1, 1),
            sluttDato = LocalDate.of(2024, 6, 1),
            status = "GJENNOMFORES",
            oppstart = Oppstartstype.LOPENDE,
            apentForPamelding = true,
            arrangor = DeltakerlistePayload.Arrangor("987654321"),
        )
    }
}
