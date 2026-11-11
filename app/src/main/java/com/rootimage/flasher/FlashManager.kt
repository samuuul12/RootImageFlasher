package com.rootimage.flasher

import java.io.File

object FlashManager {

    private val normalAllowed = setOf(
        "boot", "init_boot", "vendor_boot",
        "recovery", "dtbo", "vbmeta",
        "vbmeta_system", "vbmeta_vendor"
    )

    private val protectedPartitions = setOf(
        "system", "vendor", "product", "system_ext",
        "odm", "super", "vendor_dlkm", "odm_dlkm"
    )

    private fun baseName(name: String): String {
        return name.lowercase()
            .removeSuffix("_a")
            .removeSuffix("_b")
    }

    fun isProtected(name: String): Boolean =
        baseName(name) in protectedPartitions

    fun isFlashable(name: String): Boolean {
        val base = baseName(name)
        return base in normalAllowed || base in protectedPartitions
    }

    fun validate(
        image: File,
        partition: Partition,
        allowProtected: Boolean
    ): String? {
        if (!image.exists() || !image.isFile || !image.canRead()) {
            return "Arquivo de imagem inválido ou inacessível."
        }

        if (!image.name.endsWith(".img", ignoreCase = true)) {
            return "O arquivo selecionado não possui extensão .img."
        }

        if (image.length() <= 0L) {
            return "A imagem está vazia."
        }

        if (!isFlashable(partition.name)) {
            return "Partição não autorizada para gravação."
        }

        if (isProtected(partition.name) && !allowProtected) {
            return "Partição protegida. Desbloqueie o modo avançado."
        }

        if (!partition.path.startsWith("/dev/block/")) {
            return "Caminho de destino inválido."
        }

        if (isAndroidSparse(image)) {
            return "Imagem Android Sparse detectada. Converta-a para imagem RAW com simg2img antes de gravar."
        }

        return null
    }

    private fun isAndroidSparse(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                if (input.read(header) != 4) return false

                header[0] == 0x3A.toByte() &&
                header[1] == 0xFF.toByte() &&
                header[2] == 0x26.toByte() &&
                header[3] == 0xED.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun flash(
        image: File,
        partition: Partition,
        allowProtected: Boolean
    ): RootManager.Result {
        val error = validate(image, partition, allowProtected)
        if (error != null) return RootManager.Result(false, error)

        val input = RootManager.quote(image.absolutePath)
        val output = RootManager.quote(partition.path)
        val expectedPath = RootManager.quote(partition.path)

        val script = """
            set -e

            [ -f $input ] || {
                echo "Imagem não encontrada"
                exit 1
            }

            [ -b $output ] || {
                echo "Destino não é um dispositivo de bloco"
                exit 1
            }

            current_path=${'$'}(readlink -f $output)
            [ -n "${'$'}current_path" ] || {
                echo "Não foi possível resolver o destino"
                exit 1
            }

            expected_path=${'$'}(readlink -f $expectedPath)
            [ "${'$'}current_path" = "${'$'}expected_path" ] || {
                echo "O destino mudou desde a seleção"
                exit 1
            }

            image_size=${'$'}(stat -c %s $input)
            partition_size=${'$'}(blockdev --getsize64 $output)

            [ "${'$'}image_size" -gt 0 ] || {
                echo "Imagem vazia"
                exit 1
            }

            [ "${'$'}image_size" -le "${'$'}partition_size" ] || {
                echo "Imagem maior que a partição"
                exit 1
            }

            echo "Iniciando gravação..."
            dd if=$input of=$output bs=4M conv=fsync
            sync
            echo "FLASH_SUCCESS"
        """.trimIndent()

        return RootManager.execute(script, timeoutMs = 30 * 60 * 1000L)
    }
}
