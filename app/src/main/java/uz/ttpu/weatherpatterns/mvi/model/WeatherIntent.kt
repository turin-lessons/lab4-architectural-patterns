package uz.ttpu.weatherpatterns.mvi.model

sealed interface WeatherIntent {
    data class SelectCity(val city: String) : WeatherIntent
    data object DismissError : WeatherIntent
}
