package com.rootimage.flasher

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object RootManager {

    data class Result(
        val success: Boolean,
        val output: String
    )

    fun execute(
        command: String,
        timeoutMs: Long = 30_000L
    ): Result {
        var activeProcess: Process? = null

        return try {
            val process = ProcessBuilder(
                "su", "-c", command
            ).redirectErrorStream(true).start()

            activeProcess = process

            val output = StringBuilder()

            val reader = thread(
                start = true,
                name = "RootOutputReader"
            ) {
                BufferedReader(
                    InputStreamReader(process.inputStream)
                ).use { stream ->
                    while (true) {
                        val line = stream.readLine() ?: break
                        synchronized(output) {
                            output.append(line).append('\n')
                        }
                    }
                }
            }

            val completed = process.waitFor(
                timeoutMs,
                TimeUnit.MILLISECONDS
            )

            if (!completed) {
                process.destroyForcibly()
                reader.join(3000)

                return Result(
                    false,
                    "Tempo limite excedido. Processo Root encerrado."
                )
            }

            reader.join(5000)

            val exitCode = process.exitValue()
            val resultText = synchronized(output) {
                output.toString()
            }

            Result(exitCode == 0, resultText.trim())

        } catch (e: Exception) {
            try {
                activeProcess?.destroyForcibly()
            } catch (_: Exception) {
            }

            Result(
                false,
                e.message ?: "Erro ao executar comando Root."
            )
        }
    }

    fun isRootAvailable(): Boolean {
        val result = execute("id -u")
        return result.success && result.output.trim() == "0"
    }

    fun quote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
