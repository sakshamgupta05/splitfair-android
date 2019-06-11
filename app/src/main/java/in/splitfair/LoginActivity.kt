package `in`.splitfair

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.constraint.ConstraintSet
import android.support.design.widget.Snackbar
import android.support.transition.TransitionManager
import android.text.TextUtils
import android.util.Log
import android.view.View
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import kotlinx.android.synthetic.main.activity_login_phone.*
import java.util.concurrent.TimeUnit
import com.truecaller.android.sdk.TrueSDK
import com.truecaller.android.sdk.TrueSdkScope
import com.truecaller.android.sdk.TrueError
import com.truecaller.android.sdk.TrueProfile
import com.truecaller.android.sdk.ITrueCallback

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private var verificationInProgress = false
    private var storedVerificationId: String? = ""
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks

    private val constraintPhone = ConstraintSet()
    private val constraintVerify = ConstraintSet()

    private var currentView = VIEW_LOGIN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_phone)

        // Restore instance state
        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState)
        }

        title = LOGIN

        constraintPhone.clone(root)
        constraintVerify.clone(this, R.layout.activity_login_verify)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // truecaller sdk callback
        val sdkCallback = object : ITrueCallback {
            override fun onVerificationRequired() {
                Log.d("aaaaaaaaaa", "Verification Required : ")
            }

            override fun onSuccessProfileShared(trueProfile: TrueProfile) {

                // This method is invoked when the truecaller app is installed on the device and the user gives his
                // consent to share his truecaller profile

                Log.d("aaaaaaaaaa", "Verified Successfully : ${trueProfile.firstName} ${trueProfile.lastName} ${trueProfile.phoneNumber}")
            }

            override fun onFailureProfileShared(trueError: TrueError) {
                // This method is invoked when some error occurs or if an invalid request for verification is made

                Log.d("aaaaaaaaaa", "onFailureProfileShared: " + trueError.errorType)
            }
        }

        // truecaller sdk init
        val trueScope = TrueSdkScope.Builder(this, sdkCallback)
            .consentMode(TrueSdkScope.CONSENT_MODE_POPUP)
            .consentTitleOption(TrueSdkScope.SDK_CONSENT_TITLE_VERIFY)
            .footerType(TrueSdkScope.FOOTER_TYPE_SKIP)
            .build()

        TrueSDK.init(trueScope)

        if (!TrueSDK.getInstance().isUsable) {
            buttonTruecaller.visibility = View.GONE
        }

        // Initialize phone auth callbacks
        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // This callback will be invoked in two situations:
                // 1 - Instant verification. In some cases the phone number can be instantly
                //     verified without needing to send or enter a verification code.
                // 2 - Auto-retrieval. On some devices Google Play services can automatically
                //     detect the incoming verification SMS and perform verification without
                //     user action.
                Log.d(TAG, "onVerificationCompleted:${credential}")
                verificationInProgress = false

                // Update the UI and attempt sign in with the phone credential
                updateUI(STATE_VERIFY_SUCCESS, credential)
                signInWithPhoneAuthCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                // This callback is invoked in an invalid request for verification is made,
                // for instance if the the phone number format is not valid.
                Log.w(TAG, "onVerificationFailed", e)
                verificationInProgress = false

                if (e is FirebaseAuthInvalidCredentialsException) {
                    // Invalid request
                    fieldPhoneNumber.error = "Invalid phone number."
                } else if (e is FirebaseTooManyRequestsException) {
                    // The SMS quota for the project has been exceeded
                    Snackbar.make(findViewById(android.R.id.content), "Quota exceeded.",
                        Snackbar.LENGTH_SHORT).show()
                }

                // Show a message and update the UI
                updateUI(STATE_VERIFY_FAILED)
            }

            override fun onCodeSent(
                verificationId: String?,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                // The SMS verification code has been sent to the provided phone number, we
                // now need to ask the user to enter the code and then construct a credential
                // by combining the code with a verification ID.
                Log.d(TAG, "onCodeSent:" + verificationId!!)

                // Save verification ID and resending token so we can use them later
                storedVerificationId = verificationId
                resendToken = token

                // Update UI
                updateUI(STATE_CODE_SENT)
            }
        }
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        updateUI(currentUser)

        if (verificationInProgress && validatePhoneNumber()) {
            startPhoneNumberVerification(fieldPhoneNumber.text.toString())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_VERIFY_IN_PROGRESS, verificationInProgress)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        verificationInProgress = savedInstanceState.getBoolean(KEY_VERIFY_IN_PROGRESS)
    }

    private fun startPhoneNumberVerification(phoneNumber: String) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
            phoneNumber,        // Phone number to verify
            60,                 // Timeout duration
            TimeUnit.SECONDS,   // Unit of timeout
            this,               // Activity (for callback binding)
            callbacks)          // OnVerificationStateChangedCallbacks

        verificationInProgress = true
    }

    private fun verifyPhoneNumberWithCode(verificationId: String?, code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun resendVerificationCode(
        phoneNumber: String,
        token: PhoneAuthProvider.ForceResendingToken?
    ) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
            phoneNumber,        // Phone number to verify
            60,                 // Timeout duration
            TimeUnit.SECONDS,   // Unit of timeout
            this,               // Activity (for callback binding)
            callbacks,          // OnVerificationStateChangedCallbacks
            token)              // ForceResendingToken from callbacks
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")

                    val user = task.result?.user
                    updateUI(STATE_SIGNIN_SUCCESS)
                } else {
                    // Sign in failed, display a message and update the UI
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        // The verification code entered was invalid
                        fieldVerificationCode.error = "Invalid code."
                    }
                    updateUI(STATE_SIGNIN_FAILED)
                }
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            updateUI(STATE_SIGNIN_SUCCESS)
        } else {
            updateUI(STATE_INITIALIZED)
        }
    }

    private fun updateUI(
        uiState: Int,
        cred: PhoneAuthCredential? = null
    ) {
        when (uiState) {
            STATE_INITIALIZED -> {
                // Initialized state, show only the phone number field and start button
                enableViews(buttonStartVerification, fieldPhoneNumber)
                disableViews(buttonVerifyPhone, buttonResend, fieldVerificationCode)
            }
            STATE_CODE_SENT -> {
                // Code sent state, show the verification field, the
                enableViews(buttonVerifyPhone, buttonResend, fieldPhoneNumber, fieldVerificationCode)
                disableViews(buttonStartVerification)
                transitionView(VIEW_VERIFY)
            }
            STATE_VERIFY_FAILED -> {
                // Verification has failed, show all options
                enableViews(buttonStartVerification, buttonVerifyPhone, buttonResend, fieldPhoneNumber,
                    fieldVerificationCode)
            }
            STATE_VERIFY_SUCCESS -> {
                // Verification has succeeded, proceed to firebase sign in
                disableViews(buttonStartVerification, buttonVerifyPhone, buttonResend, fieldPhoneNumber,
                    fieldVerificationCode)

                // Set the verification text based on the credential
                if (cred != null) {
                    if (cred.smsCode != null) {
                        fieldVerificationCode.setText(cred.smsCode)
                    } else {
                        fieldVerificationCode.setText(R.string.instant_validation)
                    }
                }
            }
            STATE_SIGNIN_FAILED -> {
                enableViews(buttonVerifyPhone, buttonResend, fieldVerificationCode)
                disableViews(fieldPhoneNumber, buttonStartVerification)
            }
            STATE_SIGNIN_SUCCESS -> {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun transitionView(targetView: Int) {
        TransitionManager.beginDelayedTransition(root)
        if (currentView == VIEW_LOGIN && targetView == VIEW_VERIFY) {
            constraintVerify.applyTo(root)
            currentView = VIEW_VERIFY
            title = VERIFY
        } else if (currentView == VIEW_VERIFY && targetView == VIEW_LOGIN) {
            constraintPhone.applyTo(root)
            currentView = VIEW_LOGIN
            title = LOGIN
            updateUI(STATE_INITIALIZED)
        }
    }

    private fun validatePhoneNumber(): Boolean {
        val phoneNumber = fieldPhoneNumber.text.toString()
        if (TextUtils.isEmpty(phoneNumber)) {
            fieldPhoneNumber.error = "Invalid phone number."
            return false
        }
        return true
    }

    private fun enableViews(vararg views: View) {
        for (v in views) {
            v.isEnabled = true
        }
    }

    private fun disableViews(vararg views: View) {
        for (v in views) {
            v.isEnabled = false
        }
    }

    fun buttonStartVerificationClick(view: View) {
        if (!validatePhoneNumber()) {
            return
        }
        disableViews(buttonStartVerification, buttonVerifyPhone, buttonResend, fieldPhoneNumber,
            fieldVerificationCode)
        startPhoneNumberVerification(fieldPhoneNumber.text.toString())
    }

    fun buttonVerifyPhoneClick(view: View) {
        val code = fieldVerificationCode.text.toString()
        if (TextUtils.isEmpty(code)) {
            fieldVerificationCode.error = "Cannot be empty."
            return
        }
        disableViews(buttonStartVerification, buttonVerifyPhone, buttonResend, fieldPhoneNumber,
            fieldVerificationCode)
        verifyPhoneNumberWithCode(storedVerificationId, code)
    }

    fun buttonResendClick(view: View) {
        resendVerificationCode(fieldPhoneNumber.text.toString(), resendToken)
        disableViews(buttonStartVerification, buttonVerifyPhone, buttonResend, fieldPhoneNumber,
            fieldVerificationCode)
    }

    override fun onBackPressed() {
        if (currentView == VIEW_VERIFY) {
            transitionView(VIEW_LOGIN)
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        TrueSDK.getInstance().onActivityResultObtained( this, resultCode, data);
    }

    companion object {
        private const val TAG = "PhoneAuthActivity"
        private const val KEY_VERIFY_IN_PROGRESS = "key_verify_in_progress"
        private const val STATE_INITIALIZED = 1
        private const val STATE_VERIFY_FAILED = 3
        private const val STATE_VERIFY_SUCCESS = 4
        private const val STATE_CODE_SENT = 2
        private const val STATE_SIGNIN_FAILED = 5
        private const val STATE_SIGNIN_SUCCESS = 6
        private const val VIEW_LOGIN = 11
        private const val VIEW_VERIFY = 12
        private const val LOGIN = "Login"
        private const val VERIFY = "Verify"
    }
}
