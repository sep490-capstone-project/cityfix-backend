package com.cityfix.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "SECRET_KEY=this_is_a_default_secret_key_for_local_testing_that_should_be_long_enough",
        "RESEND_API_KEY=test",
        "RESEND_FROM_EMAIL=test@test.com",
        "CLOUDINARY_CLOUD_NAME=test",
        "CLOUDINARY_API_KEY=test",
        "CLOUDINARY_API_SECRET=test",
        "GEMINI_API_KEY=test"
})
@Disabled("Requires live PostgreSQL instance")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
