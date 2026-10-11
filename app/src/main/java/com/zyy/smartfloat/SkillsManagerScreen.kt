package com.zyy.smartfloat

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.zyy.smartfloat.database.Skills
import com.zyy.smartfloat.viewmodel.SkillsManagerViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.painterResource
import com.zyy.smartfloat.database.AddSkills

@Composable
fun SkillsManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: SkillsManagerViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadSkills()
    }

    var showEditor by remember { mutableStateOf(false) }
    var editingSkillId by remember { mutableStateOf<Long?>(null) }
    var newSkillsName by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var newClickingTips by remember { mutableStateOf("") }
    val context = LocalContext.current
    val gson = Gson()

    // 使用 collectAsState 观察状态变化
    val skills by viewModel.skills.collectAsState()

    if (showEditor) {
        // ===== 技能编辑页（替代原来的弹窗）=====
        SkillEditorPage(
            isEditing = editingSkillId != null,
            skillsName = newSkillsName,
            onSkillsNameChange = { newSkillsName = it },
            content = newContent,
            onContentChange = { newContent = it },
            clickingTips = newClickingTips,
            onClickingTipsChange = { newClickingTips = it },
            onClipboardImport = {
                // 从剪切板读取 JSON 填充表单（仅添加模式）
                val clipboardText = viewModel.getClipboardContent(context)
                Log.d("SkillsActivity", clipboardText)
                if (clipboardText.isEmpty()) {
                    Toast.makeText(context, "剪切板无信息", Toast.LENGTH_SHORT).show()
                } else {
                    val addSkill = gson.fromJson(clipboardText, AddSkills::class.java)
                    if (addSkill == null) {
                        Toast.makeText(context, "剪切板格式错误", Toast.LENGTH_SHORT).show()
                    } else {
                        newSkillsName = addSkill.skillsName
                        newContent = addSkill.content ?: ""
                        newClickingTips = addSkill.clickingTips ?: ""
                    }
                }
            },
            onCopy = {
                // 编辑模式：把当前表单复制到剪切板
                viewModel.copyAddSkill(
                    context,
                    AddSkills(newSkillsName, newContent, newClickingTips)
                )
            },
            onSave = {
                if (newSkillsName.isNotEmpty()) {
                    if (editingSkillId != null) {
                        viewModel.updateSkill(editingSkillId!!, newSkillsName, newContent, newClickingTips)
                    } else {
                        viewModel.addSkill(newSkillsName, newContent, newClickingTips)
                    }
                    newSkillsName = ""
                    newContent = ""
                    newClickingTips = ""
                    editingSkillId = null
                    showEditor = false
                }
            },
            onBack = {
                newSkillsName = ""
                newContent = ""
                newClickingTips = ""
                editingSkillId = null
                showEditor = false
            }
        )
    } else {
        // ===== 技能列表页 =====
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "技能管理",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = {
                    showEditor = true
                }) {
                    Text("添加技能")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (skills.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无技能")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(skills) { skill ->
                        SkillCard(
                            skill = skill,
                            isActive = skill.isActive,
                            onSelect = {
                                viewModel.setActiveSkill(skill.id)
                            },
                            onDelete = {
                                viewModel.deleteSkill(skill)
                            },
                            onEdit = {
                                editingSkillId = skill.id
                                newSkillsName = skill.skillsName
                                newContent = skill.content ?: ""
                                newClickingTips = skill.clickingTips ?: ""
                                showEditor = true
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 技能编辑页：标题栏 + 表单（提示文字在输入框上方，内容/要点输入框加大）。
 */
@Composable
fun SkillEditorPage(
    isEditing: Boolean,
    skillsName: String,
    onSkillsNameChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    clickingTips: String,
    onClickingTipsChange: (String) -> Unit,
    onClipboardImport: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ===== 顶部标题栏：返回 | 标题 | 剪切板导入(添加)/复制(编辑) =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("返回")
            }
            Text(
                text = if (isEditing) "编辑技能" else "添加技能",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (isEditing) {
                IconButton(onClick = onCopy) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_copy),
                        contentDescription = "复制",
                        tint = Color(0xFF0052D4),
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                IconButton(onClick = onClipboardImport) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_clipboard),
                        contentDescription = "剪切板导入",
                        tint = Color(0xFF0052D4),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 表单（可上下滚动，输入框随内容自动增高）=====
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 技能名称
            Text(
                text = "技能名称",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = skillsName,
                onValueChange = onSkillsNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("例如：象棋助手") },
                singleLine = true
            )

            // 技能内容（加大，随内容自动增高）
            Text(
                text = "技能内容",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                placeholder = { Text("例如：你正在下象棋，你先分析当前局势，然后走出你觉得最棒的一步") },
                minLines = 5
            )

            // 点击要点（加大，随内容自动增高）
            Text(
                text = "点击要点",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = clickingTips,
                onValueChange = onClickingTipsChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("例如：先点击要走的棋子，然后点击棋子的落点") },
                minLines = 4
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== 底部按钮 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF999999)
                )
            ) {
                Text("取消")
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
fun SkillCard(
    skill: Skills,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = skill.skillsName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (isActive) {
                    Text(
                        text = "当前使用",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isActive) {
                    TextButton(onClick = onSelect) {
                        Text("使用")
                    }
                }
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}
