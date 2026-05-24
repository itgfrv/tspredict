package com.gafarov.tspredict.service

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class ExcelParsingServiceTest {

    private val service = ExcelParsingService(TimestampNormalizationService())

    @Test
    fun `parses quarter labels from string date cells`() {
        val bytes = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("1")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("DATE")
                createCell(1).setCellValue("VALUE")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("I квартал 2000")
                createCell(1).setCellValue(101.63)
            }
            sheet.createRow(2).apply {
                createCell(0).setCellValue("II квартал 2000")
                createCell(1).setCellValue(101.51)
            }

            ByteArrayOutputStream().use { output ->
                workbook.write(output)
                output.toByteArray()
            }
        }

        val parsed = service.parseVertical(
            inputStream = bytes.inputStream(),
            sheetName = "1",
            dateColumnName = "DATE",
            valueColumnName = "VALUE"
        )

        assertEquals(listOf("2000-01-01", "2000-04-01"), parsed.rows.map { it.timestamp })
        assertEquals(listOf("101.63", "101.51"), parsed.rows.map { it.value })
    }
}
