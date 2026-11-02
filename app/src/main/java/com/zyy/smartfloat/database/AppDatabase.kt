package com.zyy.smartfloat.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "smart_float.db"
        private const val DATABASE_VERSION = 6

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

        // Skill 表
        private const val TABLE_SKILL = "skills"
        private const val COL_SKILL_ID = "id"
        private const val COL_SKILL_NAME = "skillsName"
        private const val COL_SKILL_CONTENT = "content"
        private const val COL_SKILL_CLICKING_TIPS = "clickingTips"
        private const val COL_SKILL_IS_ACTIVE = "isActive"
        private const val COL_SKILL_CREATED_AT = "createdAt"

        // TouchComposition 表
        private const val TABLE_TOUCH_COMPOSITION = "touch_composition"
        private const val COL_COMPOSITION_ID = "id"
        private const val COL_COMPOSITION_NAME = "name"
        private const val COL_COMPOSITION_EVENTS = "events"
        private const val COL_COMPOSITION_CREATED_AT = "createdAt"

        // Skill-Composition 关联表
        private const val TABLE_SKILL_COMPOSITION = "skill_composition"
        private const val COL_SC_ID = "id"
        private const val COL_SC_SKILL_ID = "skillId"
        private const val COL_SC_COMPOSITION_ID = "compositionId"
        private const val COL_SC_COMPOSITION_NAME = "compositionName"
        private const val COL_SC_DESCRIPTION = "description"
        private const val COL_SC_CREATED_AT = "createdAt"


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

    private val gson = Gson()
    private val eventListType = object : TypeToken<List<SFTouchEvent>>() {}.type

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

        val createSkills = """
            CREATE TABLE IF NOT EXISTS $TABLE_SKILL (
                $COL_SKILL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SKILL_NAME TEXT NOT NULL,
                $COL_SKILL_CONTENT TEXT,
                $COL_SKILL_CLICKING_TIPS TEXT,
                $COL_SKILL_IS_ACTIVE INTEGER NOT NULL DEFAULT 0,
                $COL_SKILL_CREATED_AT INTEGER NOT NULL
            )""".trimIndent()

        val createTouchComposition = """
            CREATE TABLE IF NOT EXISTS $TABLE_TOUCH_COMPOSITION (
                $COL_COMPOSITION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_COMPOSITION_NAME TEXT NOT NULL,
                $COL_COMPOSITION_EVENTS TEXT NOT NULL,
                $COL_COMPOSITION_CREATED_AT INTEGER NOT NULL
            )""".trimIndent()

        val createSkillComposition = """
            CREATE TABLE IF NOT EXISTS $TABLE_SKILL_COMPOSITION (
                $COL_SC_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SC_SKILL_ID INTEGER NOT NULL,
                $COL_SC_COMPOSITION_ID INTEGER NOT NULL,
                $COL_SC_COMPOSITION_NAME TEXT,
                $COL_SC_DESCRIPTION TEXT,
                $COL_SC_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COL_SC_SKILL_ID) REFERENCES $TABLE_SKILL($COL_SKILL_ID),
                FOREIGN KEY ($COL_SC_COMPOSITION_ID) REFERENCES $TABLE_TOUCH_COMPOSITION($COL_COMPOSITION_ID)
            )""".trimIndent()

        db.execSQL(createModelConfig)
        db.execSQL(createConversation)
        db.execSQL(createSkills)
        db.execSQL(createTouchComposition)
        db.execSQL(createSkillComposition)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            // 创建触摸事件组合表
            db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_TOUCH_COMPOSITION (
                $COL_COMPOSITION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_COMPOSITION_NAME TEXT NOT NULL,
                $COL_COMPOSITION_EVENTS TEXT NOT NULL,
                $COL_COMPOSITION_CREATED_AT INTEGER NOT NULL
            )"""
            )
        }
        if (oldVersion < 5) {
            // 创建技能-组合关联表
            db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SKILL_COMPOSITION (
                $COL_SC_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SC_SKILL_ID INTEGER NOT NULL,
                $COL_SC_COMPOSITION_ID INTEGER NOT NULL,
                $COL_SC_COMPOSITION_NAME TEXT,
                $COL_SC_DESCRIPTION TEXT,
                $COL_SC_CREATED_AT INTEGER NOT NULL
            )"""
            )
        }
        if (oldVersion < 6) {
            // 在关联表增加组合名称字段（冗余存储，避免 JOIN）
            db.execSQL("ALTER TABLE $TABLE_SKILL_COMPOSITION ADD COLUMN $COL_SC_COMPOSITION_NAME TEXT")
            // 回填已有记录的组合名称
            db.execSQL("""
                UPDATE $TABLE_SKILL_COMPOSITION
                SET $COL_SC_COMPOSITION_NAME = (
                    SELECT tc.$COL_COMPOSITION_NAME
                    FROM $TABLE_TOUCH_COMPOSITION tc
                    WHERE tc.$COL_COMPOSITION_ID = $TABLE_SKILL_COMPOSITION.$COL_SC_COMPOSITION_ID
                )
            """)
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
                    baseUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_BASE_URL)),
                    isActive = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_ACTIVE)) == 1,
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
                )
            )
        }
        cursor.close()
        return list
    }

    fun insertSkill(skill: Skills): Long {
        Log.d("Skills","insert")
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SKILL_NAME, skill.skillsName)
            put(COL_SKILL_CONTENT, skill.content)
            put(COL_SKILL_CLICKING_TIPS, skill.clickingTips)
            put(COL_SKILL_IS_ACTIVE, if (skill.isActive) 1 else 0)
            put(COL_SKILL_CREATED_AT, skill.createdAt)
        }
        return db.insert(TABLE_SKILL, null, values)
    }

    fun updateSkill(skill: Skills): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SKILL_NAME, skill.skillsName)
            put(COL_SKILL_CONTENT, skill.content)
            put(COL_SKILL_CLICKING_TIPS, skill.clickingTips)
            put(COL_SKILL_IS_ACTIVE, if (skill.isActive) 1 else 0)
        }
        return db.update(TABLE_SKILL, values, "$COL_SKILL_ID = ?", arrayOf(skill.id.toString()))
    }

    fun deleteSkill(skill: Skills): Int {
        val db = writableDatabase
        return db.delete(TABLE_SKILL, "$COL_SKILL_ID = ?", arrayOf(skill.id.toString()))
    }

    fun setActiveSkill(skillId: Long) {
        val db = writableDatabase
        // 先将所有 Skill 设置为非活跃
        val deactivateValues = ContentValues().apply {
            put(COL_SKILL_IS_ACTIVE, 0)
        }
        db.update(TABLE_SKILL, deactivateValues, null, null)

        // 然后将指定 Skill 设置为活跃
        val activateValues = ContentValues().apply {
            put(COL_SKILL_IS_ACTIVE, 1)
        }
        db.update(TABLE_SKILL, activateValues, "$COL_SKILL_ID = ?", arrayOf(skillId.toString()))
    }

    fun getAllSkills(): List<Skills> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SKILL, null, null, null, null, null,
            "$COL_SKILL_CREATED_AT DESC"
        )
        return cursorToSkillList(cursor)
    }

    fun getActiveSkill(): Skills? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SKILL, null,
            "$COL_SKILL_IS_ACTIVE = ?", arrayOf("1"),
            null, null, null, "1"
        )
        val list = cursorToSkillList(cursor)
        return list.firstOrNull()
    }

    fun getSkillById(id: Long): Skills? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SKILL, null,
            "$COL_SKILL_ID = ?", arrayOf(id.toString()),
            null, null, null, "1"
        )
        val list = cursorToSkillList(cursor)
        return list.firstOrNull()
    }

    private fun cursorToSkillList(cursor: Cursor): List<Skills> {
        val list = mutableListOf<Skills>()
        while (cursor.moveToNext()) {
            list.add(
                Skills(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SKILL_ID)),
                    skillsName = cursor.getString(cursor.getColumnIndexOrThrow(COL_SKILL_NAME)),
                    content = cursor.getString(cursor.getColumnIndexOrThrow(COL_SKILL_CONTENT)),
                    clickingTips = cursor.getString(cursor.getColumnIndexOrThrow(COL_SKILL_CLICKING_TIPS)),
                    isActive = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SKILL_IS_ACTIVE)) != 0,
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SKILL_CREATED_AT))
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

    // ==================== TouchComposition 操作 ====================

    fun insertComposition(composition: SFTouchEventComposition): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_COMPOSITION_NAME, composition.name)
            put(COL_COMPOSITION_EVENTS, gson.toJson(composition.composition, eventListType))
            put(COL_COMPOSITION_CREATED_AT, System.currentTimeMillis())
        }
        return db.insert(TABLE_TOUCH_COMPOSITION, null, values)
    }

    fun updateComposition(composition: SFTouchEventComposition): Int {
        if (composition.id == 0L) return 0
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_COMPOSITION_NAME, composition.name)
            put(COL_COMPOSITION_EVENTS, gson.toJson(composition.composition, eventListType))
        }
        return db.update(
            TABLE_TOUCH_COMPOSITION, values,
            "$COL_COMPOSITION_ID = ?", arrayOf(composition.id.toString())
        )
    }

    fun deleteComposition(composition: SFTouchEventComposition): Int {
        if (composition.id == 0L) return 0
        val db = writableDatabase
        return db.delete(
            TABLE_TOUCH_COMPOSITION,
            "$COL_COMPOSITION_ID = ?", arrayOf(composition.id.toString())
        )
    }

    fun getAllCompositions(): List<SFTouchEventComposition> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TOUCH_COMPOSITION, null, null, null, null, null,
            "$COL_COMPOSITION_ID ASC"
        )
        return cursorToCompositionList(cursor)
    }

    private fun cursorToCompositionList(cursor: Cursor): List<SFTouchEventComposition> {
        val list = mutableListOf<SFTouchEventComposition>()
        while (cursor.moveToNext()) {
            val raw = cursor.getString(cursor.getColumnIndexOrThrow(COL_COMPOSITION_EVENTS))
            val events: List<SFTouchEvent> = try {
                gson.fromJson<List<SFTouchEvent>>(raw, eventListType) ?: emptyList()
            } catch (e: Exception) {
                Log.e("AppDatabase", "解析触摸事件组合失败", e)
                emptyList()
            }
            list.add(
                SFTouchEventComposition(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_COMPOSITION_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(COL_COMPOSITION_NAME)),
                    composition = events
                )
            )
        }
        cursor.close()
        return list
    }

    // ==================== Skill-Composition 关联表操作 ====================

    fun addSkillComposition(skillId: Long, compositionId: Long, compositionName: String? = null, description: String? = null): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SC_SKILL_ID, skillId)
            put(COL_SC_COMPOSITION_ID, compositionId)
            put(COL_SC_COMPOSITION_NAME, compositionName)
            put(COL_SC_DESCRIPTION, description)
            put(COL_SC_CREATED_AT, System.currentTimeMillis())
        }
        return db.insert(TABLE_SKILL_COMPOSITION, null, values)
    }

    fun updateSkillCompositionName(relationId: Long, compositionName: String): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SC_COMPOSITION_NAME, compositionName)
        }
        return db.update(
            TABLE_SKILL_COMPOSITION, values,
            "$COL_SC_ID = ?", arrayOf(relationId.toString())
        )
    }

    fun updateSkillComposition(relationId: Long, description: String): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SC_DESCRIPTION, description)
        }
        return db.update(
            TABLE_SKILL_COMPOSITION, values,
            "$COL_SC_ID = ?", arrayOf(relationId.toString())
        )
    }

    fun deleteSkillComposition(relationId: Long): Int {
        val db = writableDatabase
        return db.delete(
            TABLE_SKILL_COMPOSITION,
            "$COL_SC_ID = ?", arrayOf(relationId.toString())
        )
    }

    fun getCompositionsBySkillId(skillId: Long): List<SkillCompositionDetail> {
        val db = readableDatabase
        val sql = """
            SELECT 
                sc.$COL_SC_ID as relationId,
                sc.$COL_SC_COMPOSITION_ID as compositionId,
                COALESCE(sc.$COL_SC_COMPOSITION_NAME, tc.$COL_COMPOSITION_NAME) as compositionName,
                tc.$COL_COMPOSITION_EVENTS as events,
                sc.$COL_SC_DESCRIPTION as description
            FROM $TABLE_SKILL_COMPOSITION sc
            LEFT JOIN $TABLE_TOUCH_COMPOSITION tc ON sc.$COL_SC_COMPOSITION_ID = tc.$COL_COMPOSITION_ID
            WHERE sc.$COL_SC_SKILL_ID = ?
            ORDER BY sc.$COL_SC_CREATED_AT ASC
        """.trimIndent()
        val cursor = db.rawQuery(sql, arrayOf(skillId.toString()))
        val list = mutableListOf<SkillCompositionDetail>()
        while (cursor.moveToNext()) {
            val eventsRaw = cursor.getString(cursor.getColumnIndexOrThrow("events"))
            val eventsCount = try {
                if (eventsRaw.isNullOrEmpty()) 0
                else {
                    val events: List<SFTouchEvent> = gson.fromJson(eventsRaw, eventListType) ?: emptyList()
                    events.size
                }
            } catch (e: Exception) {
                0
            }
            list.add(
                SkillCompositionDetail(
                    relationId = cursor.getLong(cursor.getColumnIndexOrThrow("relationId")),
                    compositionId = cursor.getLong(cursor.getColumnIndexOrThrow("compositionId")),
                    compositionName = cursor.getString(cursor.getColumnIndexOrThrow("compositionName")) ?: "",
                    eventsCount = eventsCount,
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description"))
                )
            )
        }
        cursor.close()
        return list
    }

    fun clearCompositionsBySkillId(skillId: Long): Int {
        val db = writableDatabase
        return db.delete(
            TABLE_SKILL_COMPOSITION,
            "$COL_SC_SKILL_ID = ?", arrayOf(skillId.toString())
        )
    }

    fun deleteSkillCompositionsWhenSkillDeleted(skillId: Long) {
        clearCompositionsBySkillId(skillId)
    }
}
