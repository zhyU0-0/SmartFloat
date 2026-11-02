package com.zyy.smartfloat.skills

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.zyy.smartfloat.database.AddSkills
import com.zyy.smartfloat.database.SFTouchEventComposition
import com.zyy.smartfloat.database.SkillCompositionInput
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.painterResource
import com.zyy.smartfloat.R
import com.zyy.smartfloat.skills.viewmodel.SkillsManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: SkillsManagerViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadSkills()
    }

    // 编辑器状态
    var showEditor by remember { mutableStateOf(false) }
    var editingSkillId by remember { mutableStateOf<Long?>(null) }
    var newSkillsName by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var newClickingTips by remember { mutableStateOf("") }

    // 组合选择相关状态
    var showCompositionPicker by remember { mutableStateOf(false) }
    // 编辑页当前关联的组合（relationId 为 null 表示新增未保存）
    var editingCompositions by remember { mutableStateOf<List<EditingComposition>>(emptyList()) }

    val context = LocalContext.current
    val gson = Gson()

    // 观察状态
    val skills by viewModel.skills.collectAsState()
    val allCompositions by viewModel.allCompositions.collectAsState()

    // 进入编辑页面时加载组合关联
    LaunchedEffect(showEditor, editingSkillId) {
        if (showEditor) {
            viewModel.loadAllCompositions()
            editingCompositions = viewModel.fetchSkillCompositions(editingSkillId).map { detail ->
                EditingComposition(
                    relationId = detail.relationId,
                    compositionId = detail.compositionId,
                    name = detail.compositionName,
                    eventsCount = detail.eventsCount,
                    description = detail.description ?: ""
                )
            }
        }
    }

    if (showEditor) {
        // ===== 技能编辑页 =====
        SkillEditorPage(
            isEditing = editingSkillId != null,
            skillsName = newSkillsName,
            onSkillsNameChange = { newSkillsName = it },
            content = newContent,
            onContentChange = { newContent = it },
            clickingTips = newClickingTips,
            onClickingTipsChange = { newClickingTips = it },
            // 组合相关
            compositions = editingCompositions,
            onShowCompositionPicker = {
                showCompositionPicker = true
            },
            onRemoveComposition = { index ->
                editingCompositions = editingCompositions.toMutableList().also { it.removeAt(index) }
            },
            onUpdateCompositionDescription = { index, description ->
                editingCompositions = editingCompositions.toMutableList().also { list ->
                    list[index] = list[index].copy(description = description)
                }
            },
            // 原有功能
            onClipboardImport = {
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
                viewModel.copyAddSkill(
                    context,
                    AddSkills(newSkillsName, newContent, newClickingTips)
                )
            },
            onSave = {
                if (newSkillsName.isNotEmpty()) {
                    viewModel.saveSkillWithCompositions(
                        editingSkillId = editingSkillId,
                        skillsName = newSkillsName,
                        content = newContent,
                        clickingTips = newClickingTips,
                        compositions = editingCompositions.map {
                            SkillCompositionInput(
                                relationId = it.relationId,
                                compositionId = it.compositionId,
                                compositionName = it.name,
                                description = it.description.ifBlank { null }
                            )
                        }
                    )
                    // 延迟关闭页面，等待数据库操作完成
                    newSkillsName = ""
                    newContent = ""
                    newClickingTips = ""
                    editingSkillId = null
                    editingCompositions = emptyList()
                    showEditor = false
                }
            },
            onBack = {
                newSkillsName = ""
                newContent = ""
                newClickingTips = ""
                editingSkillId = null
                editingCompositions = emptyList()
                showEditor = false
            }
        )

        // ===== 组合选择底部弹窗 =====
        if (showCompositionPicker) {
            CompositionPickerBottomSheet(
                allCompositions = allCompositions,
                selectedIds = editingCompositions.map { it.compositionId }.toSet(),
                onDismiss = { showCompositionPicker = false },
                onManageCompositions = {
                    showCompositionPicker = false
                    context.startActivity(Intent(context, TouchDesignActivity::class.java))
                },
                onSelectComposition = { composition ->
                    editingCompositions = editingCompositions + EditingComposition(
                        relationId = null,
                        compositionId = composition.id,
                        name = composition.name,
                        eventsCount = composition.composition.size,
                        description = ""
                    )
                    Toast.makeText(context, "已添加组合：${composition.name}", Toast.LENGTH_SHORT).show()
                    showCompositionPicker = false
                }
            )
        }
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

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 组合管理横栏 =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(Intent(context, TouchDesignActivity::class.java))
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "组合管理",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "创建、编辑模拟点击组合",
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        )
                    }
                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        color = Color(0xFF999999)
                    )
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
 * 编辑页中的组合关联项（relationId 为 null 表示新增未保存）
 */
data class EditingComposition(
    val relationId: Long?,
    val compositionId: Long,
    val name: String,
    val eventsCount: Int,
    val description: String
)

/**
 * 组合选择底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompositionPickerBottomSheet(
    allCompositions: List<SFTouchEventComposition>,
    selectedIds: Set<Long>,
    onDismiss: () -> Unit,
    onManageCompositions: () -> Unit,
    onSelectComposition: (SFTouchEventComposition) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(confirmValueChange = { true })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "选择组合",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (allCompositions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂无组合，请先去管理组合页面创建")
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onDismiss) {
                            Text("关闭")
                        }
                    }
                }
            } else {
                allCompositions.forEach { composition ->
                    val isSelected = composition.id in selectedIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = !isSelected) {
                                onSelectComposition(composition)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFF5F5F5) else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = composition.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${composition.composition.size} 个事件",
                                    fontSize = 12.sp,
                                    color = Color(0xFF888888)
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "已添加",
                                    fontSize = 12.sp,
                                    color = Color(0xFF888888)
                                )
                            }
                        }
                    }
                }
            }

            // ===== 底部：跳转组合管理 =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { onManageCompositions() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "＋ 新建组合",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0052D4)
                        )
                        Text(
                            text = "跳转到组合管理页面创建新的组合",
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        )
                    }
                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        color = Color(0xFF0052D4)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 技能编辑页：标题栏 + 表单（包含组合卡片）。
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
    // 组合相关
    compositions: List<EditingComposition>,
    onShowCompositionPicker: () -> Unit,
    onRemoveComposition: (Int) -> Unit,
    onUpdateCompositionDescription: (Int, String) -> Unit,
    // 原有功能
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

        // ===== 表单（可上下滚动）=====
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

            // ===== 组合关联区域 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "关联组合",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                TextButton(onClick = onShowCompositionPicker) {
                    Text("+ 添加组合")
                }
            }

            // 已关联组合卡片（可直接移除或修改描述）
            compositions.forEachIndexed { index, item ->
                CompositionCard(
                    name = item.name,
                    eventsCount = item.eventsCount,
                    description = item.description,
                    onDescriptionChange = { onUpdateCompositionDescription(index, it) },
                    onRemove = { onRemoveComposition(index) },
                    isNew = item.relationId == null
                )
            }

            if (compositions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无关联组合，点击上方按钮添加",
                        color = Color(0xFF999999),
                        fontSize = 13.sp
                    )
                }
            }
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

/**
 * 关联组合卡片
 */
@Composable
fun CompositionCard(
    name: String,
    eventsCount: Int = 0,
    description: String?,
    onDescriptionChange: (String) -> Unit,
    onRemove: () -> Unit,
    isNew: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isNew) Color(0xFFE3F2FD) else Color(0xFFF5F5F5)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (eventsCount > 0) {
                        Text(
                            text = "$eventsCount 个事件",
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("移除", fontSize = 12.sp, color = Color(0xFF999999))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description ?: "",
                onValueChange = onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("描述这个组合的用途（可选）") },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )
        }
    }
}

@Composable
fun SkillCard(
    skill: com.zyy.smartfloat.database.Skills,
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
