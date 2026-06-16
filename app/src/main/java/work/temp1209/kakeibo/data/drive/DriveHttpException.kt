package work.temp1209.kakeibo.data.drive

/**
 * Drive REST が非成功ステータスを返したときに投げる。
 * WorkManager のリトライ可否判定に使う。
 */
class DriveHttpException(
    val httpCode: Int,
    message: String,
    val responseBody: String? = null,
) : Exception(message)
