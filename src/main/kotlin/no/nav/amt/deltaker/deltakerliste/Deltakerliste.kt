package no.nav.amt.deltaker.deltakerliste

import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.util.UUID

data class Deltakerliste(
    val id: UUID,
    val gjennomforingstype: GjennomforingType,
    val tiltakstype: Tiltakstype,
    val navn: String,
    val status: GjennomforingStatusType?,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val oppstart: Oppstartstype?,
    val apentForPamelding: Boolean,
    val oppmoteSted: String?,
    val arrangor: Arrangor,
    val pameldingstype: GjennomforingPameldingType?,
) {
    fun erAvlystEllerAvbrutt(): Boolean = status == GjennomforingStatusType.AVLYST ||
        status == GjennomforingStatusType.AVBRUTT

    fun erAvsluttet(): Boolean = erAvlystEllerAvbrutt() || status == GjennomforingStatusType.AVSLUTTET

    // TODO sjekk bruken
    val erFellesOppstart get() = oppstart == Oppstartstype.FELLES

    val kanMeldePaDirekte get() = pameldingstype == GjennomforingPameldingType.DIREKTE_VEDTAK
    val trengerGodkjenning get() = pameldingstype == null || pameldingstype == GjennomforingPameldingType.TRENGER_GODKJENNING
}
