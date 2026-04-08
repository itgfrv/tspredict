package com.gafarov.tspredict

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TspredictApplication

fun main(args: Array<String>) {
	runApplication<TspredictApplication>(*args)
}
