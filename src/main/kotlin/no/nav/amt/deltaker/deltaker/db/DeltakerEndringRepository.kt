package no.nav.amt.deltaker.deltaker.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.utils.database.Database
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerEndringRepository {
    private fun rowMapper(row: Row): DeltakerEndring = DeltakerEndring(
        id = row.uuid("de.id"),
        deltakerId = row.uuid("de.deltaker_id"),
        endring = objectMapper.readValue(row.string("de.endring")),
        endretAv = row.uuid("de.endret_av"),
        endretAvEnhet = row.uuid("de.endret_av_enhet"),
        endret = row.localDateTime("de.endret"),
        forslag = row.uuidOrNull("f.id")?.let { ForslagRepository.rowMapper(row) },
    )

    fun upsert(deltakerEndring: DeltakerEndring, behandlet: LocalDateTime? = LocalDateTime.now()) = Database.query {
        val sql =
            """
            insert into deltaker_endring (id, deltaker_id, endring, endret, endret_av, endret_av_enhet, forslag_id, behandlet)
            values (:id, :deltaker_id, :endring, :endret, :endret_av, :endret_av_enhet, :forslag_id, :behandlet)
            on conflict (id) do update set 
                deltaker_id = :deltaker_id,
                endring = :endring,
                endret = :endret,
                endret_av = :endret_av,
                endret_av_enhet = :endret_av_enhet,
                forslag_id = :forslag_id,
                modified_at = current_timestamp,
                behandlet = :behandlet
            """.trimIndent()

        val params = mapOf(
            "id" to deltakerEndring.id,
            "deltaker_id" to deltakerEndring.deltakerId,
            "endring" to toPGObject(deltakerEndring.endring),
            "endret" to deltakerEndring.endret,
            "endret_av" to deltakerEndring.endretAv,
            "endret_av_enhet" to deltakerEndring.endretAvEnhet,
            "forslag_id" to deltakerEndring.forslag?.id,
            "behandlet" to behandlet,
        )

        it.update(queryOf(sql, params))
    }

    fun get(id: UUID) = Database.query {
        val query = queryOf(
            selectDeltakerEndring("de.id = :id"),
            mapOf("id" to id),
        )

        it.run(query.map(::rowMapper).asSingle)?.let { de -> Result.success(de) }
            ?: Result.failure(NoSuchElementException())
    }

    fun getForDeltaker(deltakerId: UUID) = Database.query {
        val query = queryOf(
            selectDeltakerEndring("de.deltaker_id = :deltaker_id"),
            mapOf("deltaker_id" to deltakerId),
        )
        it.run(query.map(::rowMapper).asList)
    }

    fun getUbehandletDeltakelsesmengder(offset: Int = 0, limit: Int = 500) = Database.query {
        val query = queryOf(
            selectDeltakerEndring(
                """
                de.behandlet is null 
                and ds.type in ('${DeltakerStatus.Type.VENTER_PA_OPPSTART.name}', '${DeltakerStatus.Type.DELTAR.name}')
                and de.endring->>'type' = :type
                and de.endring->>'gyldigFra' <= :now
                """.trimIndent(),
                offset,
                limit,
            ),
            mapOf("type" to DeltakerEndring.Endring.EndreDeltakelsesmengde::class.simpleName, "now" to LocalDate.now()),
        )
        it.run(query.map(::rowMapper).asList)
    }

    fun deleteForDeltaker(deltakerId: UUID) = Database.query {
        val query = queryOf(
            """
            DELETE FROM deltaker_endring
            WHERE deltaker_id = :deltaker_id;
            """.trimIndent(),
            mapOf("deltaker_id" to deltakerId),
        )
        it.update(query)
    }

    private fun selectDeltakerEndring(
        where: String? = null,
        offset: Int? = null,
        limit: Int? = null,
    ) = """
        SELECT de.id               AS "de.id",
               de.deltaker_id      AS "de.deltaker_id",
               de.endring          AS "de.endring",
               de.endret           AS "de.endret",
               de.endret_av        AS "de.endret_av",
               de.endret_av_enhet  AS "de.endret_av_enhet",
               de.modified_at      AS "de.modified_at",
               f.id                AS "f.id",
               f.deltaker_id       AS "f.deltaker_id",
               f.arrangoransatt_id AS "f.arrangoransatt_id",
               f.opprettet         AS "f.opprettet",
               f.begrunnelse       AS "f.begrunnelse",
               f.endring           AS "f.endring",
               f.status            AS "f.status"
        FROM deltaker_endring de
            INNER JOIN deltaker_status ds on ds.deltaker_id = de.deltaker_id
            LEFT JOIN forslag f on f.id = de.forslag_id
        WHERE ds.gyldig_til is null
            and ds.gyldig_fra < CURRENT_TIMESTAMP
            and ds.type != 'FEILREGISTRERT'
            ${where?.let { "and $it" } ?: ""}
        ORDER BY de.created_at
        ${offset?.let { "OFFSET $it" } ?: ""}
        ${limit?.let { "LIMIT $it" } ?: ""}
        """.trimIndent()
}
