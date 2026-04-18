package com.gafarov.tspredict.dto

data class DatasetUploadCommand(
    val name: String,
    val sheetName: String,
    val orientation: String,
    val dateColumnName: String?,
    val valueColumnName: String?,
    val dateRowIndex: Int?,
    val valueRowIndex: Int?,
    val frequency: String?
)