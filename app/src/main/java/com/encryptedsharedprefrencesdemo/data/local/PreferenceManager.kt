package com.encryptedsharedprefrencesdemo.data.local

class PreferenceManager(
    private val sharedPreferences: EncPref
) : Preference {

    companion object {
        private const val IS_LOGIN = "pref_is_login"
        private const val USER_ID = "user_id"
    }

    override fun setLogin(isLogin: Boolean) {
        sharedPreferences.putBoolean(IS_LOGIN, isLogin)
    }

    override fun isLogin(): Boolean {
        return sharedPreferences.getBoolean(IS_LOGIN, false)
    }

    override fun setUserId(customerId: String) {
        sharedPreferences.putString(USER_ID, customerId)
    }

    override fun getUserId(): String {
        return sharedPreferences.getString(USER_ID, "")
    }
}