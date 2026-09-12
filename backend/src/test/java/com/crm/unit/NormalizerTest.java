package com.crm.unit;

import com.crm.common.util.Normalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizerTest {

    @Test
    void emailIsLowercasedAndTrimmed() {
        assertThat(Normalizer.email("  John@EXAMPLE.com ")).isEqualTo("john@example.com");
    }

    @Test
    void phoneComparesOnLastTenDigits() {
        assertThat(Normalizer.phone("+971 50 123 4567")).isEqualTo(Normalizer.phone("971501234567"));
        assertThat(Normalizer.phone("0501234567")).isEqualTo("501234567");
    }

    @Test
    void websiteStripsSchemeWwwAndPath() {
        assertThat(Normalizer.website("https://www.ABC-Clinic.com/about")).isEqualTo("abc-clinic.com");
    }

    @Test
    void linkedinStripsQuery() {
        assertThat(Normalizer.linkedin("https://www.linkedin.com/company/abc-clinic?trk=x"))
            .isEqualTo("linkedin.com/company/abc-clinic");
    }

    @Test
    void nameCollapsesWhitespace() {
        assertThat(Normalizer.name("  ABC   Clinic ")).isEqualTo("abc clinic");
    }
}
