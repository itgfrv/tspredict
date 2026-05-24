package com.gafarov.tspredict.service

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Service
import java.io.InputStream


data class ParsedSeriesRow(
    val timestamp: String,
    val value: String,
    val exogenous: Map<String, String> = emptyMap()
)

data class ParsedDatasetSeries(
    val targetSeriesName: String,
    val exogenousSeriesNames: List<String>,
    val rows: List<ParsedSeriesRow>
)

@Service
class ExcelParsingService(
    private val timestampNormalizationService: TimestampNormalizationService
) {

    private val formatter = DataFormatter()

    fun parseVertical(
        inputStream: InputStream,
        sheetName: String,
        dateColumnName: String,
        valueColumnName: String,
        exogenousColumnNames: List<String> = emptyList()
    ): ParsedDatasetSeries {
        WorkbookFactory.create(inputStream).use { workbook ->
            val sheet = workbook.getSheet(sheetName)
                ?: throw IllegalArgumentException("Sheet not found: $sheetName")

            val headerRow = sheet.getRow(sheet.firstRowNum)
                ?: throw IllegalArgumentException("Header row not found")

            val columnIndexes = resolveColumnIndexes(headerRow)

            val dateCol = columnIndexes[dateColumnName]
                ?: throw IllegalArgumentException("Date column not found: $dateColumnName")

            val valueCol = columnIndexes[valueColumnName]
                ?: throw IllegalArgumentException("Value column not found: $valueColumnName")

            val exogenousColumns = exogenousColumnNames.associateWith { columnName ->
                columnIndexes[columnName]
                    ?: throw IllegalArgumentException("Exogenous column not found: $columnName")
            }

            val result = mutableListOf<ParsedSeriesRow>()

            for (rowIndex in (sheet.firstRowNum + 1)..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue

                val dateCell = row.getCell(dateCol)
                val valueCell = row.getCell(valueCol)

                val timestamp = extractCellAsIsoDate(dateCell) ?: continue
                val value = extractNumericCellAsNormalizedString(valueCell) ?: continue
                val exogenous = exogenousColumns.mapValues { (_, columnIndex) ->
                    extractNumericCellAsNormalizedString(row.getCell(columnIndex))
                }

                if (exogenous.values.any { it == null }) {
                    continue
                }

                result.add(
                    ParsedSeriesRow(
                        timestamp = timestamp,
                        value = value,
                        exogenous = exogenous.mapValues { (_, rawValue) -> rawValue!! }
                    )
                )
            }

            return ParsedDatasetSeries(
                targetSeriesName = valueColumnName,
                exogenousSeriesNames = exogenousColumnNames,
                rows = result
            )
        }
    }

    fun parseHorizontal(
        inputStream: InputStream,
        sheetName: String,
        dateRowIndex: Int,
        valueRowIndex: Int,
        exogenousRowIndexes: List<Int> = emptyList()
    ): ParsedDatasetSeries {
        WorkbookFactory.create(inputStream).use { workbook ->
            val sheet = workbook.getSheet(sheetName)
                ?: throw IllegalArgumentException("Sheet not found: $sheetName")

            val dateRow = sheet.getRow(dateRowIndex)
                ?: throw IllegalArgumentException("Date row not found: $dateRowIndex")

            val valueRow = sheet.getRow(valueRowIndex)
                ?: throw IllegalArgumentException("Value row not found: $valueRowIndex")

            val exogenousRows = exogenousRowIndexes.associateWith { rowIndex ->
                sheet.getRow(rowIndex)
                    ?: throw IllegalArgumentException("Exogenous row not found: $rowIndex")
            }
            val exogenousSeriesNames = exogenousRows.map { (rowIndex, row) ->
                resolveRowName(row, rowIndex)
            }

            val maxColumn = (listOf(dateRow, valueRow) + exogenousRows.values)
                .maxOf { it.lastCellNum.toInt() }

            val result = mutableListOf<ParsedSeriesRow>()

            for (colIndex in 1 until maxColumn) {
                val dateCell = dateRow.getCell(colIndex)
                val valueCell = valueRow.getCell(colIndex)

                val timestamp = extractCellAsIsoDate(dateCell) ?: continue
                val value = extractNumericCellAsNormalizedString(valueCell) ?: continue
                val exogenous = exogenousRows.map { (rowIndex, row) ->
                    resolveRowName(row, rowIndex) to extractNumericCellAsNormalizedString(row.getCell(colIndex))
                }

                if (exogenous.any { it.second == null }) {
                    continue
                }

                result.add(
                    ParsedSeriesRow(
                        timestamp = timestamp,
                        value = value,
                        exogenous = exogenous.associate { (name, rawValue) -> name to rawValue!! }
                    )
                )
            }

            return ParsedDatasetSeries(
                targetSeriesName = resolveRowName(valueRow, valueRowIndex),
                exogenousSeriesNames = exogenousSeriesNames,
                rows = result
            )
        }
    }

    private fun resolveColumnIndexes(headerRow: Row): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for (cell in headerRow) {
            val value = formatter.formatCellValue(cell).trim()
            if (value.isNotBlank()) {
                result[value] = cell.columnIndex
            }
        }
        return result
    }

    private fun resolveRowName(row: Row, rowIndex: Int): String {
        return formatter.formatCellValue(row.getCell(0))
            .trim()
            .takeIf { it.isNotBlank() }
            ?: "row_$rowIndex"
    }

    private fun extractCellAsIsoDate(cell: Cell?): String? {
        if (cell == null) return null

        return if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            val date = cell.localDateTimeCellValue.toLocalDate()
            date.toString()
        } else {
            val raw = formatter.formatCellValue(cell).trim()
            raw.takeIf { it.isNotBlank() }
                ?.let { timestampNormalizationService.normalizeTimestamp(it) }
        }
    }

    private fun extractNumericCellAsNormalizedString(cell: Cell?): String? {
        if (cell == null) return null

        return when (cell.cellType) {
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) null
                else cell.numericCellValue.toString()
            }

            CellType.STRING -> {
                cell.stringCellValue
                    .trim()
                    .replace(" ", "")
                    .replace(",", ".")
                    .takeIf { it.isNotBlank() }
            }

            CellType.FORMULA -> {
                when (cell.cachedFormulaResultType) {
                    CellType.NUMERIC -> cell.numericCellValue.toString()
                    CellType.STRING -> cell.stringCellValue
                        .trim()
                        .replace(" ", "")
                        .replace(",", ".")
                        .takeIf { it.isNotBlank() }
                    else -> null
                }
            }

            else -> {
                val raw = formatter.formatCellValue(cell)
                    .trim()
                    .replace(" ", "")
                    .replace(",", ".")
                raw.takeIf { it.isNotBlank() }
            }
        }
    }
}
