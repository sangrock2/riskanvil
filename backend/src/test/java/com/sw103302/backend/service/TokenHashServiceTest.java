package com.sw103302.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenHashServiceTest {

    @Test
    void hash_shouldBeDeterministicWithSamePepper() {
        TokenHashService service = new TokenHashService("pepper");

        String h1 = service.hash("sample-token");
        String h2 = service.hash("sample-token");

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64);
    }

    @Test
    void hash_shouldChangeWhenPepperChanges() {
        TokenHashService s1 = new TokenHashService("pepper-a");
        TokenHashService s2 = new TokenHashService("pepper-b");

        assertThat(s1.hash("sample-token")).isNotEqualTo(s2.hash("sample-token"));
    }

    @Test
    void hash_shouldRejectBlankToken() {
        TokenHashService service = new TokenHashService("pepper");

        assertThatThrownBy(() -> service.hash(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
