package com.cyzco.touchpad

import kotlinx.coroutines.flow.MutableStateFlow

class VirtualServerConnection : IServerConnection
{
    override val serverIp = MutableStateFlow("10.103.206.119")
    override val isConnected = MutableStateFlow(true)

    override fun onIpChange(ip: String) {}
    override fun connect() {}
    override fun disconnect() {}
    override fun sendCommand(cmd: String) {}
}