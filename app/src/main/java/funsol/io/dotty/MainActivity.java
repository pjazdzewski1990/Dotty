package funsol.io.dotty;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.location.Location;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;



public class MainActivity extends ActionBarActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = BuildConfig.DEBUG ? 1000 : 20000;
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    protected GoogleApiClient mGoogleApiClient;
    protected LocationRequest mLocationRequest;
    private GoogleMap map;

    DotsHelper database;

    private final String TAG = getClass().getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map))
                .getMap();
        database = new DotsHelper(this);

        setup();
    }

    private void setup() {
        createLocationRequest();
        createApiClient();
        map.clear();
        fillWithData();
    }

    private void fillWithData() {
        Cursor c = database.getAll();
        if (c.moveToFirst()) {
            do {
                addDotToMap(c.getDouble(0), c.getDouble(1));
            } while (c.moveToNext());
        }
    }

    private void createApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    }


    private boolean centerAtMe(Location location) {
        if(location != null) {
            LatLng locationLatLng = new LatLng(location.getLatitude(), location.getLongitude());
            map.moveCamera(CameraUpdateFactory.newLatLng(locationLatLng));
            return true;
        }
        return false;
    }

    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);

    }

    private void addDot(double lat, double lng) {
        addDotToMap(lat, lng);
        addDotToDB(lat, lng);
    }

    private void addDotToDB(double lat, double lng) {
        long newId = database.insertDot(lat, lng);
        Log.d(TAG, "Added to DB item #" + newId);
    }

    private void addDotToMap(double lat, double lng){
        Log.d(TAG, "Adding item to map " + lat + ", " + lng);
        map.addCircle(
            new CircleOptions().
                center(new LatLng(lat, lng)).
                fillColor(0x7022dec0).
                strokeColor(Color.BLUE).
                radius(17)
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }
    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    ////connection callbacks

    @Override
    public void onConnected(Bundle bundle) {
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        Log.d(TAG, "Connected " + mLastLocation);
        centerAtMe(mLastLocation);
        map.animateCamera(CameraUpdateFactory.zoomTo(14));
        startLocationUpdates();
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "Location changed to " + location);
        centerAtMe(location);
        addDot(location.getLatitude(), location.getLongitude());
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Location Suspended: " + i);
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Connection Failed: " + connectionResult);
    }

    class DotsHelper extends SQLiteOpenHelper {

        public static final String TABLE_NAME = "dots";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_LAT = "lat";
        public static final String COLUMN_LNG = "lng";

        private static final String DATABASE_NAME = "dotty.db";
        private static final int DATABASE_VERSION = 1;

        public DotsHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        // Database creation sql statement
        private final String DATABASE_CREATE = "CREATE TABLE " + TABLE_NAME
                + "("
                + COLUMN_ID + " INTEGER primary key autoincrement, "
                + COLUMN_LAT + " REAL not null, "
                + COLUMN_LNG + " REAL not null "
                + ");";

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(DotsHelper.class.getName(),
                    "Upgrading database from version " + oldVersion + " to "
                            + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE);
        }

        public Cursor getAll() {
            return getReadableDatabase().rawQuery("SELECT lat,lng FROM dots", null);
        }

        public long insertDot(double lat, double lng) {
            SQLiteDatabase db = this.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(COLUMN_LAT, lat);
            values.put(COLUMN_LNG, lng);

            // Inserting Row
            long insertedId = db.insert(TABLE_NAME, null, values);
            db.close(); // Closing database connection
            return insertedId;
        }
    }
}
