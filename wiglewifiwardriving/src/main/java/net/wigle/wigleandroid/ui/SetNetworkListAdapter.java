package net.wigle.wigleandroid.ui;

import android.bluetooth.BluetoothClass;
import android.content.Context;
import android.graphics.Color;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.wigle.wigleandroid.AbstractListAdapter;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.OUI;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * the array adapter for a list of networks.
 * note: separators aren't drawn if areAllItemsEnabled or isEnabled are false
 */
public final class SetNetworkListAdapter extends AbstractListAdapter<Network> {
    //color by signal strength
    private static final int COLOR_1 = Color.rgb(70, 170, 0);
    private static final int COLOR_2 = Color.rgb(170, 170, 0);
    private static final int COLOR_3 = Color.rgb(170, 95, 30);
    private static final int COLOR_4 = Color.rgb(180, 60, 40);
    private static final int COLOR_5 = Color.rgb(180, 45, 70);

    private static final int COLOR_1A = Color.argb(128, 70, 170, 0);
    private static final int COLOR_2A = Color.argb(128, 170, 170, 0);
    private static final int COLOR_3A = Color.argb(128, 170, 95, 30);
    private static final int COLOR_4A = Color.argb(128, 180, 60, 40);
    private static final int COLOR_5A = Color.argb(128, 180, 45, 70);

    private final SimpleDateFormat format;

    private final SetBackedNetworkList networks = new SetBackedNetworkList();

    public SetNetworkListAdapter(final Context context, final int rowLayout) {
        super(context, rowLayout);
        format = getConstructionTimeFormater(context);
        if (ListFragment.lameStatic.oui == null) {
            ListFragment.lameStatic.oui = new OUI(context.getAssets());
        }
    }

    public static SimpleDateFormat getConstructionTimeFormater(final Context context) {
        final int value = Settings.System.getInt(context.getContentResolver(), Settings.System.TIME_12_24, -1);
        SimpleDateFormat format;
        if (value == 24) {
            format = new SimpleDateFormat("H:mm:ss", Locale.getDefault());
        } else {
            format = new SimpleDateFormat("h:mm:ss a", Locale.getDefault());
        }
        return format;
    }

    public void clearWifiAndCell() {
        networks.clearWifiAndCell();
        notifyDataSetChanged();
    }

    public void clearWifi() {
        networks.clearWifi();
        notifyDataSetChanged();
    }

    public void clearCell() {
        networks.clearCell();
        notifyDataSetChanged();
    }

    public void clearBluetooth() {
        networks.clearBluetooth();
        notifyDataSetChanged();
    }

    public void clearBluetoothLe() {
        networks.clearBluetoothLe();
        notifyDataSetChanged();
    }

    public void morphBluetoothToLe(Network n) {
        networks.morphBluetoothToLe(n);
        notifyDataSetChanged();
    }

    public  void clear() {
        networks.clear();
        notifyDataSetChanged();
    }

    public void addWiFi(Network n) {
        networks.addWiFi(n);
        notifyDataSetChanged();
    }

    public void addCell(Network n) {
        networks.addCell(n);
        notifyDataSetChanged();
    }

    public void addBluetooth(Network n) {
        networks.addBluetooth(n);
        notifyDataSetChanged();
    }

    public void addBluetoothLe(Network n) {
        networks.addBluetoothLe(n);
        notifyDataSetChanged();
    }

    public void enqueueBluetooth(Network n) {
        networks.enqueueBluetooth(n);
    }

    public void enqueueBluetoothLe(Network n) {
        networks.enqueueBluetoothLe(n);
    }

    //TODO: almost certainly the source of our duplicate BT nets in non show-current
    public void batchUpdateBt(final boolean showCurrent, final boolean updateLe, final boolean updateClassic) {

        networks.batchUpdateBt(showCurrent,updateLe,updateClassic);
        notifyDataSetChanged();
    }

    @Override
    public  boolean isEmpty() {
        return super.isEmpty();
    }

    @Override
    public  int getCount() {
        return networks.size();
    }

    @Override
    public  Network getItem(int pPosition) {
        return networks.get(pPosition);
    }

    @Override
    public  long getItemId(int pPosition) {
        try {
            //should i just hash the object?
            return networks.get(pPosition).getBssid().hashCode();
        }
        catch (final IndexOutOfBoundsException ex) {
            MainActivity.info("index out of bounds on getItem: " + pPosition + " ex: " + ex, ex);
        }
        return 0L;
    }

    @Override
    public  boolean hasStableIds() {
        return true;
    }

    public void sort(Comparator comparator) {
        Collections.sort(networks, comparator);
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        // long start = System.currentTimeMillis();
        View row;

        if (null == convertView) {
            row = mInflater.inflate(R.layout.row, parent, false);
        } else {
            row = convertView;
        }

        Network network;
        try {
            network = getItem(position);
        } catch (final IndexOutOfBoundsException ex) {
            // yes, this happened to someone
            MainActivity.info("index out of bounds: " + position + " ex: " + ex);
            return row;
        }
        // info( "listing net: " + network.getBssid() );

        final ImageView ico = (ImageView) row.findViewById(R.id.wepicon);
        ico.setImageResource(getImage(network));

        final ImageView btico = (ImageView) row.findViewById(R.id.bticon);
        if (NetworkType.BT.equals(network.getType()) || NetworkType.BLE.equals(network.getType())) {
            btico.setVisibility(View.VISIBLE);
            Integer btImageId = getBtImage(network);
            if (null == btImageId) {
                btico.setVisibility(View.GONE);
            } else {
                btico.setImageResource(btImageId);
            }
        } else {
            btico.setVisibility(View.GONE);
        }

        TextView tv = (TextView) row.findViewById(R.id.ssid);
        tv.setText(network.getSsid() + " ");

        tv = (TextView) row.findViewById(R.id.oui);
        final String ouiString = network.getOui(ListFragment.lameStatic.oui);
        final String sep = ouiString.length() > 0 ? " - " : "";
        tv.setText(ouiString + sep);

        tv = (TextView) row.findViewById(R.id.time);
        tv.setText(getConstructionTime(format, network));

        tv = (TextView) row.findViewById(R.id.level_string);
        final int level = network.getLevel();
        tv.setTextColor(getSignalColor(level));
        tv.setText(Integer.toString(level));

        tv = (TextView) row.findViewById(R.id.detail);
        String det = network.getDetail();
        tv.setText(det);
        // status( position + " view done. ms: " + (System.currentTimeMillis() - start ) );

        return row;
    }

    public static String getConstructionTime(final SimpleDateFormat format, final Network network) {
        return format.format(new Date(network.getConstructionTime()));
    }

    public static int getSignalColor(final int level) {
        return getSignalColor(level, false);
    }

    public static int getSignalColor(final int level, final boolean alpha) {
        int color = alpha ? COLOR_1A : COLOR_1;
        if (level <= -90) {
            color = alpha ? COLOR_5A : COLOR_5;
        } else if (level <= -80) {
            color = alpha ? COLOR_4A : COLOR_4;
        } else if (level <= -70) {
            color = alpha ? COLOR_3A : COLOR_3;
        } else if (level <= -60) {
            color = alpha ? COLOR_2A : COLOR_2;
        }

        return color;
    }

    public static int getImage(final Network network) {
        int resource;
        if (network.getType().equals(NetworkType.WIFI)) {
            switch (network.getCrypto()) {
                case Network.CRYPTO_WEP:
                    resource = R.drawable.wep_ico;
                    break;
                case Network.CRYPTO_WPA2:
                    resource = R.drawable.wpa2_ico;
                    break;
                case Network.CRYPTO_WPA:
                    resource = R.drawable.wpa_ico;
                    break;
                case Network.CRYPTO_NONE:
                    resource = R.drawable.no_ico;
                    break;
                default:
                    throw new IllegalArgumentException("unhanded crypto: " + network.getCrypto()
                            + " in network: " + network);
            }
        } else if (NetworkType.BT.equals(network.getType())) {
            resource = R.drawable.bt_ico;
        } else if (NetworkType.BLE.equals(network.getType())) {
            resource = R.drawable.btle_ico;
        } else {
            resource = R.drawable.tower_ico;
        }

        return resource;
    }

    public static Integer getBtImage(final Network network) {
        Integer resource;
        switch (network.getFrequency()) {
            case BluetoothClass.Device.AUDIO_VIDEO_CAMCORDER:
                resource = R.drawable.av_camcorder_pro_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                resource = R.drawable.av_car_f_smile;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                resource = R.drawable.av_handsfree_headset_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
                resource = R.drawable.av_headphone_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
                resource = R.drawable.av_hifi_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER:
                resource = R.drawable.av_speaker_f_detailed;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE:
                resource = R.drawable.av_mic_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO:
                resource = R.drawable.av_boombox_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX:
                resource = R.drawable.av_settop_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED:
                resource = R.drawable.av_receiver_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VCR:
                resource = R.drawable.av_vcr_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CAMERA:
                resource = R.drawable.av_camcorder_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CONFERENCING:
                resource = R.drawable.av_conference;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER:
                resource = R.drawable.av_receiver_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_GAMING_TOY:
                resource = R.drawable.av_toy;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR:
                resource = R.drawable.av_monitor;
                break;
            case BluetoothClass.Device.COMPUTER_DESKTOP:
                resource = R.drawable.comp_desk_f;
                break;
            case BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA:
                resource = R.drawable.comp_handheld;
                break;
            case BluetoothClass.Device.COMPUTER_LAPTOP:
                resource = R.drawable.comp_laptop;
                break;
            case BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA:
                resource = R.drawable.comp_laptop_sm;
                break;
            case BluetoothClass.Device.COMPUTER_SERVER:
                resource = R.drawable.comp_server_f;
                break;
            case BluetoothClass.Device.COMPUTER_UNCATEGORIZED:
                resource = R.drawable.comp_server_desk_f;
                break;
            case BluetoothClass.Device.COMPUTER_WEARABLE:
                resource = R.drawable.comp_ar_f;
                break;
            case BluetoothClass.Device.HEALTH_BLOOD_PRESSURE:
                resource = R.drawable.med_heart;
                break;
            case BluetoothClass.Device.HEALTH_DATA_DISPLAY:
                resource = R.drawable.med_heart_display_o;
                break;
            case BluetoothClass.Device.HEALTH_PULSE_OXIMETER:
            case BluetoothClass.Device.HEALTH_PULSE_RATE:
                resource = R.drawable.med_heart;
                break;
            case BluetoothClass.Device.HEALTH_GLUCOSE:
            case BluetoothClass.Device.HEALTH_THERMOMETER:
            case BluetoothClass.Device.HEALTH_UNCATEGORIZED:
                resource = R.drawable.med_cross_f;
                break;
            case BluetoothClass.Device.HEALTH_WEIGHING:
                resource = R.drawable.med_scale_f;
                break;
            case BluetoothClass.Device.PHONE_CELLULAR:
                resource = R.drawable.tel_cell;
                break;
            case BluetoothClass.Device.PHONE_CORDLESS:
                resource = R.drawable.tel_cordless_1;
                break;
            case BluetoothClass.Device.PHONE_ISDN:
                resource = R.drawable.tel_isdn;
                break;
            case BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY:
                resource = R.drawable.tel_modem;
                break;
            case BluetoothClass.Device.PHONE_SMART:
                resource = R.drawable.comp_handheld;
                break;
            case BluetoothClass.Device.PHONE_UNCATEGORIZED:
                resource = R.drawable.tel_phone_2;
                break;
            case BluetoothClass.Device.TOY_CONTROLLER:
                resource = R.drawable.toy_controller_f;
                break;
            case BluetoothClass.Device.TOY_DOLL_ACTION_FIGURE:
                resource = R.drawable.av_toy;
                break;
            case BluetoothClass.Device.TOY_GAME:
                resource = R.drawable.av_toy;
                break;
            case BluetoothClass.Device.TOY_ROBOT:
                resource = R.drawable.toy_robot;
                break;
            case BluetoothClass.Device.TOY_UNCATEGORIZED:
                resource = R.drawable.av_toy;
                break;
            case BluetoothClass.Device.TOY_VEHICLE:
                resource = R.drawable.toy_vehicle;
                break;
            case BluetoothClass.Device.WEARABLE_GLASSES:
                resource = R.drawable.wear_glasses_1;
                break;
            case BluetoothClass.Device.WEARABLE_HELMET:
                resource = R.drawable.wear_helmet;
                break;
            case BluetoothClass.Device.WEARABLE_JACKET:
                resource = R.drawable.wear_jacket;
                break;
            case BluetoothClass.Device.WEARABLE_PAGER:
                resource = R.drawable.wear_pager;
                break;
            case BluetoothClass.Device.WEARABLE_UNCATEGORIZED:
                resource = R.drawable.wear_jacket_2;
                break;
            case BluetoothClass.Device.WEARABLE_WRIST_WATCH:
                resource = R.drawable.wear_watch;
                break;
            default:
                resource = null;
        }

        return resource;
    }

}
