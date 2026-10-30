package com.zyy.smartfloat.database


//单个点击事件
data class SFTouchEvent(
    val id: Long = 0,
    val type: Int = 0,//0 延迟 1 点击 2 滑动
    val downPosition: SFTouchPosition,
    val movePositionList: List<SFTouchPosition>,
    val upPosition: SFTouchPosition,
    val delay: Long = 0,
)

//点击坐标
data class SFTouchPosition(
    val x: Double,
    val y: Double
)
//一整个触摸事件组合
data class SFTouchEventComposition(
    val id: Long = 0,
    val name: String = "",
    val composition: List<SFTouchEvent>
)