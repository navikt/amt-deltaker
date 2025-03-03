package no.nav.amt.deltaker.deltaker.vurdering

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

open class VurderingRepository {
    companion object {
        fun rowMapper(row: Row): Vurdering {
            return Vurdering(
                id = row.uuid("id"),
                deltakerId = row.uuid("deltaker_id"),
                vurderingstype = Vurderingstype.valueOf(row.string("vurderingstype")),
                begrunnelse = row.stringOrNull("begrunnelse"),
                opprettetAvArrangorAnsattId = row.uuid("opprettet_av_arrangor_ansatt_id"),
                gyldigFra = row.localDateTime("gyldig_fra"),
            )
        }
    }

    fun getForDeltaker(deltakerId: UUID): List<Vurdering> = Database.query {
        val sql =
            """
            SELECT *
            FROM vurdering
            WHERE deltaker_id = :deltakerId;
            """.trimIndent()

        val parameters = mapOf("deltakerId" to deltakerId)
        val query = queryOf(sql, parameters)

        it.run(query.map(::rowMapper).asList)
    }

    fun upsert(vurdering: Vurdering) = Database.query {
        val sql =
            """
            INSERT INTO vurdering (id, deltaker_id, opprettet_av_arrangor_ansatt_id, vurderingstype, begrunnelse, gyldig_fra)
            VALUES (
                :id, :deltaker_id, :opprettet_av_arrangor_ansatt_id, :vurderingstype, :begrunnelse, :gyldig_fra
            )
            ON CONFLICT (id) DO UPDATE SET
                opprettet_av_arrangor_ansatt_id = :opprettet_av_arrangor_ansatt_id, 
                vurderingstype = :vurderingstype, 
                begrunnelse = :begrunnelse, 
                gyldig_fra = :gyldig_fra
            """.trimIndent()
        val params = mapOf(
            "id" to vurdering.id,
            "deltaker_id" to vurdering.deltakerId,
            "opprettet_av_arrangor_ansatt_id" to vurdering.opprettetAvArrangorAnsattId,
            "vurderingstype" to vurdering.vurderingstype.name,
            "begrunnelse" to vurdering.begrunnelse,
            "gyldig_fra" to vurdering.gyldigFra,
        )
        val query = queryOf(sql, params)
        it.update(query)
    }
}
