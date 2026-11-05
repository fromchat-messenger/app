package ru.fromchat.api.crypto

import ru.fromchat.api.crypto.backup.BackupCrypto

internal suspend fun deriveMekWrapKey(publicKey: ByteArray, context: String): ByteArray {
    val salt = ByteArray(16)
    val info = context.encodeToByteArray()
    return ru.fromchat.utils.crypto.PasswordHash.hkdfExtractAndExpand(
        inputKeyMaterial = publicKey,
        salt = salt,
        info = info,
        length = 32,
    )
}

suspend fun buildE2eeDmEnvelope(
    plaintext: String,
    senderPublicKey: ByteArray,
    recipientPublicKeyB64: String,
): E2eeBuiltEnvelope {
    val recipientPublicKey = ru.fromchat.utils.crypto.Base64.decode(recipientPublicKeyB64)
    val mek = BackupCrypto.randomBytes(32)
    val (ivB64, ciphertextB64) = ru.fromchat.api.crypto.dm.DmCrypto.encryptEnvelopePlaintext(
        plaintext.encodeToByteArray(),
        mek,
    )
    val senderWrapKey = deriveMekWrapKey(senderPublicKey, "sender_wrap_key")
    val recipientWrapKey = deriveMekWrapKey(recipientPublicKey, "recipient_wrap_key")
    val senderWrapped = ru.fromchat.api.crypto.dm.DmCrypto.wrapMek(mek, senderWrapKey)
    val recipientWrapped = ru.fromchat.api.crypto.dm.DmCrypto.wrapMek(mek, recipientWrapKey)
    return E2eeBuiltEnvelope(
        ivB64 = ivB64,
        ciphertextB64 = ciphertextB64,
        senderWrappedMekB64 = senderWrapped,
        recipientWrappedMekB64 = recipientWrapped,
    )
}

data class E2eeBuiltEnvelope(
    val ivB64: String,
    val ciphertextB64: String,
    val senderWrappedMekB64: String,
    val recipientWrappedMekB64: String,
)
