package net.wigle.wigleandroid;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.location.Address;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

import net.wigle.wigleandroid.background.ApiDownloader;
import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.DownloadHandler;
import net.wigle.wigleandroid.background.QueryThread;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.QueryArgs;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DBResultActivity extends AppCompatActivity {
    private static final int MENU_RETURN = 12;
    private static final int MSG_QUERY_DONE = 2;
    private static final int LIMIT = 50;

    private static final int DEFAULT_ZOOM = 18;

    private static final int MSG_SEARCH_DONE = 100;

    private static final String RESULT_LIST_KEY = "results";

    private static final String TRILAT_KEY = "trilat";
    private static final String TRILON_KEY = "trilong";
    private static final String SSID_KEY = "ssid";
    private static final String NETID_KEY = "netid";
    private static final String ENCRYPTION_KEY = "encryption";
    private static final String CHANNEL_KEY = "channel";


    private static final String[] ALL_NET_KEYS = new String[] {
            TRILAT_KEY, TRILON_KEY, SSID_KEY, NETID_KEY, ENCRYPTION_KEY, CHANNEL_KEY
    };


    private SetNetworkListAdapter listAdapter;
    private MapView mapView;
    private MapRender mapRender;
    private final List<Network> resultList = new ArrayList<>();
    private final ConcurrentLinkedHashMap<LatLng, Integer> obsMap = new ConcurrentLinkedHashMap<>();

    @Override
    public void onCreate( final Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // set language
        MainActivity.setLocale( this );
        setContentView( R.layout.dbresult );

        // force media volume controls
        setVolumeControlStream( AudioManager.STREAM_MUSIC );

        setupList();

        QueryArgs queryArgs = ListFragment.lameStatic.queryArgs;
        MainActivity.info("queryArgs: " + queryArgs);
        final TextView tv = (TextView) findViewById( R.id.dbstatus );

        if ( queryArgs != null ) {
            tv.setText( getString(R.string.status_working) + "..."); //TODO: throbber/overlay?
            Address address = queryArgs.getAddress();
            LatLng center = MappingFragment.DEFAULT_POINT;
            if ( address != null ) {
                center = new LatLng(address.getLatitude(), address.getLongitude());
            }
            setupMap( center, savedInstanceState );
            if (queryArgs.searchWiGLE()) {
                setupWiGLEQuery(queryArgs, this, findViewById(android.R.id.content));
            } else {
                setupQuery(queryArgs);
            }
        }
        else {
            tv.setText(getString(R.string.status_fail) + "...");
        }
    }

    private void setupList() {
        // not set by nonconfig retain
        listAdapter = new SetNetworkListAdapter( getApplicationContext(), R.layout.row );
        final ListView listView = (ListView) findViewById( R.id.dblist );
        ListFragment.setupListAdapter( listView, MainActivity.getMainActivity(), listAdapter, true );
    }

    private void setupMap( final LatLng center, final Bundle savedInstanceState ) {
        mapView = new MapView( this );
        mapView.onCreate(savedInstanceState);
        MapsInitializer.initialize(this);

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(final GoogleMap googleMap) {
                mapRender = new MapRender(DBResultActivity.this, googleMap, true);

                if (center != null) {
                    final CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(center).zoom(DEFAULT_ZOOM).build();
                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                }
            }
        });

        final RelativeLayout rlView = (RelativeLayout) findViewById( R.id.db_map_rl );
        rlView.addView( mapView );
    }

    @SuppressLint("HandlerLeak")
    private void setupQuery( final QueryArgs queryArgs ) {
        final Address address = queryArgs.getAddress();

        // what runs on the gui thread
        final Handler handler = new Handler() {
            @Override
            public void handleMessage( final Message msg ) {
                final TextView tv = (TextView) findViewById( R.id.dbstatus );

                if ( msg.what == MSG_QUERY_DONE ) {

                    tv.setText( getString(R.string.status_success)  );

                    listAdapter.clear();
                    boolean first = true;
                    for ( final Network network : resultList ) {
                        listAdapter.add(network);
                        if ( address == null && first ) {
                            final LatLng center = MappingFragment.getCenter( DBResultActivity.this, network.getLatLng(), null );
                            MainActivity.info( "set center: " + center + " network: " + network.getSsid()
                                    + " point: " + network.getLatLng());
                            mapView.getMapAsync(new OnMapReadyCallback() {
                                @Override
                                public void onMapReady(final GoogleMap googleMap) {
                                    final CameraPosition cameraPosition = new CameraPosition.Builder()
                                            .target(center).zoom(DEFAULT_ZOOM).build();
                                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                                }
                            });

                            first = false;
                        }

                        if (network.getLatLng() != null && mapRender != null) {
                            mapRender.addItem(network);
                        }
                    }
                    resultList.clear();
                }
            }
        };

        String sql = "SELECT bssid,lastlat,lastlon FROM " + DatabaseHelper.NETWORK_TABLE + " WHERE 1=1 ";
        final String ssid = queryArgs.getSSID();
        final String bssid = queryArgs.getBSSID();
        boolean limit = false;
        if ( ssid != null && ! "".equals(ssid) ) {
            sql += " AND ssid like " + DatabaseUtils.sqlEscapeString(ssid);
            limit = true;
        }
        if ( bssid != null && ! "".equals(bssid) ) {
            sql += " AND bssid like " + DatabaseUtils.sqlEscapeString(bssid);
            limit = true;
        }
        if ( address != null ) {
            final double diff = 0.1d;
            final double lat = address.getLatitude();
            final double lon = address.getLongitude();
            sql += " AND lastlat > '" + (lat - diff) + "' AND lastlat < '" + (lat + diff) + "'";
            sql += " AND lastlon > '" + (lon - diff) + "' AND lastlon < '" + (lon + diff) + "'";
        }
        if ( limit ) {
            sql += " LIMIT " + LIMIT;
        }

        final TreeMap<Float,String> top = new TreeMap<>();
        final float[] results = new float[1];
        final long[] count = new long[1];

        final QueryThread.Request request = new QueryThread.Request( sql, new QueryThread.ResultHandler() {
            @Override
            public boolean handleRow( final Cursor cursor ) {
                final String bssid = cursor.getString(0);
                final float lat = cursor.getFloat(1);
                final float lon = cursor.getFloat(2);
                count[0]++;

                if ( address == null ) {
                    top.put( (float) count[0], bssid );
                }
                else {
                    Location.distanceBetween( lat, lon, address.getLatitude(), address.getLongitude(), results );
                    final float meters = results[0];

                    if ( top.size() <= LIMIT ) {
                        putWithBackoff( top, bssid, meters );
                    }
                    else {
                        Float last = top.lastKey();
                        if ( meters < last ) {
                            top.remove( last );
                            putWithBackoff( top, bssid, meters );
                        }
                    }
                }

                return true;
            }

            @Override
            public void complete() {
                for ( final String bssid : top.values() ) {
                    final Network network = ListFragment.lameStatic.dbHelper.getNetwork( bssid );
                    resultList.add( network );
                    final LatLng point = network.getLatLng();
                    if (point != null) {
                        obsMap.put(point, 0);
                    }
                }

                handler.sendEmptyMessage( MSG_QUERY_DONE );
                if ( mapView != null ) {
                    // force a redraw
                    mapView.postInvalidate();
                }
            }
        });

        // queue it up
        ListFragment.lameStatic.dbHelper.addToQueue( request );
    }

    private void setupWiGLEQuery(final QueryArgs queryArgs, final AppCompatActivity activity, final View view) {

        String queryParams = "";
        if (queryArgs.getSSID() != null && !queryArgs.getSSID().isEmpty()) {
            if (!queryParams.isEmpty()) {
                queryParams+="&";
            }

            if (queryArgs.getSSID().contains("%") || queryArgs.getSSID().contains("_")) {
                queryParams+="ssidlike="+URLEncoder.encode((queryArgs.getSSID()));
            } else {
                queryParams+="ssidlike="+URLEncoder.encode((queryArgs.getSSID()));
            }
        }

        if (queryArgs.getBSSID() != null && !queryArgs.getBSSID().isEmpty()) {
            if (!queryParams.isEmpty()) {
                queryParams+="&";
            }

            if (queryArgs.getBSSID().contains("%") || queryArgs.getBSSID().contains("%")) {
                String splitBssid[] = queryArgs.getBSSID().split("%|_", 2);
                queryParams+="netid="+URLEncoder.encode(splitBssid[0]);
            } else {
                queryParams+="netid="+URLEncoder.encode((queryArgs.getBSSID()));
            }
        }

        //TODO: address

        final NetsDownloadHandler handler = new NetsDownloadHandler(view,
                activity.getPackageName(), getResources(), listAdapter, mapView, mapRender, obsMap,
                (TextView) findViewById(R.id.dbstatus), getString(R.string.status_success), "");

        final ApiDownloader task = new ApiDownloader(activity, ListFragment.lameStatic.dbHelper,
                "search-cache-"+queryParams+".json", MainActivity.SEARCH_WIFI_URL+"?"+queryParams,
                false, true, true,
                ApiDownloader.REQUEST_GET,
                new ApiListener() {
                    @Override
                    public void requestComplete(final JSONObject json, final boolean isCache) {
                        handleNets(json, handler);
                    }
                });
        try {
            task.startDownload(getSupportFragmentManager().findFragmentById(R.id.net_list));
        } catch (WiGLEAuthException waex) {
            //unauthenticated call - should never trip
            MainActivity.warn("Authentication error on news load (should not happen)", waex);
        }

    }

    private void handleNets(final JSONObject json, final Handler handler) {
        MainActivity.info("handleNets");

        if (json == null) {
            MainActivity.info("handleNets null json, returning");
            return;
        }

        final Bundle bundle = new Bundle();
        try {
            final JSONArray list = json.getJSONArray(RESULT_LIST_KEY);
            final ArrayList<Parcelable> resultList = new ArrayList<>(list.length());
            for (int i = 0; i < list.length(); i++) {
                final JSONObject row = list.getJSONObject(i);
                final Bundle rowBundle = new Bundle();
                for  (final String key: ALL_NET_KEYS) {
                    String value = row.getString(key);
                    rowBundle.putString(key, value);
                }
                resultList.add(rowBundle);
            }
            bundle.putParcelableArrayList(RESULT_LIST_KEY, resultList);
        } catch (final JSONException ex) {
            MainActivity.error("json error: " + ex, ex);
        } catch (final Exception e) {
            MainActivity.error("search error: " + e, e);
        }

        final Message message = new Message();
        message.setData(bundle);
        message.what = MSG_SEARCH_DONE;
        handler.sendMessage(message);

    }

    private static void putWithBackoff( TreeMap<Float,String> top, String s, float diff ) {
        String old = top.put( diff, s );
        // protect against infinite loops
        int count = 0;
        while ( old != null && count < 1000 ) {
            // ut oh, two at the same difference away. add a slight bit and put it back
            // info( "collision at diff: " + diff + " old: " + old.getCallsign() + " orig: " + s.getCallsign() );
            diff += 0.0001f;
            old = top.put( diff, old );
            count++;
        }
    }

    /* Creates the menu items */
    @Override
    public boolean onCreateOptionsMenu( final Menu menu ) {
        MenuItem item = menu.add(0, MENU_RETURN, 0, getString(R.string.menu_return));
        item.setIcon( android.R.drawable.ic_media_previous );

        return true;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        switch ( item.getItemId() ) {
            case MENU_RETURN:
                finish();
                return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (null != mapView) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        if (mapRender != null) {
            // save memory
            mapRender.clear();
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    private final static class NetsDownloadHandler extends DownloadHandler {
        private final SetNetworkListAdapter resultList;
        private final MapView mapView;
        private final MapRender mapRender;
        private final ConcurrentLinkedHashMap<LatLng, Integer> obsMap;
        private final TextView statusView;
        private final String success;
        private final String failure;

        private NetsDownloadHandler(final View view, final String packageName,
                                    final Resources resources, final SetNetworkListAdapter resultList,
                                    final MapView mapView, final MapRender mapRender,
                                    final ConcurrentLinkedHashMap<LatLng, Integer> obsMap,
                                    final TextView statusView, final String success, final String failure) {
            super(view, null, packageName, resources);
            this.resultList = resultList;
            this.mapView = mapView;
            this.mapRender = mapRender;
            this.obsMap = obsMap;
            this.statusView = statusView;
            this.success = success;
            this.failure = failure;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(final Message msg) {
            final Bundle bundle = msg.getData();

            final ArrayList<Parcelable> results = bundle.getParcelableArrayList(RESULT_LIST_KEY);
            // MainActivity.info("handleMessage. results: " + results);
            if (msg.what == MSG_SEARCH_DONE && results != null /*&& handler != null*/) {
                resultList.clear();
                boolean first = true;
                for (final Parcelable result : results) {
                    if (result instanceof Bundle) {
                        final Bundle row = (Bundle) result;
                        final Network network = new Network(row.getString(NETID_KEY), row.getString(SSID_KEY),
                                Integer.parseInt(row.getString(CHANNEL_KEY)), "[SEARCH]",
                        -113, NetworkType.WIFI);
                        network.setLatLng(new LatLng(Double.parseDouble(row.getString(TRILAT_KEY)),
                                Double.parseDouble(row.getString(TRILON_KEY))));
                        if ( first ) {
                            mapView.getMapAsync(new OnMapReadyCallback() {
                                @Override
                                public void onMapReady(final GoogleMap googleMap) {
                                    final CameraPosition cameraPosition = new CameraPosition.Builder()
                                            .target(network.getLatLng()).zoom(DEFAULT_ZOOM).build();
                                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                                }
                            });

                            first = false;
                        }
                        resultList.add( network );

                        if (network.getLatLng() != null && mapRender != null) {
                            mapRender.addItem(network);
                        }

                        final LatLng point = network.getLatLng();
                        if (point != null) {
                            obsMap.put(point, 0);
                        }
                    }
                }

                if (statusView != null) {
                    statusView.setText(success);
                }

                if (mapView != null) {
                    mapView.postInvalidate();
                }

            }
        }
    }


}
