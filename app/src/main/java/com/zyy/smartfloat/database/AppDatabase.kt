package com.zyy.smartfloat.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "smart_float.db"
        private const val DATABASE_VERSION = 2

        // ModelConfig 表
        private const val TABLE_MODEL_CONFIG = "model_config"
        private const val COL_MODEL_ID = "id"
        private const val COL_MODEL_NAME = "modelName"
        private const val COL_API_KEY = "apiKey"
        private const val COL_BASE_URL = "baseUrl"
        private const val COL_IS_ACTIVE = "isActive"
        private const val COL_CREATED_AT = "createdAt"

        // Conversation 表
        private const val TABLE_CONVERSATION = "conversation"
        private const val COL_CONV_ID = "id"
        private const val COL_REMARK = "remark"
        private const val COL_INPUT_TOKEN = "inputToken"
        private const val COL_OUTPUT_TOKEN = "outputToken"
        private const val COL_ANSWER_TIME = "answerTime"
        private const val COL_MODEL_ID_FK = "modelId"
        private const val COL_CONV_CREATED_AT = "createdAt"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = AppDatabase(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 创建模型配置表
        val createModelConfig = """
            CREATE TABLE $TABLE_MODEL_CONFIG (
                $COL_MODEL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MODEL_NAME TEXT NOT NULL,
                $COL_API_KEY TEXT NOT NULL,
                $COL_BASE_URL TEXT,
                $COL_IS_ACTIVE INTEGER DEFAULT 0,
                $COL_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()

        // 创建对话表
        val createConversation = """
            CREATE TABLE $TABLE_CONVERSATION (
                $COL_CONV_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_REMARK TEXT NOT NULL,
                $COL_INPUT_TOKEN INTEGER NOT NULL,
                $COL_OUTPUT_TOKEN INTEGER NOT NULL,
                $COL_ANSWER_TIME INTEGER NOT NULL,
                $COL_MODEL_ID_FK INTEGER NOT NULL,
                $COL_MODEL_NAME TEXT NOT NULL,
                $COL_CONV_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()

        db.execSQL(createModelConfig)
        db.execSQL(createConversation)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // 添加 baseUrl 列
            db.execSQL("ALTER TABLE $TABLE_MODEL_CONFIG ADD COLUMN $COL_BASE_URL TEXT")
        }
    }

    // ==================== ModelConfig 操作 ====================

    fun insertModel(model: ModelConfig): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_MODEL_NAME, model.modelName)
            put(COL_API_KEY, model.apiKey)
            put(COL_BASE_URL, model.baseUrl)
            put(COL_IS_ACTIVE, if (model.isActive) 1 else 0)
            put(COL_CREATED_AT, model.createdAt)
        }
        return db.insert(TABLE_MODEL_CONFIG, null, values)
    }

    fun updateModel(model: ModelConfig): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_MODEL_NAME, model.modelName)
            put(COL_API_KEY, model.apiKey)
            put(COL_BASE_URL, model.baseUrl)
            put(COL_IS_ACTIVE, if (model.isActive) 1 else 0)
        }
        return db.update(TABLE_MODEL_CONFIG, values, "$COL_MODEL_ID = ?", arrayOf(model.id.toString()))
    }

    fun deleteModel(model: ModelConfig): Int {
        val db = writableDatabase
        return db.delete(TABLE_MODEL_CONFIG, "$COL_MODEL_ID = ?", arrayOf(model.id.toString()))
    }

    fun setActiveModel(modelId: Long) {
        val db = writableDatabase
        // 先将所有模型设置为非活跃
        val deactivateValues = ContentValues().apply {
            put(COL_IS_ACTIVE, 0)
        }
        db.update(TABLE_MODEL_CONFIG, deactivateValues, null, null)

        // 然后将指定模型设置为活跃
        val activateValues = ContentValues().apply {
            put(COL_IS_ACTIVE, 1)
        }
        db.update(TABLE_MODEL_CONFIG, activateValues, "$COL_MODEL_ID = ?", arrayOf(modelId.toString()))
    }

    fun getAllModels(): List<ModelConfig> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MODEL_CONFIG, null, null, null, null, null,
            "$COL_CREATED_AT DESC"
        )
        return cursorToModelList(cursor)
    }

    fun getActiveModel(): ModelConfig? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MODEL_CONFIG, null,
            "$COL_IS_ACTIVE = ?", arrayOf("1"),
            null, null, null, "1"
        )
        val list = cursorToModelList(cursor)
        return list.firstOrNull()
    }

    fun getModelById(id: Long): ModelConfig? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MODEL_CONFIG, null,
            "$COL_MODEL_ID = ?", arrayOf(id.toString()),
            null, null, null, "1"
        )
        val list = cursorToModelList(cursor)
        return list.firstOrNull()
    }

    private fun cursorToModelList(cursor: Cursor): List<ModelConfig> {
        val list = mutableListOf<ModelConfig>()
        while (cursor.moveToNext()) {
            list.add(
                ModelConfig(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MODEL_ID)),
                    modelName = cursor.getString(cursor.getColumnIndexOrThrow(COL_MODEL_NAME)),
                    apiKey = cursor.getString(cursor.getColumnIndexOrThrow(COL_API_KEY)),
                    baseUrl = cursor.getString(cursor.getColumnIndex(COL_BASE_URL)),
                    isActive = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_ACTIVE)) == 1,
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
                )
            )
        }
        cursor.close()
        return list
    }

    // ==================== Conversation 操作 ====================

    fun insertConversation(conversation: Conversation): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_REMARK, conversation.remark)
            put(COL_INPUT_TOKEN, conversation.inputToken)
            put(COL_OUTPUT_TOKEN, conversation.outputToken)
            put(COL_ANSWER_TIME, conversation.answerTime)
            put(COL_MODEL_ID_FK, conversation.modelId)
            put(COL_MODEL_NAME, conversation.modelName)
            put(COL_CONV_CREATED_AT, conversation.createdAt)
        }
        return db.insert(TABLE_CONVERSATION, null, values)
    }

    fun deleteConversation(conversation: Conversation): Int {
        val db = writableDatabase
        return db.delete(TABLE_CONVERSATION, "$COL_CONV_ID = ?", arrayOf(conversation.id.toString()))
    }

    fun deleteAllConversations() {
        val db = writableDatabase
        db.delete(TABLE_CONVERSATION, null, null)
    }

    fun getAllConversations(): List<Conversation> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CONVERSATION, null, null, null, null, null,
            "$COL_CONV_CREATED_AT DESC"
        )
        return cursorToConversationList(cursor)
    }

    fun getConversationsByModel(modelId: Long): List<Conversation> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CONVERSATION, null,
            "$COL_MODEL_ID_FK = ?", arrayOf(modelId.toString()),
            null, null, "$COL_CONV_CREATED_AT DESC"
        )
        return cursorToConversationList(cursor)
    }

    fun getConversationsByTimeRange(startTime: Long, endTime: Long): List<Conversation> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CONVERSATION, null,
            "$COL_CONV_CREATED_AT >= ? AND $COL_CONV_CREATED_AT <= ?",
            arrayOf(startTime.toString(), endTime.toString()),
            null, null, "$COL_CONV_CREATED_AT DESC"
        )
        return cursorToConversationList(cursor)
    }

    fun getDailyTokenUsage(): List<DailyTokenUsage> {
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT strftime('%Y-%m-%d', $COL_CONV_CREATED_AT / 1000, 'unixepoch') as date,
                   SUM($COL_INPUT_TOKEN + $COL_OUTPUT_TOKEN) as totalToken
            FROM $TABLE_CONVERSATION
            GROUP BY date
            ORDER BY date DESC
            LIMIT 7
        """, null)

        val list = mutableListOf<DailyTokenUsage>()
        while (cursor.moveToNext()) {
            list.add(
                DailyTokenUsage(
                    date = cursor.getString(cursor.getColumnIndexOrThrow("date")),
                    totalToken = cursor.getInt(cursor.getColumnIndexOrThrow("totalToken"))
                )
            )
        }
        cursor.close()
        return list
    }

    fun getTokenUsageByModel(): List<ModelTokenUsage> {
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT $COL_MODEL_NAME, SUM($COL_INPUT_TOKEN + $COL_OUTPUT_TOKEN) as totalToken
            FROM $TABLE_CONVERSATION
            GROUP BY $COL_MODEL_NAME
        """, null)

        val list = mutableListOf<ModelTokenUsage>()
        while (cursor.moveToNext()) {
            list.add(
                ModelTokenUsage(
                    modelName = cursor.getString(cursor.getColumnIndexOrThrow(COL_MODEL_NAME)),
                    totalToken = cursor.getInt(cursor.getColumnIndexOrThrow("totalToken"))
                )
            )
        }
        cursor.close()
        return list
    }

    fun getTotalTokenUsed(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT SUM($COL_INPUT_TOKEN + $COL_OUTPUT_TOKEN) FROM $TABLE_CONVERSATION
        """, null)

        var total = 0
        if (cursor.moveToFirst()) {
            total = cursor.getInt(0)
        }
        Log.d("total",total.toString())
        Log.d("cursor",cursor.toString())
        cursor.close()
        return total
    }

    fun getTodayTokenUsage(): Int {
        val db = readableDatabase

        val calendar = java.util.Calendar.getInstance()

        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        val cursor = db.rawQuery("""
            SELECT COALESCE(SUM($COL_INPUT_TOKEN + $COL_OUTPUT_TOKEN), 0) as totalToken
            FROM $TABLE_CONVERSATION
            WHERE $COL_CONV_CREATED_AT >= ? AND $COL_CONV_CREATED_AT <= ?
        """, arrayOf(startOfDay.toString(), endOfDay.toString()))

        var total = 0
        if (cursor.moveToFirst()) {
            total = cursor.getInt(cursor.getColumnIndexOrThrow("totalToken"))
        }
        cursor.close()
        return total
    }

    private fun cursorToConversationList(cursor: Cursor): List<Conversation> {
        val list = mutableListOf<Conversation>()
        while (cursor.moveToNext()) {
            list.add(
                Conversation(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CONV_ID)),
                    remark = cursor.getString(cursor.getColumnIndexOrThrow(COL_REMARK)),
                    inputToken = cursor.getInt(cursor.getColumnIndexOrThrow(COL_INPUT_TOKEN)),
                    outputToken = cursor.getInt(cursor.getColumnIndexOrThrow(COL_OUTPUT_TOKEN)),
                    answerTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ANSWER_TIME)),
                    modelId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MODEL_ID_FK)),
                    modelName = cursor.getString(cursor.getColumnIndexOrThrow(COL_MODEL_NAME)),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CONV_CREATED_AT))
                )
            )
        }
        cursor.close()
        return list
    }
}
