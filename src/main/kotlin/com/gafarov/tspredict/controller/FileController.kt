package com.gafarov.tspredict.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FileController() {
    @GetMapping("/hello")
    fun parseExcel(): String{
        return "hello";
    }
}