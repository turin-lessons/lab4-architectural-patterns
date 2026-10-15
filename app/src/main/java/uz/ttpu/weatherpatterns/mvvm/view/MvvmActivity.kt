package uz.ttpu.weatherpatterns.mvvm.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.ttpu.weatherpatterns.di.WeatherModule
import uz.ttpu.weatherpatterns.mvvm.viewmodel.WeatherUiState
import uz.ttpu.weatherpatterns.mvvm.viewmodel.WeatherViewModel
import uz.ttpu.weatherpatterns.ui.WeatherScreen

class MvvmActivity : ComponentActivity() {

    private val viewModel: WeatherViewModel by viewModels {
        WeatherModule.weatherViewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                WeatherScreen(
                    status = uiState.toStatusText(),
                    onCitySelected = viewModel::onCitySelected,
                )
            }
        }
    }
}

private fun WeatherUiState.toStatusText(): String = when (this) {
    WeatherUiState.Idle -> "Pick a city"
    WeatherUiState.Loading -> "Loading…"
    is WeatherUiState.Success ->
        "${weather.city}: ${weather.temperatureC}°C, ${weather.condition}"
    is WeatherUiState.Error -> "Error: $message"
}
