package com.cyzco.touchpad

import kotlinx.coroutines.flow.StateFlow

interface IServerConnection
{
    val serverIp: StateFlow<String>
    val isConnected: StateFlow<Boolean>

    fun onIpChange(ip: String)
    fun connect()
    fun disconnect()
    fun sendCommand(cmd: String)
}