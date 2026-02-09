package top.yling.ozx.guiagent.util

import android.content.Context

/**
 * Token Manager for storing and managing JWT tokens
 */
object TokenManager {

    private const val PREF_NAME = "auth_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USERNAME = "username"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_USER_PROFILE = "user_profile"

    /**
     * Save authentication data
     */
    fun saveAuthData(context: Context, token: String, username: String, nickname: String?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_USERNAME, username)
            putString(KEY_NICKNAME, nickname ?: username)
            apply()
        }
    }

    /**
     * Get stored token
     */
    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    /**
     * Get stored username
     */
    fun getUsername(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USERNAME, null)
    }

    /**
     * Get stored nickname
     */
    fun getNickname(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NICKNAME, null)
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(context: Context): Boolean {
        return getToken(context) != null
    }

    /**
     * Clear all authentication data (logout)
     */
    fun clearAuthData(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * Save user profile description
     */
    fun saveUserProfile(context: Context, profile: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_PROFILE, profile).apply()
    }

    /**
     * Get stored user profile description
     */
    fun getUserProfile(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_PROFILE, null)
    }
}
