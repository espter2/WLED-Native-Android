package ca.cgagnier.wlednativeandroid.service

import android.graphics.Color
import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.DevicesApplication
import ca.cgagnier.wlednativeandroid.service.api.JsonApi
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object DeviceApi {
    private const val TAG = "DeviceApi"
    private var application: DevicesApplication? = null
    @OptIn(DelicateCoroutinesApi::class)
    private val scope = CoroutineScope(newSingleThreadContext(TAG))

    fun setApplication(devicesApplication: DevicesApplication) {
        application = devicesApplication
    }

    fun update(device: Device, silentUpdate: Boolean) {
        if (!silentUpdate) {
            val newDevice = device.copy(isRefreshing = true)

            scope.launch {
                application!!.repository.update(newDevice)
            }
        }

        val stateInfoCall = getJsonApi(device).getStateInfo()
        stateInfoCall.enqueue(object : Callback<DeviceStateInfo> {
            override fun onResponse(call: Call<DeviceStateInfo>, response: Response<DeviceStateInfo>) =
                onSuccess(device, response)

            override fun onFailure(call: Call<DeviceStateInfo>, t: Throwable) =
                onFailure(device, t)
        })
    }

    fun postJson(device: Device, jsonData: JsonPost) {
        Log.d(TAG, "Posting update to device [${device.address}]")

        val stateInfoCall = getJsonApi(device).postJson(jsonData)
        stateInfoCall.enqueue(object : Callback<DeviceStateInfo> {
            override fun onResponse(call: Call<DeviceStateInfo>, response: Response<DeviceStateInfo>) =
                onSuccess(device, response)

            override fun onFailure(call: Call<DeviceStateInfo>, t: Throwable) =
                onFailure(device, t)
        })
    }

    private fun getJsonApi(device: Device): JsonApi {
        // TODO show invalid URL in the interface (How to reproduce: add url "a b")
        return Retrofit.Builder()
            .baseUrl(device.getDeviceUrl())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(JsonApi::class.java)
    }

    private fun onFailure(device: Device, t: Throwable? = null) {
        if (t != null) {
            Log.e(TAG, t.message!!)
        }
        val updatedDevice = device.copy(isOnline = false, isRefreshing = false)
        scope.launch {
            application!!.repository.update(updatedDevice)
        }
    }

    fun onSuccess(device: Device, response: Response<DeviceStateInfo>) {
        if (response.code() == 200) {
            val deviceStateInfo = response.body()!!
            val colorInfo = deviceStateInfo.state.segment?.get(0)?.colors?.get(0)

            val updatedDevice = device.copy(
                isOnline = true,
                name = if (device.isCustomName) device.name else deviceStateInfo.info.name,
                brightness = if (device.isSliding) device.brightness else deviceStateInfo.state.brightness,
                isPoweredOn = deviceStateInfo.state.isOn,
                color = if (colorInfo != null) Color.rgb(
                    colorInfo[0],
                    colorInfo[1],
                    colorInfo[2]
                ) else Color.WHITE,
                isRefreshing = false,
                networkRssi = deviceStateInfo.info.wifi.rssi ?: 0
            )

            if (updatedDevice != device) {
                scope.launch {
                    application!!.repository.update(updatedDevice)
                }
            }
        } else {
            onFailure(device)
        }
    }
}
