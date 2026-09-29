package tk.glucodata

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.wearable.Node

object WatchBridge {
    private const val LOG_ID = "WatchBridge"

    fun isWearOsEnabled(): Boolean {
        return try {
            Applic.useWearos()
        } catch (_: Throwable) {
            false
        }
    }

    fun setWearOsEnabled(context: Context, enabled: Boolean) {
        try {
            val manager = context.packageManager
            val component = ComponentName(context, MessageReceiver::class.java)
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            manager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            if (enabled) {
                MessageSender.initwearos(context.applicationContext)
                val sender = MessageSender.getMessageSender()
                sender?.finddevices()
                Natives.switchSync()
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, th)
        }
    }

    private const val TRANSPORT_PREFS = "wear_transport"
    private const val MESSAGES_MIGRATED = "messages_default_v1"

    fun getWatchTransport(mirrorIndex: Int): Int {
        if (mirrorIndex < 0) return BleMirror.TRANSPORT_MESSAGES
        return try {
            Natives.getbackuptransport(mirrorIndex)
        } catch (_: Throwable) {
            BleMirror.TRANSPORT_AUTOMATIC
        }
    }

    /** Saves the carrier locally and sends it to the watch over the existing link. */
    fun setWatchTransport(label: String, mirrorIndex: Int, transport: Int): Boolean {
        if (mirrorIndex < 0) return false
        return try {
            val bleClient = Natives.getbackupbleclient(mirrorIndex)
            val applied = Natives.setMirrorTransport(label, transport, bleClient)
            if (applied)
                BleMirror.configurationChanged(mirrorIndex, true)
            applied
        } catch (th: Throwable) {
            Log.stack(LOG_ID, th)
            false
        }
    }

    /**
     * One-time switch of existing Automatic watch rows to Messages. Automatic
     * prefers TCP to the peer's last LAN address and stalls or flaps when away
     * from home; Messages always uses the Wear data layer. Runs on phone and
     * watch so both sides converge even if one was unreachable.
     */
    fun migrateWatchesToMessages(context: Context) {
        val prefs = context.getSharedPreferences(TRANSPORT_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MESSAGES_MIGRATED, false)) return
        try {
            for (i in 0 until Natives.backuphostNr()) {
                if (!Natives.isWearOS(i) || Natives.getHostDeactivated(i)) continue
                if (Natives.getbackuptransport(i) != BleMirror.TRANSPORT_AUTOMATIC) continue
                val label = Natives.getbackuplabel(i) ?: continue
                val applied = setWatchTransport(label, i, BleMirror.TRANSPORT_MESSAGES)
                Log.i(LOG_ID, "Watch mirror $label($i) Automatic -> Messages: $applied")
            }
            prefs.edit().putBoolean(MESSAGES_MIGRATED, true).apply()
        } catch (th: Throwable) {
            Log.stack(LOG_ID, th)
        }
    }

    fun getWearNodes(): List<Node> {
        val sender = MessageSender.getMessageSender() ?: return emptyList()
        return sender.nodes?.toList() ?: emptyList()
    }

    fun getBleWatchLabels(): List<String> {
        return try {
            BleMirror.wearControlLabels().toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun initWatchApp(nodeId: String, isGalaxy: Boolean) {
        try {
            val sender = MessageSender.getMessageSender() ?: return
            Natives.resetbylabel(nodeId, isGalaxy)
            sender.startWearOSActivity(nodeId)
            MessageSender.sendnetinfo(nodeId)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, th)
        }
    }

    fun getNotifyWatch(): Boolean = try { Notify.alertwatch } catch (_: Throwable) { false }
    fun isGalaxyDefault(): Boolean = try { Applic.ALLGALAXY } catch (_: Throwable) { false }

    fun syncWatch(nodeId: String) {
        try {
            MessageSender.sendnetinfo(nodeId)
            Applic.switchSync()
            MessageSender.reinit()
        } catch (th: Throwable) {
            Log.stack(LOG_ID, th)
        }
    }

    fun resetWatchDefaults(nodeId: String, isGalaxy: Boolean, context: Context) {
        try {
            val sender = MessageSender.getMessageSender()
            sender?.toDefaults(nodeId)
            Natives.setWearosdefaults(nodeId, isGalaxy)
            Applic.setbluetooth(MainActivity.thisone ?: context, true)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, th)
        }
    }

    fun setWatchDirectSensor(nodeId: String, direct: Boolean, isGalaxy: Boolean, hasWatchNums: Boolean) {
        try {
            val netinfo = Natives.getmynetinfo(
                nodeId,
                true,
                if (direct) 1 else -1,
                isGalaxy,
                if (hasWatchNums) 1 else -1,
                false
            )
            Applic.switchbluetooth(nodeId, netinfo, direct)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, th)
        }
    }

    fun setWatchEnterNums(nodeId: String, watchNums: Boolean, direct: Boolean, isGalaxy: Boolean) {
        try {
            val netinfo = Natives.getmynetinfo(
                nodeId,
                true,
                if (direct) 1 else -1,
                isGalaxy,
                if (watchNums) 1 else -1,
                false
            )
            Applic.sendbluetooth(nodeId, netinfo, false)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, th)
        }
    }

    fun searchWatches() {
        try {
            val sender = MessageSender.getMessageSender()
            sender?.finddevices()
            BleMirror.networkChanged()
            MessageSender.reinit()
        } catch (th: Throwable) {
            Log.stack(LOG_ID, th)
        }
    }

    fun setWatchdrip(enabled: Boolean) {
        try {
            Natives.setwatchdrip(enabled)
            val cls = Class.forName("tk.glucodata.watchdrip")
            cls.getMethod("set", Boolean::class.javaPrimitiveType).invoke(null, enabled)
        } catch (_: Throwable) {}
    }

    fun setGadgetbridge(enabled: Boolean) {
        try {
            Natives.setgadgetbridge(enabled)
            SuperGattCallback.doGadgetbridge = enabled
        } catch (_: Throwable) {}
    }

    fun setGarmin(enabled: Boolean) {
        try {
            Natives.setusegarmin(enabled)
        } catch (_: Throwable) {}
    }

    fun setSeparateAlerts(enabled: Boolean) {
        try {
            Notify.alertseparate = enabled
            Natives.setSeparate(enabled)
        } catch (_: Throwable) {}
    }

    fun setNotifyWatch(enabled: Boolean) {
        try {
            Applic.app.setnotify(enabled)
        } catch (_: Throwable) {}
    }
}
