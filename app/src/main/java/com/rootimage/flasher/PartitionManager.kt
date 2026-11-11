package com.rootimage.flasher

data class Partition(
    val name: String,
    val path: String
)

object PartitionManager {

    fun scan(): List<Partition> {
        val script = """
            for base in \
                /dev/block/by-name \
                /dev/block/bootdevice/by-name \
                /dev/block/platform/*/by-name \
                /dev/block/mapper
            do
                [ -d "${'$'}base" ] || continue

                for p in "${'$'}base"/*
                do
                    [ -e "${'$'}p" ] || [ -L "${'$'}p" ] || continue

                    name=${'$'}{p##*/}
                    path=${'$'}(readlink -f "${'$'}p")

                    [ -n "${'$'}path" ] || continue
                    [ -b "${'$'}path" ] || continue

                    echo "${'$'}name|${'$'}path"
                done
            done
        """.trimIndent()

        val result = RootManager.execute(script)
        if (!result.success) return emptyList()

        return result.output.lines()
            .mapNotNull { line ->
                val parts = line.trim().split("|")
                if (parts.size != 2) return@mapNotNull null

                val name = parts[0].trim()
                val path = parts[1].trim()

                if (name.isBlank() || path.isBlank()) {
                    null
                } else {
                    Partition(name, path)
                }
            }
            .distinctBy { it.name }
            .sortedBy { it.name }
    }
}
