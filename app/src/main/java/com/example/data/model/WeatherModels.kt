package com.example.data.model

import androidx.compose.ui.graphics.vector.ImageVector

data class WeatherLocation(
    val nameBangla: String,
    val nameEnglish: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "Bangladesh"
)

data class HourlyWeather(
    val timeFormatted: String,
    val tempC: Double,
    val weatherCode: Int,
    val conditionBangla: String,
    val rainProbability: Int,
    val isDay: Boolean = true
)

data class DailyWeather(
    val dayNameBangla: String,
    val dayNameEnglish: String,
    val dateFormatted: String,
    val minTempC: Double,
    val maxTempC: Double,
    val weatherCode: Int,
    val conditionBangla: String,
    val rainProbability: Int,
    val precipitationSumMm: Double
)

data class WeatherAdvice(
    val title: String,
    val description: String,
    val category: String, // UMBRELLA, CLOTHING, TRAVEL, HEALTH, AGRI
    val alertLevel: AlertLevel = AlertLevel.INFO
)

enum class AlertLevel {
    INFO, WARNING, SEVERE
}

data class CurrentWeatherReport(
    val location: WeatherLocation,
    val tempC: Double,
    val feelsLikeC: Double,
    val minTempC: Double,
    val maxTempC: Double,
    val weatherCode: Int,
    val conditionBangla: String,
    val conditionEnglish: String,
    val humidity: Int,
    val windSpeedKmh: Double,
    val windDirectionDeg: Int,
    val pressureHpa: Double,
    val uvIndex: Double,
    val precipitationProbability: Int,
    val precipitationMm: Double,
    val visibilityKm: Double,
    val aqi: Int,
    val aqiStatusBangla: String,
    val sunrise: String,
    val sunset: String,
    val isDay: Boolean,
    val lastUpdated: String,
    val hourlyForecast: List<HourlyWeather>,
    val dailyForecast: List<DailyWeather>,
    val advisories: List<WeatherAdvice>
)
