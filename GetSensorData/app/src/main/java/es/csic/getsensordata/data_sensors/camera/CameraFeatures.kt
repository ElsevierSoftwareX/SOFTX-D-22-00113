package es.csic.getsensordata.data_sensors.camera

data class CameraFeatures(
    val id: String,
    val infoSupportedHardwareLevel: SupportedHardwareLevel?,
    val lensFacing: LensFacing?
) {
    companion object {
        interface CameraFeaturesEnum {
            val id: Int
        }

        enum class SupportedHardwareLevel(override val id: Int): CameraFeaturesEnum {
            Limited(0),
            Full(1),
            Legacy(2),
            Three(3),
            External(4);
        }

        enum class LensFacing(override val id: Int): CameraFeaturesEnum {
            Front(0),
            Back(1),
            External(2);
        }

        inline fun <reified T> getEnum(id: Int?): T? where T : Enum<T>, T : CameraFeaturesEnum {
            return enumValues<T>().find { it.id == id }
        }
    }
}
