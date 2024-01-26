package com.x8bit.bitwarden.ui.auth.feature.twofactorlogin

import android.net.Uri
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.x8bit.bitwarden.R
import com.x8bit.bitwarden.data.auth.datasource.network.model.TwoFactorAuthMethod
import com.x8bit.bitwarden.data.auth.datasource.network.model.TwoFactorDataModel
import com.x8bit.bitwarden.data.auth.datasource.network.util.availableAuthMethods
import com.x8bit.bitwarden.data.auth.datasource.network.util.preferredAuthMethod
import com.x8bit.bitwarden.data.auth.datasource.network.util.twoFactorDisplayEmail
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.model.LoginResult
import com.x8bit.bitwarden.data.auth.repository.model.ResendEmailResult
import com.x8bit.bitwarden.data.auth.repository.util.CaptchaCallbackTokenResult
import com.x8bit.bitwarden.data.auth.repository.util.generateUriForCaptcha
import com.x8bit.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.ui.platform.base.util.Text
import com.x8bit.bitwarden.ui.platform.base.util.asText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

private const val KEY_STATE = "state"

/**
 * Manages application state for the Two-Factor Login screen.
 */
@HiltViewModel
@Suppress("TooManyFunctions")
class TwoFactorLoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<TwoFactorLoginState, TwoFactorLoginEvent, TwoFactorLoginAction>(
    initialState = savedStateHandle[KEY_STATE]
        ?: TwoFactorLoginState(
            authMethod = authRepository.twoFactorResponse.preferredAuthMethod,
            availableAuthMethods = authRepository.twoFactorResponse.availableAuthMethods,
            codeInput = "",
            displayEmail = authRepository.twoFactorResponse.twoFactorDisplayEmail,
            dialogState = null,
            isContinueButtonEnabled = false,
            isRememberMeEnabled = false,
            captchaToken = null,
            email = TwoFactorLoginArgs(savedStateHandle).emailAddress,
            password = TwoFactorLoginArgs(savedStateHandle).password,
        ),
) {
    init {
        // As state updates, write to saved state handle.
        stateFlow
            .onEach { savedStateHandle[KEY_STATE] = it }
            .launchIn(viewModelScope)

        // Automatically attempt to login again if a captcha token is received.
        authRepository
            .captchaTokenResultFlow
            .onEach {
                sendAction(
                    TwoFactorLoginAction.Internal.ReceiveCaptchaToken(
                        tokenResult = it,
                    ),
                )
            }
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: TwoFactorLoginAction) {
        when (action) {
            TwoFactorLoginAction.CloseButtonClick -> handleCloseButtonClicked()
            is TwoFactorLoginAction.CodeInputChanged -> handleCodeInputChanged(action)
            TwoFactorLoginAction.ContinueButtonClick -> handleContinueButtonClick()
            TwoFactorLoginAction.DialogDismiss -> handleDialogDismiss()
            is TwoFactorLoginAction.RememberMeToggle -> handleRememberMeToggle(action)
            TwoFactorLoginAction.ResendEmailClick -> handleResendEmailClick()
            is TwoFactorLoginAction.SelectAuthMethod -> handleSelectAuthMethod(action)

            is TwoFactorLoginAction.Internal.ReceiveCaptchaToken -> {
                handleCaptchaTokenReceived(action.tokenResult)
            }

            is TwoFactorLoginAction.Internal.ReceiveLoginResult -> handleReceiveLoginResult(action)
            is TwoFactorLoginAction.Internal.ReceiveResendEmailResult -> {
                handleReceiveResendEmailResult(action)
            }
        }
    }

    private fun handleCaptchaTokenReceived(tokenResult: CaptchaCallbackTokenResult) {
        when (tokenResult) {
            CaptchaCallbackTokenResult.MissingToken -> {
                mutableStateFlow.update {
                    it.copy(
                        dialogState = TwoFactorLoginState.DialogState.Error(
                            title = R.string.log_in_denied.asText(),
                            message = R.string.captcha_failed.asText(),
                        ),
                    )
                }
            }

            is CaptchaCallbackTokenResult.Success -> {
                mutableStateFlow.update {
                    it.copy(captchaToken = tokenResult.token)
                }
                handleContinueButtonClick()
            }
        }
    }

    /**
     * Update the state with the new text and enable or disable the continue button.
     */
    private fun handleCodeInputChanged(action: TwoFactorLoginAction.CodeInputChanged) {
        mutableStateFlow.update {
            it.copy(
                codeInput = action.input,
                isContinueButtonEnabled = action.input.length >= 6,
            )
        }
    }

    /**
     * Verify the input and attempt to authenticate with the code.
     */
    private fun handleContinueButtonClick() {
        mutableStateFlow.update {
            it.copy(
                dialogState = TwoFactorLoginState.DialogState.Loading(
                    message = R.string.logging_in.asText(),
                ),
            )
        }

        // If the user is manually entering a code, remove any white spaces, just in case.
        val code = mutableStateFlow.value.codeInput.let { rawCode ->
            if (mutableStateFlow.value.authMethod == TwoFactorAuthMethod.AUTHENTICATOR_APP ||
                mutableStateFlow.value.authMethod == TwoFactorAuthMethod.EMAIL
            ) {
                rawCode.replace(" ", "")
            } else {
                rawCode
            }
        }

        viewModelScope.launch {
            val result = authRepository.login(
                email = mutableStateFlow.value.email,
                password = mutableStateFlow.value.password,
                twoFactorData = TwoFactorDataModel(
                    code = code,
                    method = mutableStateFlow.value.authMethod.value.toString(),
                    remember = mutableStateFlow.value.isRememberMeEnabled,
                ),
                captchaToken = mutableStateFlow.value.captchaToken,
            )
            sendAction(
                TwoFactorLoginAction.Internal.ReceiveLoginResult(
                    loginResult = result,
                ),
            )
        }
    }

    /**
     * Dismiss the view.
     */
    private fun handleCloseButtonClicked() {
        sendEvent(TwoFactorLoginEvent.NavigateBack)
    }

    /**
     * Dismiss the dialog.
     */
    private fun handleDialogDismiss() {
        mutableStateFlow.update { it.copy(dialogState = null) }
    }

    /**
     * Handle the login result.
     */
    private fun handleReceiveLoginResult(action: TwoFactorLoginAction.Internal.ReceiveLoginResult) {
        // Dismiss the loading overlay.
        mutableStateFlow.update { it.copy(dialogState = null) }

        when (val loginResult = action.loginResult) {
            // Launch the captcha flow if necessary.
            is LoginResult.CaptchaRequired -> {
                sendEvent(
                    event = TwoFactorLoginEvent.NavigateToCaptcha(
                        uri = generateUriForCaptcha(captchaId = loginResult.captchaId),
                    ),
                )
            }

            // NO-OP: This error shouldn't be possible at this stage.
            is LoginResult.TwoFactorRequired -> Unit

            // Display any error with the same invalid verification code message.
            is LoginResult.Error -> {
                mutableStateFlow.update {
                    it.copy(
                        dialogState = TwoFactorLoginState.DialogState.Error(
                            title = R.string.an_error_has_occurred.asText(),
                            message = R.string.invalid_verification_code.asText(),
                        ),
                    )
                }
            }

            // NO-OP: Let the auth flow handle navigation after this.
            is LoginResult.Success -> Unit
        }
    }

    /**
     * Handle the resend email result.
     */
    private fun handleReceiveResendEmailResult(
        action: TwoFactorLoginAction.Internal.ReceiveResendEmailResult,
    ) {
        // Dismiss the loading overlay.
        mutableStateFlow.update { it.copy(dialogState = null) }

        when (action.resendEmailResult) {
            // Display a dialog for an error result.
            is ResendEmailResult.Error -> {
                mutableStateFlow.update {
                    it.copy(
                        dialogState = TwoFactorLoginState.DialogState.Error(
                            title = R.string.an_error_has_occurred.asText(),
                            message = R.string.verification_email_not_sent.asText(),
                        ),
                    )
                }
            }

            // Display a toast for a successful result.
            ResendEmailResult.Success -> {
                sendEvent(
                    TwoFactorLoginEvent.ShowToast(
                        message = R.string.verification_email_sent.asText(),
                    ),
                )
            }
        }
    }

    /**
     * Update the state with the new toggle value.
     */
    private fun handleRememberMeToggle(action: TwoFactorLoginAction.RememberMeToggle) {
        mutableStateFlow.update {
            it.copy(
                isRememberMeEnabled = action.isChecked,
            )
        }
    }

    /**
     * Resend the verification code email.
     */
    private fun handleResendEmailClick() {
        // Ensure that the user is in fact verifying with email.
        if (mutableStateFlow.value.authMethod != TwoFactorAuthMethod.EMAIL) {
            return
        }

        // Show the loading overlay.
        mutableStateFlow.update {
            it.copy(
                dialogState = TwoFactorLoginState.DialogState.Loading(
                    message = R.string.submitting.asText(),
                ),
            )
        }

        // Resend the email notification.
        viewModelScope.launch {
            val result = authRepository.resendVerificationCodeEmail()
            sendAction(
                TwoFactorLoginAction.Internal.ReceiveResendEmailResult(
                    resendEmailResult = result,
                ),
            )
        }
    }

    /**
     * Update the state with the auth method or opens the url for the recovery code.
     */
    private fun handleSelectAuthMethod(action: TwoFactorLoginAction.SelectAuthMethod) {
        if (action.authMethod == TwoFactorAuthMethod.RECOVERY_CODE) {
            sendEvent(TwoFactorLoginEvent.NavigateToRecoveryCode)
        } else {
            mutableStateFlow.update {
                it.copy(
                    authMethod = action.authMethod,
                )
            }
        }
    }
}

/**
 * Models state of the Two-Factor Login screen.
 */
@Parcelize
data class TwoFactorLoginState(
    val authMethod: TwoFactorAuthMethod,
    val availableAuthMethods: List<TwoFactorAuthMethod>,
    val codeInput: String,
    val dialogState: DialogState?,
    val displayEmail: String,
    val isContinueButtonEnabled: Boolean,
    val isRememberMeEnabled: Boolean,
    // Internal
    val captchaToken: String?,
    val email: String,
    val password: String?,
) : Parcelable {
    /**
     * Represents the current state of any dialogs on the screen.
     */
    sealed class DialogState : Parcelable {
        /**
         * Represents an error dialog with the given [message] and optional [title]. It no title
         * is specified a default will be provided.
         */
        @Parcelize
        data class Error(
            val title: Text? = null,
            val message: Text,
        ) : DialogState()

        /**
         * Represents a loading dialog with the given [message].
         */
        @Parcelize
        data class Loading(
            val message: Text,
        ) : DialogState()
    }
}

/**
 * Models events for the Two-Factor Login screen.
 */
sealed class TwoFactorLoginEvent {
    /**
     * Navigates back to the previous screen.
     */
    data object NavigateBack : TwoFactorLoginEvent()

    /**
     * Navigates to the captcha verification screen.
     */
    data class NavigateToCaptcha(val uri: Uri) : TwoFactorLoginEvent()

    /**
     * Navigates to the recovery code help page.
     */
    data object NavigateToRecoveryCode : TwoFactorLoginEvent()

    /**
     * Shows a toast with the given [message].
     */
    data class ShowToast(
        val message: Text,
    ) : TwoFactorLoginEvent()
}

/**
 * Models actions for the Two-Factor Login screen.
 */
sealed class TwoFactorLoginAction {
    /**
     * Indicates that the top-bar close button was clicked.
     */
    data object CloseButtonClick : TwoFactorLoginAction()

    /**
     * Indicates that the input on the verification code field changed.
     */
    data class CodeInputChanged(
        val input: String,
    ) : TwoFactorLoginAction()

    /**
     * Indicates that the Continue button was clicked.
     */
    data object ContinueButtonClick : TwoFactorLoginAction()

    /**
     * Indicates that the dialog has been dismissed.
     */
    data object DialogDismiss : TwoFactorLoginAction()

    /**
     * Indicates that the Remember Me switch  toggled.
     */
    data class RememberMeToggle(
        val isChecked: Boolean,
    ) : TwoFactorLoginAction()

    /**
     * Indicates that the Resend Email button was clicked.
     */
    data object ResendEmailClick : TwoFactorLoginAction()

    /**
     * Indicates an auth method was selected from the menu dropdown.
     */
    data class SelectAuthMethod(
        val authMethod: TwoFactorAuthMethod,
    ) : TwoFactorLoginAction()

    /**
     * Models actions that the [TwoFactorLoginViewModel] itself might send.
     */
    sealed class Internal : TwoFactorLoginAction() {
        /**
         * Indicates a captcha callback token has been received.
         */
        data class ReceiveCaptchaToken(
            val tokenResult: CaptchaCallbackTokenResult,
        ) : Internal()

        /**
         * Indicates a login result has been received.
         */
        data class ReceiveLoginResult(
            val loginResult: LoginResult,
        ) : Internal()

        /**
         * Indicates a resend email result has been received.
         */
        data class ReceiveResendEmailResult(
            val resendEmailResult: ResendEmailResult,
        ) : Internal()
    }
}