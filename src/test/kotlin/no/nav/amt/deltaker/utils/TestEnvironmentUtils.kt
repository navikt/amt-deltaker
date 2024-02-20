package no.nav.amt.deltaker.utils

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.auth.PreAuthorizedApp
import java.nio.file.Paths

fun configureEnvForAuthentication() {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL().toString()
    val preAuthorizedApp = PreAuthorizedApp("dev:amt:amt-deltaker-bff", "amt-deltaker-bff")
    System.setProperty(Environment.AZURE_OPENID_CONFIG_JWKS_URI_KEY, uri)
    System.setProperty(Environment.AZURE_OPENID_CONFIG_ISSUER_KEY, "issuer")
    System.setProperty(Environment.AZURE_APP_CLIENT_ID_KEY, "amt-deltaker")
    System.setProperty(Environment.AZURE_APP_PRE_AUTHORIZED_APPS, objectMapper.writeValueAsString(listOf(preAuthorizedApp)))
}
