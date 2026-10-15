package uz.ttpu.weatherpatterns.mvc.model

import uz.ttpu.weatherpatterns.data.model.Weather
import uz.ttpu.weatherpatterns.data.repository.WeatherRepository

class WeatherModel(private val repository: WeatherRepository) {

    var isLoading: Boolean = false
        private set
    var weather: Weather? = null
        private set
    var error: String? = null
        private set

    fun startLoading() {
        isLoading = true
        error = null
    }

    suspend fun load(city: String) {
        repository.getWeather(city)
            .onSuccess { weather = it }
            .onFailure { error = it.message ?: "Unknown error" }
        isLoading = false
    }
}
