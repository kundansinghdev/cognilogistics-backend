package com.cognilogistic.auth.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPhoneNormalizerTest {

    @Test
    void normalizesTenDigitIndianLocal() {
        assertThat(AuthPhoneNormalizer.normalize("9876543210")).isEqualTo("+919876543210");
    }

    @Test
    void normalizesWithSpacesAndPlus91() {
        assertThat(AuthPhoneNormalizer.normalize("+91 98765 43210")).isEqualTo("+919876543210");
    }

    @Test
    void normalizesLeading91WithoutPlus() {
        assertThat(AuthPhoneNormalizer.normalize("919876543210")).isEqualTo("+919876543210");
    }

    @Test
    void preservesExplicitInternationalPlus() {
        assertThat(AuthPhoneNormalizer.normalize("+44 7911 123456")).isEqualTo("+447911123456");
    }

    @Test
    void emptyReturnsEmpty() {
        assertThat(AuthPhoneNormalizer.normalize(null)).isEmpty();
        assertThat(AuthPhoneNormalizer.normalize("   ")).isEmpty();
    }
}
