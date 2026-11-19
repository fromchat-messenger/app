package ru.fromchat.api.instance

enum class InstanceEncryptionMode(val wireValue: String) {
    Compliance("compliance"),
    E2ee("e2ee"),
    ;

    companion object {
        fun fromWire(raw: String?): InstanceEncryptionMode? =
            when (raw?.trim()?.lowercase()) {
                Compliance.wireValue -> Compliance
                E2ee.wireValue -> E2ee
                else -> null
            }

        fun displayLabel(mode: InstanceEncryptionMode): String = when (mode) {
            Compliance -> "Compliance"
            E2ee -> "E2EE"
        }
    }
}

data class InstanceEncryptionSnapshot(
    val instanceId: String,
    val configKey: String,
    val mode: InstanceEncryptionMode,
    val version: Int,
    val changedAt: String?,
    val protocol: String,
)
