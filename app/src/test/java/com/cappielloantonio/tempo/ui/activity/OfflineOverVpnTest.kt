package com.cappielloantonio.tempo.ui.activity

import android.content.ComponentName
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Looper
import android.view.View
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.util.NetworkUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/** Tailscale and other VPNs stay up with Wi-Fi and mobile data off, though nothing can be reached. */
// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30])
@RunWith(RobolectricTestRunner::class)
class OfflineOverVpnTest {
    private val manager get() = App.getContext().getSystemService(ConnectivityManager::class.java)
    private var controller: ActivityController<MainActivity>? = null
    private val activity get() = controller!!.get()

    // The fake manager finds the active network by its type, so each network's id is its type.
    private val wifi = network(ConnectivityManager.TYPE_WIFI, NetworkCapabilities.TRANSPORT_WIFI)
    private val mobile = network(ConnectivityManager.TYPE_MOBILE, NetworkCapabilities.TRANSPORT_CELLULAR)
    private val vpn = network(ConnectivityManager.TYPE_VPN, NetworkCapabilities.TRANSPORT_VPN)

    private class FakeNetwork(val network: Network, val info: NetworkInfo, val capabilities: NetworkCapabilities)

    private fun network(type: Int, transport: Int, internet: Boolean = true): FakeNetwork {
        val info = ShadowNetworkInfo.newInstance(
            NetworkInfo.DetailedState.CONNECTED, type, 0, true, NetworkInfo.State.CONNECTED,
        )
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addTransportType(transport)
        if (internet) {
            shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        return FakeNetwork(ShadowNetwork.newInstance(type), info, capabilities)
    }

    /** The first network listed is the active one, as a VPN is when it's on. */
    private fun connect(vararg networks: FakeNetwork) {
        shadowOf(manager).clearAllNetworks()
        for (n in networks) {
            shadowOf(manager).addNetwork(n.network, n.info)
            shadowOf(manager).setNetworkCapabilities(n.network, n.capabilities)
        }
        shadowOf(manager).setActiveNetworkInfo(networks.firstOrNull()?.info)
    }

    private fun disconnect(n: FakeNetwork) {
        shadowOf(manager).removeNetwork(n.network)
        shadowOf(manager).networkCallbacks.forEach { it.onLost(n.network) }
        idle()
    }

    private fun reconnect(n: FakeNetwork) {
        shadowOf(manager).addNetwork(n.network, n.info)
        shadowOf(manager).setNetworkCapabilities(n.network, n.capabilities)
        shadowOf(manager).networkCallbacks.forEach { it.onAvailable(n.network) }
        idle()
    }

    @Before
    fun setUp() {
        AppTestEnv.install()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))
    }

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        AppTestEnv.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun launch() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
    }

    private fun showsOffline(): Boolean {
        val banner = activity.bind.offlineModeTextView.visibility == View.VISIBLE
        assertEquals("banner and server state agree", banner, activity.serverReachable.value == false)
        return banner
    }

    @Test
    fun aVpnWithNothingUnderItIsOffline() {
        connect(vpn)

        assertTrue(NetworkUtil.isOffline())
        assertTrue(NetworkUtil.isServerUnreachable())
    }

    @Test
    fun aVpnOverWifiIsOnline() {
        connect(vpn, wifi)

        assertFalse(NetworkUtil.isOffline())
    }

    @Test
    fun aVpnOverMobileDataIsOnline() {
        connect(vpn, mobile)

        assertFalse(NetworkUtil.isOffline())
    }

    @Test
    fun plainWifiIsStillOnline() {
        connect(wifi)

        assertFalse(NetworkUtil.isOffline())
    }

    @Test
    fun realNetworksLeaveOutVpnsAndNetworksWithoutInternet() {
        val noInternet = network(ConnectivityManager.TYPE_ETHERNET, NetworkCapabilities.TRANSPORT_ETHERNET, internet = false)
        connect(vpn, wifi, noInternet, mobile)

        assertEquals(setOf(wifi.network, mobile.network), NetworkUtil.realNetworks(manager).toSet())
    }

    @Test
    fun openingTheAppWithOnlyTheVpnShowsOfflineStraightAway() {
        connect(vpn)

        launch()

        assertTrue(showsOffline())
    }

    @Test
    fun openingTheAppOverWifiShowsOnline() {
        connect(vpn, wifi)

        launch()

        assertFalse(showsOffline())
    }

    @Test
    fun turningOffWifiWithTheVpnOnGoesOfflineStraightAway() {
        connect(vpn, wifi)
        launch()

        disconnect(wifi)

        assertTrue(showsOffline())
        assertTrue(NetworkUtil.isServerUnreachable())
    }

    @Test
    fun itStaysOnlineWhileMobileDataIsLeft() {
        connect(vpn, wifi, mobile)
        launch()

        disconnect(wifi)

        assertFalse(showsOffline())
    }

    @Test
    fun turningOffBothGoesOfflineWhenTheLastOneGoes() {
        connect(vpn, wifi, mobile)
        launch()

        disconnect(wifi)
        assertFalse(showsOffline())
        disconnect(mobile)

        assertTrue(showsOffline())
    }

    @Test
    fun wifiComingBackGoesOnlineAgain() {
        connect(vpn, wifi)
        launch()
        disconnect(wifi)

        reconnect(wifi)

        assertFalse(showsOffline())
        assertFalse(NetworkUtil.isServerUnreachable())
    }

    @Test
    fun withoutAVpnLosingTheLastNetworkStillGoesOffline() {
        connect(wifi)
        launch()

        disconnect(wifi)

        assertTrue(showsOffline())
    }

    @Test
    fun anotherNetworkJoiningDoesNotOverrideAFailedPing() {
        connect(vpn, wifi)
        launch()
        NetworkUtil.setServerReachable(false)

        reconnect(mobile)

        assertTrue(NetworkUtil.isServerUnreachable())
    }

    @Test
    fun itStopsListeningWhenClosed() {
        connect(vpn, wifi)
        launch()
        assertEquals(1, shadowOf(manager).networkCallbacks.size)

        controller!!.pause().stop().destroy()
        controller = null

        assertTrue(shadowOf(manager).networkCallbacks.isEmpty())
    }
}
