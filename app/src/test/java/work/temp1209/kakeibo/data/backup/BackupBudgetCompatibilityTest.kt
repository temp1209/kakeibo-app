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
}
