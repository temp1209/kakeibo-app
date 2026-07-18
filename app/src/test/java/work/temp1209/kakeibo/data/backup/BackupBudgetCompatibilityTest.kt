package work.temp1209.kakeibo.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupBudgetCompatibilityTest {

    @Test
    fun schema12WithoutBudget_remainsReadable() {
        val json =
            """
            {
              "backupSchemaVersion": "1.2",
              "exportType": "FULL_SNAPSHOT",
              "exportedAt": "2026-07-18T00:00:00Z",
              "rangeStart": "2026-07-01T00:00:00Z",
              "rangeEnd": "2026-07-18T00:00:00Z",
              "app": {"packageName": "test", "versionName": "1", "versionCode": 1},
              "data": {"receipts": [], "receiptItems": []}
            }
            """.trimIndent()

        val file = BackupJsonCodec.fromJson(json)

        assertEquals("1.2", file.backupSchemaVersion)
        assertNull(file.budget)
    }

    @Test
    fun schema13_roundTripsBudgetSettings() {
        val file = KakeiboBackupFile(
            exportType = BackupExportTypes.FULL_SNAPSHOT,
            exportedAt = "2026-07-18T00:00:00Z",
            rangeStart = "2026-07-01T00:00:00Z",
            rangeEnd = "2026-07-18T00:00:00Z",
            app = BackupAppInfo("test", "1", 1),
            data = BackupDataPayload(emptyList(), emptyList()),
            budget = BudgetBackupDto(
                enabled = true,
                monthlyBudgetYen = 80_000,
                aggregateMode = "DISCRETIONARY_ONLY",
            ),
        )

        val restored = BackupJsonCodec.fromJson(BackupJsonCodec.toJson(file))

        assertEquals("1.3", restored.backupSchemaVersion)
        assertEquals(80_000L, restored.budget?.monthlyBudgetYen)
        assertEquals("DISCRETIONARY_ONLY", restored.budget?.aggregateMode)
    }

    @Test
    fun failedReceipt_roundTripsAnalysisErrorMessage() {
        val receipt = ReceiptBackupDto(
            receiptId = "r1",
            receiptDatetime = "2026-07-18T10:00:00Z",
            capturedAt = "2026-07-18T10:00:00Z",
            merchantName = null,
            totalAmountYen = null,
            paymentMethod = null,
            paymentServiceName = null,
            analysisStatus = "FAILED",
            analysisErrorMessage = "APIの利用上限に達しました",
            needsReview = true,
            itemsSubtotalYen = 0,
            adjustmentYen = 0,
            createdAt = "2026-07-18T10:00:00Z",
            updatedAt = "2026-07-18T10:01:00Z",
            deletedAt = null,
            deleteReason = null,
            backupRevision = 0,
        )
        val file = KakeiboBackupFile(
            exportType = BackupExportTypes.FULL_SNAPSHOT,
            exportedAt = "2026-07-18T00:00:00Z",
            rangeStart = "2026-07-01T00:00:00Z",
            rangeEnd = "2026-07-18T00:00:00Z",
            app = BackupAppInfo("test", "1", 1),
            data = BackupDataPayload(listOf(receipt), emptyList()),
        )

        val restored = BackupJsonCodec.fromJson(BackupJsonCodec.toJson(file))

        assertEquals("APIの利用上限に達しました", restored.data.receipts.single().analysisErrorMessage)
    }

    @Test
    fun schema12WithoutErrorMessage_defaultsToNull() {
        val json =
            """
            {
              "backupSchemaVersion": "1.2",
              "exportType": "FULL_SNAPSHOT",
              "exportedAt": "2026-07-18T00:00:00Z",
              "rangeStart": "2026-07-01T00:00:00Z",
              "rangeEnd": "2026-07-18T00:00:00Z",
              "app": {"packageName": "test", "versionName": "1", "versionCode": 1},
              "data": {
                "receipts": [{
                  "receiptId": "r1",
                  "receiptDatetime": "2026-07-18T10:00:00Z",
                  "capturedAt": "2026-07-18T10:00:00Z",
                  "merchantName": null,
                  "totalAmountYen": null,
                  "paymentMethod": null,
                  "paymentServiceName": null,
                  "analysisStatus": "FAILED",
                  "needsReview": true,
                  "itemsSubtotalYen": 0,
                  "adjustmentYen": 0,
                  "createdAt": "2026-07-18T10:00:00Z",
                  "updatedAt": "2026-07-18T10:01:00Z",
                  "deletedAt": null,
                  "deleteReason": null,
                  "backupRevision": 0
                }],
                "receiptItems": []
              }
            }
            """.trimIndent()

        val file = BackupJsonCodec.fromJson(json)

        assertNull(file.data.receipts.single().analysisErrorMessage)
    }
}
