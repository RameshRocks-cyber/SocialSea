package com.socialsea;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "spring.mail.username=test",
        "spring.mail.password=test"
    }
)
class SocialseaApplicationTests {

    @Test
    void contextLoads() {
    }
}
