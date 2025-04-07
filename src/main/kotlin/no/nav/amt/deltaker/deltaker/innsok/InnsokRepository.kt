package no.nav.amt.deltaker.deltaker.innsok

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.utils.prefixColumn
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.Innsok
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class InnsokRepository {
    companion object {
        fun rowmapper(row: Row, alias: String? = null): Innsok {
            val col = prefixColumn(alias)
            return Innsok(
                id = row.uuid(col("id")),
                deltakerId = row.uuid(col("deltaker_id")),
                innsokt = row.localDateTime(col("innsokt")),
                innsoktAv = row.uuid(col("innsokt_av")),
                innsoktAvEnhet = row.uuid(col("innsokt_av_enhet")),
                utkastGodkjentAvNav = row.boolean(col("utkast_godkjent_av_nav")),
                utkastDelt = row.localDateTimeOrNull(col("utkast_delt")),
                deltakelsesinnhold = objectMapper.readValue(row.string(col("deltakelsesinnhold"))),
            )
        }
    }

    fun insert(innsok: Innsok) = Database.query {
        val sql =
            """
            insert into innsok (id, deltaker_id, innsokt, innsokt_av, innsokt_av_enhet, utkast_godkjent_av_nav, utkast_delt, deltakelsesinnhold) 
            values (:id, :deltaker_id, :innsokt, :innsokt_av, :innsokt_av_enhet, :utkast_godkjent_av_nav, :utkast_delt, :deltakelsesinnhold)
            """.trimIndent()
        val params = mapOf(
            "id" to innsok.id,
            "deltaker_id" to innsok.deltakerId,
            "innsokt" to innsok.innsokt,
            "innsokt_av" to innsok.innsoktAv,
            "innsokt_av_enhet" to innsok.innsoktAvEnhet,
            "utkast_godkjent_av_nav" to innsok.utkastGodkjentAvNav,
            "utkast_delt" to innsok.utkastDelt,
            "deltakelsesinnhold" to toPGObject(innsok.deltakelsesinnhold),
        )

        it.update(queryOf(sql, params))
    }

    fun get(id: UUID) = Database.query {
        val sql =
            """
            select * from innsok inn where id = :id
            """.trimIndent()
        val params = mapOf("id" to id)

        it.run(queryOf(sql, params).map(::rowmapper).asSingle)?.let {
            Result.success(it)
        } ?: Result.failure(NoSuchElementException("Fant ikke innsok med id $id"))
    }

    fun getForDeltaker(deltakerId: UUID) = Database.query {
        val sql =
            """
            select * from innsok inn where deltaker_id = :deltaker_id
            """.trimIndent()
        val params = mapOf("deltaker_id" to deltakerId)

        it.run(queryOf(sql, params).map(::rowmapper).asSingle)?.let {
            Result.success(it)
        } ?: Result.failure(NoSuchElementException("Fant ikke innsok for deltaker $deltakerId"))
    }
}
