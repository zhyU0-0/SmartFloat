package com.zyy.smartfloat.skills.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.zyy.smartfloat.MyApp
import com.zyy.smartfloat.database.AddSkills
import com.zyy.smartfloat.database.AppDatabase
import com.zyy.smartfloat.database.Skills
import com.zyy.smartfloat.database.SkillCompositionDetail
import com.zyy.smartfloat.database.SkillCompositionInput
import com.zyy.smartfloat.database.SFTouchEventComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SkillsManagerViewModel : ViewModel() {
    private val repository = MyApp.Companion.repository
    private val database = AppDatabase.getInstance(MyApp.instance)

    private val _skills = MutableStateFlow<List<Skills>>(emptyList())
    val skills: StateFlow<List<Skills>> = _skills.asStateFlow()

    // 所有组合列表（供选择使用）
    private val _allCompositions = MutableStateFlow<List<SFTouchEventComposition>>(emptyList())
    val allCompositions: StateFlow<List<SFTouchEventComposition>> = _allCompositions.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allSkills.collect {
                _skills.value = it
            }
        }
    }

    fun loadSkills() {
        viewModelScope.launch {
            repository.refreshData()
        }
    }

    // 加载所有组合列表
    fun loadAllCompositions() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                database.getAllCompositions()
            }
            _allCompositions.value = list
        }
    }

    // 直接返回指定技能关联的组合详情（供界面初始化编辑态使用）
    suspend fun fetchSkillCompositions(skillId: Long?): List<SkillCompositionDetail> {
        if (skillId == null || skillId == 0L) return emptyList()
        return withContext(Dispatchers.IO) { database.getCompositionsBySkillId(skillId) }
    }

    /**
     * 保存技能及其组合关联
     * @param editingSkillId 编辑中的技能ID，null表示新建
     * @param skillsName 技能名称
     * @param content 技能内容
     * @param clickingTips 点击要点
     * @param compositions 编辑页当前的组合关联列表（relationId 为 null 表示新增）
     */
    fun saveSkillWithCompositions(
        editingSkillId: Long?,
        skillsName: String,
        content: String?,
        clickingTips: String?,
        compositions: List<SkillCompositionInput>
    ) {
        viewModelScope.launch {
            var skillId = editingSkillId

            if (skillId != null && skillId != 0L) {
                // 更新已有技能
                val skill = _skills.value.find { it.id == skillId }
                if (skill != null) {
                    val updatedSkill = skill.copy(
                        skillsName = skillsName,
                        content = content,
                        clickingTips = clickingTips
                    )
                    repository.updateSkill(updatedSkill)
                }
            } else {
                // 新建技能
                val skill = Skills(
                    skillsName = skillsName,
                    content = content,
                    clickingTips = clickingTips,
                    isActive = _skills.value.isEmpty(),
                    createdAt = System.currentTimeMillis()
                )
                skillId = repository.insertSkill(skill)
            }

            // 处理组合关联
            val finalSkillId = skillId ?: return@launch
            if (finalSkillId != 0L) {
                withContext(Dispatchers.IO) {
                    val keptRelationIds = compositions.mapNotNull { it.relationId }.toSet()

                    // 1. 删除编辑页已移除的关联
                    database.getCompositionsBySkillId(finalSkillId)
                        .filter { it.relationId !in keptRelationIds }
                        .forEach { database.deleteSkillComposition(it.relationId) }

                    // 2. 更新已有关联（描述/组合名）
                    compositions.forEach { input ->
                        val relationId = input.relationId ?: return@forEach
                        database.updateSkillComposition(relationId, input.description ?: "")
                        database.updateSkillCompositionName(relationId, input.compositionName)
                    }

                    // 3. 新增关联
                    compositions.filter { it.relationId == null }.forEach { input ->
                        database.addSkillComposition(
                            finalSkillId,
                            input.compositionId,
                            input.compositionName,
                            input.description
                        )
                    }
                }
            }
        }
    }

    fun addSkill(skillsName: String, content: String?, clickingTips: String?) {
        viewModelScope.launch {
            val skill = Skills(
                skillsName = skillsName,
                content = content,
                clickingTips = clickingTips,
                isActive = _skills.value.isEmpty(),
                createdAt = System.currentTimeMillis()
            )
            repository.insertSkill(skill)
        }
    }

    fun setActiveSkill(skillId: Long) {
        viewModelScope.launch {
            repository.setActiveSkill(skillId)
        }
    }

    fun deleteSkill(skill: Skills) {
        viewModelScope.launch {
            repository.deleteSkill(skill)
        }
    }

    fun updateSkill(skillId: Long, skillsName: String, content: String?, clickingTips: String?) {
        viewModelScope.launch {
            val skill = _skills.value.find { it.id == skillId }
            if (skill != null) {
                val updatedSkill = skill.copy(
                    skillsName = skillsName,
                    content = content,
                    clickingTips = clickingTips
                )
                repository.updateSkill(updatedSkill)
            }
        }
    }

    fun getClipboardContent(context: Context): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip

        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            Log.d("MainActivity getClipboardContent", item.toString())
            return item.text?.toString() ?: ""
        } else {
            Log.d("MainActivity getClipboardContent", "null")
        }
        return ""
    }

    fun copyAddSkill(context: Context, addSkill: AddSkills) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val oldClip = clipboard.primaryClip
        val gson = Gson()
        val addSkillText = gson.toJson(addSkill)
        val newClip = ClipData.newPlainText("add_skill", addSkillText)
        if (oldClip != null) {
            for (i in 0 until oldClip.itemCount) {
                newClip.addItem(oldClip.getItemAt(i))
            }
        }
        Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
        clipboard.setPrimaryClip(newClip)
    }

    fun gotoTouchDesignActivity() {
        // TODO: 实现跳转
    }
}
