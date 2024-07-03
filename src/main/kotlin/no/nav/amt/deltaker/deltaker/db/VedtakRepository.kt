package no.nav.amt.deltaker.deltaker.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.db.Database
import no.nav.amt.deltaker.db.toPGObject
import no.nav.amt.deltaker.deltaker.model.Vedtak
import java.util.UUID

class VedtakRepository {
    fun rowMapper(row: Row) = Vedtak(
        id = row.uuid("id"),
        deltakerId = row.uuid("deltaker_id"),
        fattet = row.localDateTimeOrNull("fattet"),
        gyldigTil = row.localDateTimeOrNull("gyldig_til"),
        deltakerVedVedtak = objectMapper.readValue(row.string("deltaker_ved_vedtak")),
        fattetAvNav = row.boolean("fattet_av_nav"),
        opprettet = row.localDateTime("created_at"),
        opprettetAv = row.uuid("opprettet_av"),
        opprettetAvEnhet = row.uuid("opprettet_av_enhet"),
        sistEndret = row.localDateTime("modified_at"),
        sistEndretAv = row.uuid("sist_endret_av"),
        sistEndretAvEnhet = row.uuid("sist_endret_av_enhet"),
    )

    fun upsert(vedtak: Vedtak) = Database.query {
        val sql =
            """
            insert into vedtak (id,
                                deltaker_id,
                                fattet,
                                gyldig_til,
                                deltaker_ved_vedtak,
                                fattet_av_nav,
                                opprettet_av,
                                opprettet_av_enhet,
                                sist_endret_av,
                                sist_endret_av_enhet)
            values (:id,
                    :deltaker_id,
                    :fattet, 
                    :gyldig_til,
                    :deltaker_ved_vedtak,
                    :fattet_av_nav,
                    :opprettet_av,
                    :opprettet_av_enhet,
                    :sist_endret_av,
                    :sist_endret_av_enhet)
            on conflict (id) do update
                set fattet                = :fattet,
                    gyldig_til            = :gyldig_til,
                    deltaker_ved_vedtak   = :deltaker_ved_vedtak,
                    fattet_av_nav         = :fattet_av_nav,
                    modified_at           = current_timestamp,
                    sist_endret_av        = :sist_endret_av,
                    sist_endret_av_enhet  = :sist_endret_av_enhet
            """.trimIndent()

        val params = mapOf(
            "id" to vedtak.id,
            "deltaker_id" to vedtak.deltakerId,
            "fattet" to vedtak.fattet,
            "gyldig_til" to vedtak.gyldigTil,
            "deltaker_ved_vedtak" to toPGObject(vedtak.deltakerVedVedtak),
            "fattet_av_nav" to vedtak.fattetAvNav,
            "opprettet_av" to vedtak.opprettetAv,
            "opprettet_av_enhet" to vedtak.opprettetAvEnhet,
            "sist_endret_av" to vedtak.sistEndretAv,
            "sist_endret_av_enhet" to vedtak.sistEndretAvEnhet,
        )

        it.update(queryOf(sql, params))
    }

    fun get(id: UUID) = Database.query {
        val query = queryOf("select * from vedtak where id = :id", mapOf("id" to id))

        it.run(query.map(::rowMapper).asSingle)
    }

    fun getIkkeFattet(deltakerId: UUID) = Database.query {
        val sql =
            """
            select * from vedtak
            where deltaker_id = :deltaker_id and fattet is null
            """.trimIndent()

        val query = queryOf(sql, mapOf("deltaker_id" to deltakerId))

        it.run(query.map(::rowMapper).asSingle)
    }

    fun getForDeltaker(deltakerId: UUID) = Database.query {
        val query = queryOf(
            "select * from vedtak where deltaker_id = :deltaker_id order by created_at",
            mapOf("deltaker_id" to deltakerId),
        )
        it.run(query.map(::rowMapper).asList)
    }

    fun getAll() = Database.query {
        val query = queryOf("select * from vedtak")

        it.run(query.map(::rowMapper).asList)
    }
}
