package ru.fromchat.api.schema.user.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthUsernameStepRequest(
    val username: String,
)

@Serializable
data class AuthUsernameStepResponse(
    val ok: Boolean = true,
    val exists: Boolean? = null,
)

@Serializable
data class AuthPasswordStepRequest(
    val username: String,
    val password: String,
)

@Serializable
data class YandexOAuthParams(
    val client_id: String,
    val redirect_uri: String,
    val authorize_url: String,
    val scope: String,
)

@Serializable
data class AuthNeedsRegisterResponse(
    val status: String,
    val yandex_required: Boolean = false,
    val yandex: YandexOAuthParams? = null,
)

@Serializable
data class YandexExchangeRequest(
    val code: String,
    val code_verifier: String,
)

@Serializable
data class YandexExchangeResponse(
    val registration_proof: String,
)

@Serializable
data class AccountYandexResponse(
    val linked: Boolean = false,
    val yandex: YandexOAuthParams? = null,
)

@Serializable
data class ChangeYandexRequest(
    val registration_proof: String,
)

@Serializable
data class ChangeYandexResponse(
    val status: String? = null,
    val unchanged: Boolean = false,
)

@Serializable
data class RegisterConfirmRequest(
    val username: String,
    val display_name: String,
    val password: String,
    val confirm_password: String,
    val bio: String? = null,
    val registration_proof: String? = null,
)
