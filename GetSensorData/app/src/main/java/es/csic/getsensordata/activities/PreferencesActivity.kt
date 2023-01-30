package es.csic.getsensordata.activities

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import es.csic.getsensordata.R
import es.csic.getsensordata.fragments.PreferencesFragment


class PreferencesActivity : AppCompatActivity(), /*FragmentManager.OnBackStackChangedListener,*/ PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(Tag, "onCreate(savedInstanceState=$savedInstanceState)")

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_preferences)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.preferences, PreferencesFragment())
                .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(Tag, "onOptionsItemSelected(item=$item)")

        when (item.itemId) {
            android.R.id.home -> {
                val fm = supportFragmentManager
                if (fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                    return true
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        super.onBackPressed()

        title = getString(R.string.preferencesActivityTitle)
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat?, pref: Preference?): Boolean {
        Log.d(Tag, "onPreferenceStartFragment(caller=$caller, pref=$pref)")

        val args = pref?.extras
        val fragment = pref?.fragment?.let {
            supportFragmentManager.fragmentFactory.instantiate(
                    classLoader,
                    it)
        }
        if (fragment != null) {
            fragment.arguments = args
            fragment.setTargetFragment(caller, 0)
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.preferences, fragment)
                    .addToBackStack("Camera Preferences")
                    .commit()
        }

        return true
    }

    companion object {
        private val Tag = PreferencesActivity::class.java.simpleName
    }
}
