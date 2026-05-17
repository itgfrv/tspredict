package com.gafarov.tspredict.dto

data class DatasetUploadCommand(
    val name: String,
    val sheetName: String,
    val orientation: String,
    val dateColumnName: String?,
    val valueColumnName: String?,
    val exogenousColumnNames: List<String> = emptyList(),
    val dateRowIndex: Int?,
    val valueRowIndex: Int?,
    val exogenousRowIndexes: List<Int> = emptyList(),
    val frequency: String?
)
