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
    val value: String
)

@Service
class ExcelParsingService {

    private val formatter = DataFormatter()

    fun parseVertical(
        inputStream: InputStream,
        sheetName: String,
        dateColumnName: String,
        valueColumnName: String
    ): List<ParsedSeriesRow> {
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

            val result = mutableListOf<ParsedSeriesRow>()

            for (rowIndex in (sheet.firstRowNum + 1)..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue

                val dateCell = row.getCell(dateCol)
                val valueCell = row.getCell(valueCol)

                val timestamp = extractCellAsIsoDate(dateCell) ?: continue
                val value = extractNumericCellAsNormalizedString(valueCell) ?: continue

                result.add(ParsedSeriesRow(timestamp = timestamp, value = value))
            }

            return result
        }
    }

    fun parseHorizontal(
        inputStream: InputStream,
        sheetName: String,
        dateRowIndex: Int,
        valueRowIndex: Int
    ): List<ParsedSeriesRow> {
        WorkbookFactory.create(inputStream).use { workbook ->
            val sheet = workbook.getSheet(sheetName)
                ?: throw IllegalArgumentException("Sheet not found: $sheetName")

            val dateRow = sheet.getRow(dateRowIndex)
                ?: throw IllegalArgumentException("Date row not found: $dateRowIndex")

            val valueRow = sheet.getRow(valueRowIndex)
                ?: throw IllegalArgumentException("Value row not found: $valueRowIndex")

            val maxColumn = maxOf(dateRow.lastCellNum.toInt(), valueRow.lastCellNum.toInt())

            val result = mutableListOf<ParsedSeriesRow>()

            for (colIndex in 1 until maxColumn) {
                val dateCell = dateRow.getCell(colIndex)
                val valueCell = valueRow.getCell(colIndex)

                val timestamp = extractCellAsIsoDate(dateCell) ?: continue
                val value = extractNumericCellAsNormalizedString(valueCell) ?: continue

                result.add(ParsedSeriesRow(timestamp = timestamp, value = value))
            }

            return result
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

    private fun extractCellAsIsoDate(cell: Cell?): String? {
        if (cell == null) return null

        return if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            val date = cell.localDateTimeCellValue.toLocalDate()
            date.toString()
        } else {
            val raw = formatter.formatCellValue(cell).trim()
            raw.takeIf { it.isNotBlank() }
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