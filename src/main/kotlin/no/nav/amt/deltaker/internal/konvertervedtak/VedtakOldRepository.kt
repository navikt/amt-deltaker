package no.nav.amt.deltaker.internal.konvertervedtak

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.db.Database
import no.nav.amt.deltaker.db.toPGObject
import no.nav.amt.deltaker.deltaker.model.Vedtak
import no.nav.amt.deltaker.deltakerliste.tiltakstype.DeltakerRegistreringInnhold

class VedtakOldRepository {
    fun rowMapper(row: Row) = VedtakOld(
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
        ledetekst = row.stringOrNull("innhold")?.let { objectMapper.readValue<DeltakerRegistreringInnhold?>(it)?.ledetekst },
    )

    fun updateDeltakerVedVedtak(vedtak: Vedtak) = Database.query {
        val sql =
            """
            update vedtak
            set deltaker_ved_vedtak = :deltaker_ved_vedtak
            where id = :id
            """.trimIndent()

        val params = mapOf(
            "id" to vedtak.id,
            "deltaker_ved_vedtak" to toPGObject(vedtak.deltakerVedVedtak),
        )

        it.update(queryOf(sql, params))
    }

    fun getAll() = Database.query {
        val query =
            queryOf(
                """
                SELECT vedtak.id as "id",
                       vedtak.deltaker_id as "deltaker_id",
                       vedtak.fattet as "fattet",
                       vedtak.gyldig_til as "gyldig_til",
                       vedtak.deltaker_ved_vedtak as "deltaker_ved_vedtak",
                       vedtak.fattet_av_nav as "fattet_av_nav",
                       vedtak.opprettet_av as "opprettet_av",
                       vedtak.opprettet_av_enhet as "opprettet_av_enhet",
                       vedtak.sist_endret_av as "sist_endret_av",
                       vedtak.sist_endret_av_enhet as "sist_endret_av_enhet",
                       vedtak.created_at as "created_at",
                       vedtak.modified_at as "modified_at",
                       t.innhold as "innhold"
                FROM vedtak
                         INNER JOIN public.deltaker d ON d.id = vedtak.deltaker_id
                         INNER JOIN public.deltakerliste dl ON dl.id = d.deltakerliste_id
                         INNER JOIN public.tiltakstype t ON t.id = dl.tiltakstype_id;
                """.trimIndent(),
            )

        it.run(query.map(::rowMapper).asList)
    }
}
