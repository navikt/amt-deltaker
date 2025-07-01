package no.nav.amt.deltaker.testdata

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post

fun Routing.registerTestdataApi(testdataService: TestdataService) {
    authenticate("SYSTEM") {
        post("/testdata/opprett") {
            val request = call.receive<OpprettTestDeltakelseRequest>()
            request.valider()
            val deltaker = testdataService.opprettDeltakelse(request)

            call.respond(deltaker)
        }
    }
}
