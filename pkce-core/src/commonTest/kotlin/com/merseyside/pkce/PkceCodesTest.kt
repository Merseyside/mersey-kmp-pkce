package com.merseyside.pkce

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PkceCodesTest {

    /** Пример из RFC 7636, приложение B. */
    @Test
    fun challengeMatchesRfcExample() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            PkceCodes.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        )
    }

    @Test
    fun verifierLengthIsWithinRfcLimits() {
        val verifier = PkceCodes.generate().verifier
        assertTrue(verifier.length in 43..128, "verifier length is ${verifier.length}")
    }

    @Test
    fun verifierHasNoCharactersThatNeedEscaping() {
        val allowed = Regex("^[A-Za-z0-9\\-._~]+$")
        assertTrue(allowed.matches(PkceCodes.generate().verifier))
    }

    @Test
    fun everyGenerationIsDifferent() {
        assertNotEquals(PkceCodes.generate().verifier, PkceCodes.generate().verifier)
    }
}
