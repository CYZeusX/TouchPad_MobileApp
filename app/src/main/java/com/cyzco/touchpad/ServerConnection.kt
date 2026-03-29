package com.cyzco.touchpad

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// the name of the preferences file
private val Context.dataStore by preferencesDataStore(name = "settings")

// the key to store the IP address
private val SERVER_IP_KEY = stringPreferencesKey("server_ip")

class ServerConnection(application: Application) : AndroidViewModel(application), IServerConnection
{
    // use the application context to get our DataStore
    private val dataStore = getApplication<Application>().dataStore

    private val _serverIp = MutableStateFlow("")
    override val serverIp = _serverIp.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected = _isConnected.asStateFlow()

    private var client: JavaUdpClient? = null
    private val serverPort = 42069
    private var sendJob: Job? = null

    init
    {
        viewModelScope.launch(Dispatchers.IO)
        {
            // read from DataStore, if it is null, use the default value
            val savedIp = dataStore.data.map()
            { preferences -> preferences[SERVER_IP_KEY] ?: _serverIp.value }.first()

            // Update the StateFlow on the main thread
            withContext(Dispatchers.Main)
            { _serverIp.value = savedIp }
        }
    }

    /**
     * Called when the text field changes.
     */
    override fun onIpChange(newIp: String)
    {
        // When new IP introduced, disconnect from the old one
        _serverIp.value = newIp
        disconnect()
    }

    /**
     * Called by the "Connect" button.
     */
    override fun connect()
    {
        viewModelScope.launch()
        {
            // Close old client if any
            client?.close()

            // Create the new client on the IO (network) thread
            val newClient = withContext(Dispatchers.IO)
            { JavaUdpClient(_serverIp.value, serverPort) }

            // Check if the client was created successfully
            if (newClient.isInitialized)
            {
                client = newClient
                _isConnected.value = true

                withContext(Dispatchers.IO)
                {
                    dataStore.edit{ preferences -> preferences[SERVER_IP_KEY] = _serverIp.value }
                }
            }

            else
            {
                client = null
                _isConnected.value = false
            }
        }
    }

    /**
     * Called by the "Disconnect" button.
     */
    override fun disconnect()
    {
        client?.close()
        client = null
        _isConnected.value = false
    }

    /**
     * Sends a command IF we are connected.
     */
    override fun sendCommand(command: String)
    {
        if (command.isBlank() || !_isConnected.value || client == null)
            return

        sendJob?.cancel()
        sendJob = viewModelScope.launch(Dispatchers.IO)
        { client?.sendCommand(command) }
    }

    override fun onCleared()
    {
        disconnect()
        super.onCleared()
    }
}