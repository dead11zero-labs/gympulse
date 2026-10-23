package app.gympulse.core.ai.debug

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.gympulse.core.ai.DeviceMemory
import app.gympulse.core.ai.LlmBackend
import app.gympulse.core.ai.LlmEngineFactory
import app.gympulse.core.ai.LlmEngineManager
import app.gympulse.core.ai.ModelLocator
import app.gympulse.core.ai.R
import app.gympulse.core.insights.narration.AppLanguage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Nur im Debug-Build: misst Gemma 4 E2B auf dem Gerät (Ladezeit, Zeit bis zum ersten Token, Token/s,
 * Speicher) auf GPU und CPU. Ergebnisse stehen auf dem Bildschirm und im Logcat (Tag `GymPulseAiBench`).
 * Eigener Launcher-Eintrag "GymPulse AI Bench".
 */
class AiBenchmarkActivity : Activity() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BenchmarkEntryPoint {
        fun engineFactory(): LlmEngineFactory
        fun modelLocator(): ModelLocator
        fun deviceMemory(): DeviceMemory
        fun engineManager(): LlmEngineManager
    }

    private val scope = MainScope()
    private var running: Job? = null
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deps = EntryPointAccessors.fromApplication(applicationContext, BenchmarkEntryPoint::class.java)
        val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()

        output = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            text = getString(R.string.ai_bench_intro)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(button(R.string.ai_bench_run_gpu) { run(deps, listOf(LlmBackend.GPU)) })
            addView(button(R.string.ai_bench_run_cpu) { run(deps, listOf(LlmBackend.CPU)) })
            addView(button(R.string.ai_bench_run_both) { run(deps, listOf(LlmBackend.GPU, LlmBackend.CPU)) })
            addView(output, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun button(label: Int, onClick: () -> Unit) = Button(this).apply {
        setText(label)
        setOnClickListener { onClick() }
    }

    private fun run(deps: BenchmarkEntryPoint, backends: List<LlmBackend>) {
        if (running?.isActive == true) return
        running = scope.launch {
            // Eine evtl. vom Coach geladene Engine freigeben, damit Messungen nicht doppelt Speicher belegen.
            deps.engineManager().releaseIfIdle()
            val model = deps.modelLocator().locate()
            if (model == null) {
                output.text = getString(R.string.ai_bench_no_model)
                return@launch
            }
            val memory = deps.deviceMemory()
            val language = AppLanguage.fromLocale(resources.configuration.locales[0])
            val report = StringBuilder()
            report.appendLine("Model: ${model.path} (${model.sizeBytes / (1024 * 1024)} MB)")
            report.appendLine("Device RAM: ${memory.totalBytes / (1024 * 1024)} MB, available ${memory.availableBytes / (1024 * 1024)} MB")
            report.appendLine("Language: $language")
            output.text = getString(R.string.ai_bench_running)
            val benchmark = NarrationBenchmark(deps.engineFactory(), ::sampleMemory, Dispatchers.Default)
            for (backend in backends) {
                val result = benchmark.run(model, backend, BenchmarkSamples.prompt(language))
                report.appendLine("=====").appendLine(result.format())
                Log.i(TAG, result.format().substringBefore("\n---"))
                result.error?.let { Log.w(TAG, "Benchmark failed on $backend", it) }
                output.text = report
            }
        }
    }

    private fun sampleMemory(): MemorySample {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return MemorySample(
            totalPssKb = info.totalPss.toLong(),
            nativeHeapKb = Debug.getNativeHeapAllocatedSize() / 1024,
            graphicsKb = info.getMemoryStat("summary.graphics")?.toLongOrNull() ?: 0L,
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "GymPulseAiBench"
    }
}
