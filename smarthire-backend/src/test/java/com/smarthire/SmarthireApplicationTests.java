package com.smarthire;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmarthireApplicationTests {

    @Test
    void applicationClassExists() {
        // Verifies main application class can be loaded
        assertThat(SmarthireApplication.class).isNotNull();
    }
}
