package com.example.bioguard_wearos.data.local.db

import androidx.room.TypeConverter
import com.example.bioguard_wearos.domain.risk.RiskLevel

class RiskLevelConverter {

    @TypeConverter
    fun fromRiskLevel(riskLevel: RiskLevel): String = riskLevel.name

    @TypeConverter
    fun toRiskLevel(value: String): RiskLevel = RiskLevel.valueOf(value)
}
