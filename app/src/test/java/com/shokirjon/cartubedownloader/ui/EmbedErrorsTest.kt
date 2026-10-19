package com.shokirjon.cartubedownloader.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedErrorsTest {
    @Test fun ownerDisabledEmbeddingDoesNotClaimPlayerIsBroken() {
        assertTrue(EmbedErrors.message(101).contains("Muallif"))
        assertTrue(EmbedErrors.message(150).contains("Muallif"))
    }

    @Test fun missingIdentityProvidesDiagnosticCode() {
        assertTrue(EmbedErrors.message(153).contains("153"))
    }
}
