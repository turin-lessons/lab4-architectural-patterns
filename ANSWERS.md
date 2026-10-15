# Architectural Design Patterns — Practice Class (WeatherPatterns)

Everything below uses only the ideas that the practice class text itself names from Lecture 10.
Measured results come from running the app on the emulator and the tests on the computer.

---

## Part 1 — Why architecture

### Question 1 — What a proper architecture gives you

The 600-line `MainActivity.kt` calls the server, parses JSON, uses SharedPreferences, validates the form and updates the UI in one class.

| Goal | How this class loses it |
|---|---|
| Simplicity (KISS) | One class has five jobs. To understand the form validation you have to read the network and JSON code around it, and a change in one job can break another one. |
| Testability | The validation rule is plain logic, but it sits in the same class as the UI, so the only way to run it is to start the Activity on an emulator. The server call is written inside the class, so it cannot be replaced with a fake. |
| Low-cost maintenance | If the server changes its JSON, you edit the same 600 lines that also draw the screen. Every change touches the same file, so it is risky and slow, and two people editing it will conflict. |

**First piece to move out:** the server call and the JSON parsing (with the SharedPreferences access), into a repository in a data layer. It does not depend on the UI, it has a clear boundary, and a Model layer must exist anyway before any pattern can be used. After that the screen logic can be separated step by step.

### Question 2 — Starting from nothing

Plan for the classmate:

1. **Before any pattern: find out what the app must do and what hurts.** The lecture says a pattern is abstract and its implementation depends on the requirements, so write down the requirements and the pain points (which classes are huge, what cannot be tested, what is expensive to change).
2. **Separate the Model.** Move the data access out of the Activities into a data layer (api, model, repository). All patterns need this, so it is never wasted.
3. **Learn the rules of the patterns** (MVC, MVP, MVVM, MVI). The more you understand a pattern, the better you apply it.
4. **Pick one pattern for one screen or module** that matches its requirements, and move its logic out of the Activity.
5. **Check the result against the three goals** (simplicity, testability, low-cost maintenance): write a test for the class that now holds the logic. Then continue module by module; each module may have its own structure.

**Why "use MVVM everywhere" is a bad answer:** the lecture says no single pattern suits every project, and that even in one complex project each module can have its own structure. "Everywhere" ignores the requirements of each screen, and it is a rule that is used without understanding the pattern.

---

## Part 2 — One Model layer

### Question 3 — Project and the shared data layer

- Project name `WeatherPatterns`, package `uz.ttpu.weatherpatterns`, Minimum SDK 24.
- `WeatherRepository.getWeather` calls `api.fetchWeather(city)`, returns `Result.success(...)`, and turns an `IllegalArgumentException` into `Result.failure(e)`. It uses `try/catch` and not `runCatching`, so a `CancellationException` is not swallowed.
- `WeatherRepositoryTest`: 2 tests, both pass on the computer (no emulator). `delay` was resolved without adding any extra dependency.

### Question 4 — Shared screen, module and launcher

- Packages created as in the target map; `WeatherModule` in `di`, `WeatherScreen` in `ui`.
- Four empty Activities were added, and `AndroidManifest.xml` has four new `<activity>` entries (`.mvc.view.MvcActivity`, `.mvp.view.MvpActivity`, `.mvvm.view.MvvmActivity`, `.mvi.view.MviActivity`).
- Checked on the emulator: each launcher button opens its own screen, and Back returns to the launcher.

---

## Part 3 — MVC

### Question 5 — MVC roles and the missing Controller

| # | Piece | Role |
|---|---|---|
| 1 | `Weather` data class | Model |
| 2 | `WeatherRepository` | Model |
| 3 | Composable `WeatherScreen` | View |
| 4 | `onClick` lambda in `MainActivity` that calls the repository and writes the result into a `TextView` | **Controller and View at the same time** |
| 5 | `FakeWeatherApi` | Model |
| 6 | New class `WeatherController` | Controller |

Piece 4 receives the user's action and starts the work (Controller), and it also writes into the `TextView` (View). So the Activity keeps growing, because all the logic ends up inside it, and the logic can be tested only together with the Activity and the UI.

The Model has no knowledge of the **View**.

### Question 6 — MVC with a separate Controller

- `WeatherController.onCitySelected`: `model.startLoading()` → `view.render()` → `model.load(city)` → `view.render()`.
- `MvcActivity.render()` reads `isLoading`, `error` and `weather` from the Model and sets `status`.
- Run on the emulator: `Loading…`, then `Tashkent: 24°C, Sunny`, `Samarkand: 21°C, Cloudy`, `Bukhara: 27°C, Windy`; Atlantis shows `Error: No forecast for Atlantis`.
- The answer about `render()` is in the comment above it in `MvcActivity.kt`:
  - `render()` takes no parameters because in MVC the View may look at the Model, so the Controller only says "the Model changed" and the View reads what it needs.
  - If the Controller passed the `Weather` into the View, the View would stop reading the Model and would only show what it is given. That is MVP: the middle man feeds a passive View, and the View interface would also need loading and error methods.

---

## Part 4 — MVP

### Question 7 — MVP rules: find the violations

- **(A)** `repository.getWeather("Tashkent")` inside the Activity: the View accesses the Model. Breaks rule **(1)**: the View cannot access the Model.
- **(B)** `statusLabel.text = if (result.isSuccess) "Sunny" else "Oops"`: the View decides what to show from the result. Breaks rule **(3)**: the View must be completely dumb and passive.
- **(C)** `WeatherPresenter(this)` and **(D)** `activity.statusLabel.text = "Loading…"`: they respect rule **(2)** (one Presenter for one View, a one-to-one relationship), and (D) also respects rule **(3)** because the Presenter, not the View, decides the text.
  What is still wrong: the Presenter needs a concrete `WeatherActivity`, a class of the Android framework with a `TextView`. In a test on the computer you cannot create it, and you cannot give the Presenter a fake View, so the Presenter cannot be tested without an emulator.
- **Fix:** depend on the interface from the contract:

```kotlin
class WeatherPresenter(
    private val view: WeatherContract.View,
    private val repository: WeatherRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
)
```

### Question 8 — MVP with a testable Presenter

- `WeatherPresenter.onCitySelected`: `view.showLoading()`, then in `scope.launch` it asks the repository and calls `view.showWeather(...)` or `view.showError(...)`.
- Tests with `FakeView`: 3 tests pass on the computer with no extra library:
  - `known_city_shows_loading_then_weather`
  - `unknown_city_shows_loading_then_error` → `["loading", "error:No forecast for Atlantis"]`
  - `two_requests_in_a_row_show_two_loading_events` → `["loading", "weather:Tashkent", "loading", "weather:Samarkand"]`

### Question 9 — The Presenter that outlives its View

Experiment: open MVP, tap Bukhara, rotate within 3 seconds, Logcat filter `tag:MVP`.

**Before the fix**

```
D MVP : onDestroy  activity=36862021
D MVP : onCreate   activity=204303763
D MVP : showWeather called on activity=36862021
```

- `showWeather` was called on activity `36862021`, the **old, already destroyed** Activity. The new Activity `204303763` never received it.
- Reason: the old Presenter kept its coroutine running and kept the old Activity as its View.

**After the fix** (`Presenter.destroy()` cancels the scope, `onDestroy()` calls it)

```
D MVP : onDestroy  activity=245723324
D MVP : onCreate   activity=120902274
```

- There is **no** `showWeather` line any more: the work was cancelled, so no callback reaches a destroyed View.

**Screen after rotation:** `Pick a city`, both before and after the fix.
The new Activity is a new object with a new Presenter and its own `status`, which starts at `Pick a city`. The result went to the old Activity (before) or was cancelled (after), so the request is lost.

To survive rotation the state (and the work in flight) has to live in an object that outlives the Activity: the **ViewModel**. That is Question 11.

---

## Part 5 — MVVM

### Question 10 — Where does each class live?

| Class | Package |
|---|---|
| `WeatherApi` | data/api |
| `FakeWeatherApi` | data/api |
| `Weather` | data/model |
| `WeatherRepository` | data/repository |
| `WeatherViewModel` | presentation/viewmodel |
| `WeatherUiState` | presentation/viewmodel |
| `WeatherScreen` | presentation/view |
| `MvvmActivity` | presentation/view |
| `WeatherModule` | di |

**(a)** The Activity is destroyed and recreated on rotation, but the ViewModel survives. A reference to the Activity or to a composable would keep a destroyed View alive (the same bug as in Question 9) and would tie the ViewModel to the View. Instead it exposes observable state, `StateFlow<WeatherUiState>`, and the View observes it.

**(b)** Because the ViewModel does not know the View, a test can create `WeatherViewModel` with a repository on fake API, call `onCitySelected("Tashkent")` and check that `uiState` is `Success` with the city `Tashkent` (and `Error` for `Atlantis`), with no Activity at all.

### Question 11 — MVVM with a ViewModel and StateFlow

- Dependencies added: `lifecycle-viewmodel-compose` and `lifecycle-runtime-compose` (in `libs.versions.toml`).
- `WeatherViewModel.onCitySelected`: publishes `Loading`, then in `viewModelScope.launch` publishes `Success` or `Error`. `WeatherModule` got `weatherViewModelFactory`. `toStatusText()` has one branch per state.
- Experiment: tap Samarkand/Bukhara and rotate within 3 seconds.
  - 0.3 s after the rotation the screen shows `Loading…` (not `Pick a city`).
  - After the 3 seconds it shows `Samarkand: 21°C, Cloudy` without tapping again.
  - **The request was not lost:** the ViewModel and the coroutine in `viewModelScope` survived the rotation, and the new Activity observes the same state.

---

## Part 6 — MVI

### Question 12 — MVI principles: spot the violations

| Spot | Principle it breaks | Why |
|---|---|---|
| (1) `var city`, `var isLoading`, `var temperatureC`, `var error` in the ViewModel | **Immutability** | The state is several public mutable fields that anyone can change in place. A state must not change once set; a new state is created instead. |
| (2) `onSpinnerChanged(name)` and `onRefreshButtonClicked()` | **Separation of concerns** | What the user wants is not captured as an Intent. The ViewModel has one function per widget and changes `city` itself. It could be argued under unidirectional flow, but the real fault is the missing Intent, so I choose this one. |
| (3) `data class WeatherState(var isLoading, var temperatureC)` | **Immutability** | The state class has `var` fields, so the state can be modified after it was created; it should be `val` and updated with `copy()`. |
| (4) `viewModel.isLoading = true` in the Activity | **Unidirectional data flow** | The View writes straight into the state, so data goes from the View to the Model and not back as an Intent. It also breaks separation of concerns, but the wrong direction of the data is the main fault. |

The three declarations an MVI version needs:

```kotlin
data class WeatherState(val isLoading: Boolean, val weather: Weather?, val error: String?)

sealed interface WeatherIntent

fun onIntent(intent: WeatherIntent)   // the only public function of the ViewModel
```

### Question 13 — MVI: one state, immutable updates, intents

- `WeatherMviViewModel`: `load(city)` first publishes `copy(isLoading = true, error = null)`, then in `viewModelScope` publishes a **new** state with `isLoading = false` and either `weather` or `error`. `DismissError` publishes `copy(error = null)`. `weatherMviViewModelFactory` was added to `WeatherModule`.
- **Which check comes first in `toStatusText()`:** `isLoading`. While a new request runs, the old weather (or error) is still inside the state. If `weather` or `error` were checked first, the screen would keep showing the old text and the user would not see that a new request is running.
- Tests: the two missing tests were written; all 3 MVI tests pass (`kotlinx-coroutines-test` 1.9.0, the same version as the coroutines library). On the emulator: Bukhara → Atlantis shows `Loading…` while the old weather is still in the state, then the error; rotation inside the 3 s keeps `Loading…` and then shows the result.

---

## Part 7 — Compare and choose

### Question 14 — What does each pattern depend on?

Find in Files over each pattern package (`import android.` and `import androidx.`):

| Pattern | (1) Class with the logic | (2) imports `android.` | (3) imports `androidx.` | (4) The View holds | (5) Survives rotation |
|---|---|---|---|---|---|
| MVC | `WeatherController` (state in `WeatherModel`) | no | no | `MvcActivity` holds the Controller (and reads the Model); the Controller holds the View interface | nothing: Model and Controller are fields of the Activity |
| MVP | `WeatherPresenter` | no | no | the Presenter | nothing: the Presenter is a field of the Activity (Question 9) |
| MVVM | `WeatherViewModel` | no | yes (`ViewModel`, `viewModelScope`) | the ViewModel | the ViewModel, with its state and the work in flight |
| MVI | `WeatherMviViewModel` | no | yes (`ViewModel`, `viewModelScope`) | the ViewModel | the ViewModel, with the single state and the work in flight |

In every pattern the `android.` and most `androidx.` imports are only in the `*Activity` (View) files.

Which tests run on the computer, and which need `kotlinx-coroutines-test` (all checked by running them):

- **MVC:** the Controller takes the View and the scope in its constructor, so a test with a fake `WeatherView` and `CoroutineScope(Dispatchers.Unconfined)` runs with no extra test library.
- **MVP:** the Presenter test with `FakeView` and `Dispatchers.Unconfined` runs with no extra test library.
- **MVVM:** `viewModelScope` uses `Dispatchers.Main`, so without `Dispatchers.setMain` the test fails with `IllegalStateException: Module with the Main dispatcher had failed to initialize`; it needs `kotlinx-coroutines-test`.
- **MVI:** the same: `WeatherMviViewModelTest` fails with the same exception without `setMain`, so it needs `kotlinx-coroutines-test`.

### Question 15 — Choose a pattern

| Situation | Choice | Lecture idea | One cost |
|---|---|---|---|
| (1) About screen with two static texts | No pattern | No single pattern suits every project; the implementation depends on the requirements (and KISS: simplicity). | If the screen later gets real data or logic, the structure must be added then. |
| (2) Chat screen: message list, typing indicator and connection status change at the same time | MVI | One immutable state for the whole screen and unidirectional data flow, so the parts cannot get out of sync. | More types (state, intents) and a new state object for every change, so more code than MVVM with simple state. |
| (3) Five-year-old app, 40 Activities that read the database directly, two developers, one month | No pattern, only a data layer: move the database reads into a repository (Model) first, and give a pattern only to the screens that change | In one complex project each module may have its own structure; a pattern is chosen by the requirements (here: time and size). | The old Activities stay mixed and untested for now, so the app has two styles at the same time. |

**Can (1) and (2) live in the same app with different structures?** Yes. The lecture says that in one complex project each module may have its own structure, and that there is no single pattern that suits all Android projects.
