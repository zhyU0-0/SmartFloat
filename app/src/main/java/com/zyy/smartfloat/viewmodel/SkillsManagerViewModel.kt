package com.zyy.smartfloat.viewmodel

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
import com.zyy.smartfloat.database.Skills
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SkillsManagerViewModel : ViewModel() {
    private val repository = MyApp.repository

    private val _skills = MutableStateFlow<List<Skills>>(emptyList())
    val skills: StateFlow<List<Skills>> = _skills.asStateFlow()

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

}