package es.csic.getsensordata.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import es.csic.getsensordata.R

const val PERMISSIONS_REQUEST = 0

class PermissionsActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    companion object {
        val requiredPermissions = arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
        )
        private val TAG = PermissionsActivity::class.qualifiedName
    }


    private lateinit var layout: View
    private var missingPermissions = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        layout = findViewById(R.id.permissions_activity_layout)

        val buttonRequestPermissions = findViewById(R.id.requestPermissionsButton) as Button
        buttonRequestPermissions.setOnClickListener {
            Log.d(TAG, "buttonRequestPermissions touched")
            fillMissingPermissions()
            if (missingPermissions.isNotEmpty()) {
                requestMissingPermissions()
            } else {
                showMainActivity()
            }
        }
    }

    private fun fillMissingPermissions() {
        Log.d(TAG, "fillMissingPermissions")
        missingPermissions.clear()
        for (requiredPermission in requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(this, requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(requiredPermission)
            }
        }
    }

    private fun requestMissingPermissions() {
        Log.d(TAG, "requestMissingPermissions")
        ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSIONS_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "onRequestPermissionsResult")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST) {
            if (permissions.size == missingPermissions.size && grantResults.size == missingPermissions.size) {
                var allPermissionsGranted = true
                for (grantResult in grantResults) {
                    if (grantResult == PackageManager.PERMISSION_DENIED) {
                        allPermissionsGranted = false
                        break
                    }
                }
                if (allPermissionsGranted) {
                    showMainActivity()
                } else {
                    Log.d(TAG, "not all permissions granted")
                }
            } else {
                Log.d(TAG, "missing permissions do not match returned permissions")
            }
        } else {
            Log.d(TAG, "wrong requestCode")
        }
    }

    private fun showMainActivity() {
        Log.d(TAG, "showMainActivity")
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        finish()
        startActivity(intent)
    }
}
