package com.johnstrack.firebasesocial

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import com.twitter.sdk.android.core.*
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private val GOOGLE_SIGNIN_CODE = 1
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var callbackManager: CallbackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TWITTER CONFIG
        val authConfig = TwitterAuthConfig(getString(R.string.CONSUMER_KEY), getString(R.string.CONSUMER_SECRET))
        val twitterConfig = TwitterConfig.Builder(this)
                .twitterAuthConfig(authConfig)
                .build()
        Twitter.initialize(twitterConfig)

        setContentView(R.layout.activity_main)

        // Configure GOOGLE Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

        // Build a GoogleSignInClient with the options specified by gso.
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        auth = FirebaseAuth.getInstance()

        googleSigninBtn.setOnClickListener {
            val signinIntent = googleSignInClient.signInIntent
            startActivityForResult(signinIntent, GOOGLE_SIGNIN_CODE)
        }

        // FACEBOOK
        callbackManager = CallbackManager.Factory.create()
        facebookLoginBtn.setReadPermissions("email", "public_profile")
        facebookLoginBtn.registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult?) {
                if (result != null) {
                    val credential = FacebookAuthProvider.getCredential(result.accessToken.token)
                    firebaseLogin(credential)
                }
            }

            override fun onCancel() {
                Log.d("Cancel", "Facebook login cancelled")
                updateUI(null)
            }

            override fun onError(error: FacebookException?) {
                Log.e("Error", "Facbook login failed")
                updateUI(null)
            }

        })

        // TWITTER
        twitterLoginBtn.callback = object : Callback<TwitterSession>() {
            override fun success(result: Result<TwitterSession>?) {
                val session = result?.data ?: return updateUI(null)
                val credential = TwitterAuthProvider.getCredential(session.authToken.token, session.authToken.secret)
                firebaseLogin(credential)
            }

            override fun failure(exception: TwitterException?) {
                Log.e("ERROR", "Twitter login failed")
                updateUI(null)
            }

        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode === GOOGLE_SIGNIN_CODE) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
                firebaseLogin(credential)
            } catch (e: ApiException) {
                Log.e("Error", "Could not sign in Google: ${e.statusCode}")
            }
        } else if (FacebookSdk.isFacebookRequestCode(requestCode)) {
            callbackManager.onActivityResult(requestCode, resultCode, data)
        } else if (requestCode == TwitterAuthConfig.DEFAULT_AUTH_REQUEST_CODE){
            twitterLoginBtn.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun firebaseLogin (credential: AuthCredential) {
        Log.i("INFORMATION", "Credentials: ${credential.toString()}")
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                updateUI(auth.currentUser)
            } else {
                Log.e("Error", "Sign in with Firebase failed: ${task.exception}")
                Toast.makeText(this, "Authentication fialed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            userIdLbl.text = "Welcome user: ${user.uid}"
        } else {
            userIdLbl.text = "No one logged in."
        }
    }

    fun logoutButtonClicked (view: View) {

        val user = auth.currentUser ?: return updateUI(null)

        for (info in user.providerData) {
            when (info.providerId) {
                GoogleAuthProvider.PROVIDER_ID -> googleSignInClient.signOut()
                FacebookAuthProvider.PROVIDER_ID -> LoginManager.getInstance().logOut()
                TwitterAuthProvider.PROVIDER_ID -> TwitterCore.getInstance().sessionManager.clearActiveSession()
            }
        }

        auth.signOut()
        updateUI(null)
    }
}