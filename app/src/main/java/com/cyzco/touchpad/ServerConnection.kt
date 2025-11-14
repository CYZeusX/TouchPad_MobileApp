package com.cyzco.touchpad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerConnection : ViewModel() {
    private val _serverIp = MutableStateFlow("10.103.206.119") // Change this default
    val serverIp = _serverIp.asStateFlow()

    // NEW: A state to show connection status
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private var client: JavaUdpClient? = null
    private val serverPort = 42069
    private var sendJob: Job? = null

    // We no longer connect in 'init'

    /**
     * Called when the text field changes.
     */
    fun onIpChange(newIp: String) {
        _serverIp.value = newIp
        // If user types a new IP, they are disconnected from the old one.
        disconnect()
    }

    /**
     * Called by the "Connect" button.
     */
    fun connect() {
        viewModelScope.launch {
            client?.close() // Close old client if any

            // Create the new client on the IO (network) thread
            val newClient = withContext(Dispatchers.IO) {
                JavaUdpClient(_serverIp.value, serverPort)
            }

            // Check if the client was created successfully
            if (newClient.isInitialized()) {
                client = newClient
                _isConnected.value = true
            } else {
                client = null
                _isConnected.value = false
            }
        }
    }

    /**
     * Called by the "Disconnect" button.
     */
    fun disconnect() {
        client?.close()
        client = null
        _isConnected.value = false
    }

    /**
     * Sends a command IF we are connected.
     */
    fun sendCommand(command: String) {
        if (command.isBlank() || !_isConnected.value || client == null) {
            return // Do nothing if not connected
        }

        sendJob?.cancel()
        sendJob = viewModelScope.launch(Dispatchers.IO) {
            client?.sendCommand(command)
        }
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}