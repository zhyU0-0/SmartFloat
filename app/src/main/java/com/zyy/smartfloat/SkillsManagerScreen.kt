package com.zyy.smartfloat

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSkillId by remember { mutableStateOf<Long?>(null) }
    var newSkillsName by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var newClickingTips by remember { mutableStateOf("") }
    var clipboardDialogText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val gson = Gson()

    // 使用 collectAsState 观察状态变化
    val skills by viewModel.skills.collectAsState()

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
                clipboardDialogText = viewModel.getClipboardContent(context)
                Log.d("SkillsActivity", clipboardDialogText)
                showAddDialog = true
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
                            showAddDialog = true
                        }
                    )
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddDialog = false
                    editingSkillId = null
                },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (editingSkillId != null) "编辑技能" else "添加新技能")
                        if (editingSkillId == null) {
                            Button(
                                onClick = {
                                    if (clipboardDialogText.isEmpty()) {
                                        Toast.makeText(context, "剪切板无信息", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val addSkill = gson.fromJson(clipboardDialogText, AddSkills::class.java)
                                        if (addSkill == null) {
                                            Toast.makeText(context, "剪切板格式错误", Toast.LENGTH_SHORT).show()
                                        } else {
                                            newSkillsName = addSkill.skillsName
                                            newContent = addSkill.content ?: ""
                                            newClickingTips = addSkill.clickingTips ?: ""
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xe3e5f3)
                                ),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_clipboard),
                                    contentDescription = "剪切板图标",
                                    tint = Color(0xFF0052D4),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Row {
                                Button(
                                    onClick = {
                                        val addSkill = AddSkills(
                                            newSkillsName,
                                            newContent,
                                            newClickingTips
                                        )
                                        viewModel.copyAddSkill(context, addSkill)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xe3e5f3)
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_copy),
                                        contentDescription = "复制图标",
                                        tint = Color(0xFF0052D4),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newSkillsName,
                            onValueChange = { newSkillsName = it },
                            label = { Text("技能名称") },
                            placeholder = { Text("例如：象棋助手") }
                        )
                        OutlinedTextField(
                            value = newContent,
                            onValueChange = { newContent = it },
                            label = { Text("技能内容") },
                            placeholder = { Text("例如：你正在下象棋，你先分析当前局势，然后走出你觉得最棒的一步") }
                        )
                        OutlinedTextField(
                            value = newClickingTips,
                            onValueChange = { newClickingTips = it },
                            label = { Text("点击要点") },
                            placeholder = { Text("例如：先点击要走的棋子，然后点击棋子的落点") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
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
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        newSkillsName = ""
                        newContent = ""
                        newClickingTips = ""
                        showAddDialog = false
                        editingSkillId = null
                    }) {
                        Text("取消")
                    }
                }
            )
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