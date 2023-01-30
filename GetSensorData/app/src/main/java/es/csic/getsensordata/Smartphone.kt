package es.csic.getsensordata

import android.content.Context

/**
 * Offer smartphone details in an static manner, without having to create an instance of the class.
 *
 * This is done in three ways:
 * - via its properties.
 * - through the method `getSummary()` that composes an string with all the details.
 * - transforming the object into its string representation.
 */
class Smartphone {
    companion object {
        val manufacturer: String = android.os.Build.MANUFACTURER
        val model: String = android.os.Build.MODEL
        val sdkVersion: Int = android.os.Build.VERSION.SDK_INT
        val releaseVersion: String = android.os.Build.VERSION.RELEASE

        /**
         * Prepare a string with the smartphone details, and a prologue taken from the string
         * resources.
         *
         * @param context: a suitable app context to access string resources.
         *
         * @return Smartphone details, with a prologue taken from the string resources prepended.
         */
        fun getSummary(context: Context): String {
            return context.getString(
                R.string.phoneSummary,
                manufacturer,
                model,
                sdkVersion,
                releaseVersion
            )
        }

        /**
         * Transform the object to a suitable string representation. This can be used in logs.
         *
         * @return String representation of the smartphone details.
         */
        override fun toString(): String {
            return "Smartphone(\n" +
                    "- manufacturer: $manufacturer\n" +
                    "- model: $model\n" +
                    "- sdkVersion: $sdkVersion\n" +
                    "- releaseVersion: $releaseVersion\n" +
                    ")"
        }
    }
}
