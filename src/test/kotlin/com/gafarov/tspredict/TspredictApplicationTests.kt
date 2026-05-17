package com.gafarov.tspredict

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "app.forecast-results.listener-enabled=false"
    ]
)
class TspredictApplicationTests {

	@Test
	fun contextLoads() {
	}

}
