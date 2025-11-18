package no.nav.amt.deltaker.deltakerliste.kafka

import io.kotest.assertions.json.schema.boolean
import io.kotest.assertions.json.schema.jsonSchema
import io.kotest.assertions.json.schema.obj
import io.kotest.assertions.json.schema.string

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
            withProperty("id") { string() }
            withProperty("tiltakskode", optional = true) { string() }
            withProperty("tiltakstype", optional = true) { tiltakstypeSchema() }
            withProperty("navn", optional = true) { string() }
            withProperty("startDato", optional = true) { string() } // ISO-8601 format
            withProperty("sluttDato", optional = true) { string() }
            withProperty("status", optional = true) { string() }
            withProperty("oppstart", optional = true) { string() }
            withProperty("apentForPamelding") { boolean() }
            withProperty("oppmoteSted") { string() }
            withProperty("arrangor") { arrangorSchema() }
            additionalProperties = false
        }
    }
}
