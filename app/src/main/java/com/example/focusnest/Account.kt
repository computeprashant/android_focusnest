package com.example.focusnest

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

/**
 * Google sign-in (through Firebase Auth) and the points database (Cloud Firestore).
 * Points are stored per user: a running total, and a map of points per day for the leaderboard.
 */
object Account {
    private const val USERS = "users"

    fun currentUser(): FirebaseUser? = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    fun signInIntent(ctx: Context): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId(ctx))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(ctx, options).signInIntent
    }

    /** Exchanges the Google account for a Firebase user. [onDone] runs on the main thread. */
    fun handleSignInResult(data: Intent?, onDone: (Result<FirebaseUser>) -> Unit) {
        runCatching {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential).addOnCompleteListener { task ->
                val user = task.result?.user
                if (task.isSuccessful && user != null) {
                    createProfile(user)
                    onDone(Result.success(user))
                } else {
                    onDone(Result.failure(task.exception ?: IllegalStateException("Sign-in failed")))
                }
            }
        }.onFailure { onDone(Result.failure(it)) }
    }

    fun signOut(ctx: Context) {
        runCatching { FirebaseAuth.getInstance().signOut() }
        runCatching {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(ctx, options).signOut()
        }
    }

    /**
     * Adds the points earned since the last sync to the signed-in user's total and today's entry.
     * Does nothing when signed out or when no points were earned.
     */
    fun recordPoints(totalPoints: Int, todayPoints: Int) {
        val user = currentUser() ?: return
        if (totalPoints == 0 && todayPoints == 0) return
        runCatching {
            db().collection(USERS).document(user.uid).set(
                mapOf(
                    "total" to FieldValue.increment(totalPoints.toLong()),
                    "daily" to mapOf(AppSettings.todayKey() to FieldValue.increment(todayPoints.toLong()))
                ),
                SetOptions.merge()
            )
        }
    }

    /** Top users by today's points. Firestore keeps the write offline and sends it when online. */
    fun leaderboard(onResult: (Result<List<Pair<String, Int>>>) -> Unit) {
        val today = AppSettings.todayKey()
        val field = FieldPath.of("daily", today)
        runCatching {
            db().collection(USERS)
                .orderBy(field, Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener { snapshot ->
                    val rows = snapshot.documents.map { doc ->
                        (doc.getString("name") ?: "Student") to ((doc.get(field) as? Number)?.toInt() ?: 0)
                    }
                    onResult(Result.success(rows))
                }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }.onFailure { onResult(Result.failure(it)) }
    }

    private fun createProfile(user: FirebaseUser) {
        runCatching {
            db().collection(USERS).document(user.uid).set(
                mapOf(
                    "name" to (user.displayName ?: "Student"),
                    // increment(0) keeps an existing total and creates a zero total for new users
                    "total" to FieldValue.increment(0L)
                ),
                SetOptions.merge()
            )
        }
    }

    private fun db(): FirebaseFirestore = FirebaseFirestore.getInstance()

    /** Read from the google-services.json file. The lookup returns 0 if that file is missing. */
    private fun webClientId(ctx: Context): String {
        val id = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        if (id == 0) throw IllegalStateException("Add google-services.json from Firebase to enable sign-in")
        return ctx.getString(id)
    }
}
