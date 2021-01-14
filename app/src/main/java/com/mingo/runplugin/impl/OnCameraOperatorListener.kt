package com.mingo.runplugin.impl

interface OnCameraOperatorListener {
    fun onClick()

    fun onPressStart()

    fun onFinish(time: Int)

}