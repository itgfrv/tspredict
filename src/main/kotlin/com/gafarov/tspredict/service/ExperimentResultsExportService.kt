package com.gafarov.tspredict.service

import com.gafarov.tspredict.dto.ExperimentResultPayload
import com.gafarov.tspredict.dto.ExperimentSeriesPayload
import com.gafarov.tspredict.entity.ExperimentRunEntity
import com.gafarov.tspredict.repository.ExperimentRepository
import com.gafarov.tspredict.repository.ExperimentRunRepository
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class ExperimentResultsExportService(
    private val experimentRepository: ExperimentRepository,
    private val experimentRunRepository: ExperimentRunRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional(readOnly = true)
    fun exportExperimentResults(experimentId: UUID): ExperimentResultsWorkbook {
        val experiment = experimentRepository.findById(experimentId)
            .orElseThrow { IllegalArgumentException("Experiment not found: $experimentId") }

        if (experiment.status in setOf("PENDING", "RUNNING")) {
            throw IllegalStateException("Experiment is not finished yet: ${experiment.status}")
        }

        val runs = experimentRunRepository.findAllByExperimentIdOrderByCreatedAtAsc(experimentId)
        val completedRuns = runs
            .filter { it.status == "COMPLETED" && !it.resultJson.isNullOrBlank() }
            .map { run ->
                ParsedRunResult(
                    run = run,
                    result = objectMapper.readValue(run.resultJson, ExperimentResultPayload::class.java)
                )
            }

        if (completedRuns.isEmpty()) {
            throw IllegalStateException("Experiment has no completed model results to export")
        }

        XSSFWorkbook().use { workbook ->
            val styles = WorkbookStyles(workbook)
            writeSummarySheet(workbook, styles, experiment, runs, completedRuns)
            writeCombinedResultsSheet(workbook, styles, completedRuns)
            completedRuns.forEachIndexed { index, parsedRun ->
                writeModelSheet(workbook, styles, parsedRun, index + 1)
            }

            val output = ByteArrayOutputStream()
            workbook.write(output)

            return ExperimentResultsWorkbook(
                fileName = buildFileName(experiment.name),
                bytes = output.toByteArray()
            )
        }
    }

    private fun writeSummarySheet(
        workbook: Workbook,
        styles: WorkbookStyles,
        experiment: com.gafarov.tspredict.entity.ExperimentEntity,
        runs: List<ExperimentRunEntity>,
        completedRuns: List<ParsedRunResult>
    ) {
        val sheet = workbook.createSheet("Summary")
        sheet.createFreezePane(0, 9)

        var rowIndex = 0
        sheet.createRow(rowIndex++).apply {
            writeCell(0, "Experiment results export", styles.title)
        }
        rowIndex++

        rowIndex = writeKeyValue(sheet.createRow(rowIndex), "Experiment", experiment.name, styles) + 1
        rowIndex = writeKeyValue(sheet.createRow(rowIndex), "Experiment ID", experiment.id.toString(), styles) + 1
        rowIndex = writeKeyValue(sheet.createRow(rowIndex), "Status", experiment.status, styles) + 1
        rowIndex = writeKeyValue(sheet.createRow(rowIndex), "Forecast mode", experiment.forecastMode, styles) + 1
        rowIndex = writeKeyValue(sheet.createRow(rowIndex), "Horizon", experiment.horizon.toString(), styles) + 1
        rowIndex = writeKeyValue(sheet.createRow(rowIndex), "Dataset", experiment.dataset.name, styles) + 1
        rowIndex = writeKeyValue(
            sheet.createRow(rowIndex),
            "Created at",
            experiment.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            styles
        ) + 1

        rowIndex++
        val header = sheet.createRow(rowIndex++)
        listOf("Model", "Model key", "Run status", "MAE", "RMSE", "Forecast points", "Error message")
            .forEachIndexed { column, label -> header.writeCell(column, label, styles.header) }

        val parsedByRunId = completedRuns.associateBy { it.run.id }
        runs.forEach { run ->
            val parsed = parsedByRunId[run.id]
            val row = sheet.createRow(rowIndex++)
            row.writeCell(0, run.model.displayName, styles.text)
            row.writeCell(1, run.model.modelKey, styles.text)
            row.writeCell(2, run.status, styles.text)
            row.writeNumberCell(3, run.mae, styles.number)
            row.writeNumberCell(4, run.rmse, styles.number)
            row.writeNumberCell(5, parsed?.result?.forecast?.values?.size?.toDouble(), styles.integer)
            row.writeCell(6, run.errorMessage ?: "", styles.text)
        }

        autosize(sheet, 7)
    }

    private fun writeCombinedResultsSheet(
        workbook: Workbook,
        styles: WorkbookStyles,
        completedRuns: List<ParsedRunResult>
    ) {
        val sheet = workbook.createSheet("Results")
        sheet.createFreezePane(2, 1)

        val observed = buildObservedValues(completedRuns.first().result)
        val timestamps = sortedTimestamps(
            observed.keys,
            completedRuns.flatMap { it.result.forecast.timestamps }
        )

        val header = sheet.createRow(0)
        header.writeCell(0, "Timestamp", styles.header)
        header.writeCell(1, "Observed value", styles.header)
        completedRuns.forEachIndexed { index, parsedRun ->
            header.writeCell(index + 2, "${parsedRun.run.model.displayName} forecast", styles.header)
        }

        timestamps.forEachIndexed { rowOffset, timestamp ->
            val row = sheet.createRow(rowOffset + 1)
            row.writeCell(0, timestamp, styles.text)
            row.writeNumberCell(1, observed[timestamp], styles.number)

            completedRuns.forEachIndexed { index, parsedRun ->
                val forecast = seriesToMap(parsedRun.result.forecast)[timestamp]
                row.writeNumberCell(index + 2, forecast, styles.number)
            }
        }

        autosize(sheet, completedRuns.size + 2)
    }

    private fun writeModelSheet(
        workbook: Workbook,
        styles: WorkbookStyles,
        parsedRun: ParsedRunResult,
        sequence: Int
    ) {
        val sheetName = safeUniqueSheetName(
            workbook,
            "${sequence}_${parsedRun.run.model.modelKey}"
        )
        val sheet = workbook.createSheet(sheetName)
        sheet.createFreezePane(0, 1)

        val result = parsedRun.result
        val train = seriesToMap(result.train)
        val actual = seriesToMap(result.actual)
        val forecast = seriesToMap(result.forecast)
        val observed = buildObservedValues(result)
        val timestamps = sortedTimestamps(observed.keys, forecast.keys)

        val header = sheet.createRow(0)
        listOf("Timestamp", "Observed value", "Forecast value", "Error", "Point type")
            .forEachIndexed { column, label -> header.writeCell(column, label, styles.header) }

        timestamps.forEachIndexed { rowOffset, timestamp ->
            val row = sheet.createRow(rowOffset + 1)
            val observedValue = observed[timestamp]
            val forecastValue = forecast[timestamp]

            row.writeCell(0, timestamp, styles.text)
            row.writeNumberCell(1, observedValue, styles.number)
            row.writeNumberCell(2, forecastValue, styles.number)
            row.writeNumberCell(
                3,
                if (observedValue != null && forecastValue != null) forecastValue - observedValue else null,
                styles.number
            )
            row.writeCell(4, pointType(timestamp, train, actual, forecast), styles.text)
        }

        autosize(sheet, 5)
    }

    private fun buildObservedValues(result: ExperimentResultPayload): LinkedHashMap<String, Double> {
        val observed = LinkedHashMap<String, Double>()
        result.train?.let { series ->
            series.timestamps.zip(series.values).forEach { (timestamp, value) ->
                observed[timestamp] = value
            }
        }
        result.actual?.let { series ->
            series.timestamps.zip(series.values).forEach { (timestamp, value) ->
                observed[timestamp] = value
            }
        }
        return observed
    }

    private fun seriesToMap(series: ExperimentSeriesPayload?): Map<String, Double> {
        if (series == null) return emptyMap()
        return series.timestamps.zip(series.values).toMap()
    }

    private fun pointType(
        timestamp: String,
        train: Map<String, Double>,
        actual: Map<String, Double>,
        forecast: Map<String, Double>
    ): String {
        val types = mutableListOf<String>()
        if (timestamp in train) types += "train"
        if (timestamp in actual) types += "actual"
        if (timestamp in forecast) types += "forecast"
        return types.joinToString("/")
    }

    private fun sortedTimestamps(vararg groups: Collection<String>): List<String> {
        return groups
            .flatMap { it }
            .distinct()
            .sorted()
    }

    private fun writeKeyValue(row: Row, key: String, value: String, styles: WorkbookStyles): Int {
        row.writeCell(0, key, styles.key)
        row.writeCell(1, value, styles.text)
        return row.rowNum
    }

    private fun autosize(sheet: org.apache.poi.ss.usermodel.Sheet, columns: Int) {
        repeat(columns) { column ->
            sheet.autoSizeColumn(column)
            val currentWidth = sheet.getColumnWidth(column)
            val maxWidth = 60 * 256
            if (currentWidth > maxWidth) {
                sheet.setColumnWidth(column, maxWidth)
            }
        }
    }

    private fun safeUniqueSheetName(workbook: Workbook, desiredName: String): String {
        val base = WorkbookUtil.createSafeSheetName(desiredName)
            .take(25)
            .ifBlank { "Model" }

        var candidate = base
        var suffix = 1
        while (workbook.getSheet(candidate) != null) {
            val ending = "_$suffix"
            candidate = base.take(31 - ending.length) + ending
            suffix += 1
        }
        return candidate
    }

    private fun buildFileName(experimentName: String): String {
        val safeName = experimentName
            .trim()
            .replace(Regex("[^A-Za-z0-9а-яА-Я._-]+"), "_")
            .trim('_')
            .take(80)
            .ifBlank { "experiment" }

        return "${safeName}_results.xlsx"
    }

    private fun Row.writeCell(column: Int, value: String, style: CellStyle) {
        val cell = createCell(column)
        cell.setCellValue(value)
        cell.cellStyle = style
    }

    private fun Row.writeNumberCell(column: Int, value: Double?, style: CellStyle) {
        val cell = createCell(column)
        if (value != null && value.isFinite()) {
            cell.setCellValue(value)
        }
        cell.cellStyle = style
    }

    private data class ParsedRunResult(
        val run: ExperimentRunEntity,
        val result: ExperimentResultPayload
    )

    data class ExperimentResultsWorkbook(
        val fileName: String,
        val bytes: ByteArray
    )

    private class WorkbookStyles(workbook: Workbook) {
        private val dataFormat = workbook.createDataFormat()

        val title: CellStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            font.fontHeightInPoints = 16
            setFont(font)
        }

        val header: CellStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_TEAL.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN

            val font = workbook.createFont()
            font.bold = true
            font.color = IndexedColors.WHITE.index
            setFont(font)
        }

        val key: CellStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        val text: CellStyle = workbook.createCellStyle()

        val number: CellStyle = workbook.createCellStyle().apply {
            dataFormat = this@WorkbookStyles.dataFormat.getFormat("0.########")
        }

        val integer: CellStyle = workbook.createCellStyle().apply {
            dataFormat = this@WorkbookStyles.dataFormat.getFormat("0")
        }
    }
}
