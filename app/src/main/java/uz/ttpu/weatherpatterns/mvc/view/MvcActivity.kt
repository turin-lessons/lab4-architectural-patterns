package uz.ttpu.weatherpatterns.mvc.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.ttpu.weatherpatterns.di.WeatherModule
import uz.ttpu.weatherpatterns.mvc.controller.WeatherController
import uz.ttpu.weatherpatterns.mvc.model.WeatherModel
import uz.ttpu.weatherpatterns.ui.WeatherScreen

class MvcActivity : ComponentActivity(), WeatherView {

    private val model = WeatherModel(WeatherModule.repository)
    private val controller = WeatherController(model, view = this)
    private var status by mutableStateOf("Pick a city")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WeatherScreen(status = status, onCitySelected = controller::onCitySelected)
            }
        }
    }

    // Why does render() take no parameters?
    // In MVC the View may look at the Model, so the Controller only says "the Model
    // changed" and render() reads isLoading, error and weather by itself. The Controller
    // does not need to know what the screen shows.
    // If the Controller passed the Weather into the View instead, the View would stop
    // reading the Model and would only show what it is given: that is MVP, where the
    // Presenter is the middle man and the View is passive. The Controller would then also
    // have to pass the loading and the error state, so the View interface would grow.
    override fun render() {
        val error = model.error
        val weather = model.weather
        status = when {
            model.isLoading -> "Loading…"
            error != null -> "Error: $error"
            weather != null -> "${weather.city}: ${weather.temperatureC}°C, ${weather.condition}"
            else -> "Pick a city"
        }
    }
}
