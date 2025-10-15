package no.nav.amt.deltaker.deltakerliste.kafka

import io.kotest.assertions.json.schema.boolean
import io.kotest.assertions.json.schema.jsonSchema
import io.kotest.assertions.json.schema.obj
import io.kotest.assertions.json.schema.string
import io.kotest.matchers.collections.beIn
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlistePayload.Companion.ENKELTPLASS_V2_TYPE
import no.nav.amt.deltaker.deltakerliste.kafka.DeltakerlistePayload.Companion.GRUPPE_V2_TYPE

object DeltakerlistePayloadJsonSchemas {
    val arrangorSchema = jsonSchema {
        obj {
            withProperty("organisasjonsnummer") { string() }
            additionalProperties = false
        }
    }

    val tiltakstypeSchema = jsonSchema {
        obj {
            withProperty("tiltakskode") { string() }
            additionalProperties = false
        }
    }

    val deltakerlistePayloadV2Schema = jsonSchema {
        obj {
            withProperty("type") {
                string { beIn(setOf(ENKELTPLASS_V2_TYPE, GRUPPE_V2_TYPE)) }
            }
            withProperty("id") { string() }
            withProperty("tiltakstype") { tiltakstypeSchema() }
            withProperty("navn", optional = true) { string() }
            withProperty("startDato", optional = true) { string() } // ISO-8601 format
            withProperty("sluttDato", optional = true) { string() }
            withProperty("status", optional = true) { string() }
            withProperty("oppstart", optional = true) { string() }
            withProperty("apentForPamelding") { boolean() }
            withProperty("arrangor") { arrangorSchema() }
            additionalProperties = false
        }
    }

    val deltakerlistePayloadV1Schema = jsonSchema {
        obj {
            withProperty("id") { string() }
            withProperty("tiltakstype") { tiltakstypeSchema() }
            withProperty("navn") { string() }
            withProperty("startDato") { string() } // ISO-8601 format
            withProperty("sluttDato", optional = true) { string() }
            withProperty("status") { string() }
            withProperty("oppstart") { string() }
            withProperty("apentForPamelding") { boolean() }
            withProperty("virksomhetsnummer") { string() }
            additionalProperties = false
        }
    }
}
