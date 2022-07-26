package com.benlinux.go4lunch.modules;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import com.benlinux.go4lunch.R;
import com.benlinux.go4lunch.activities.MainActivity;
import com.benlinux.go4lunch.data.bookingManager.BookingManager;
import com.benlinux.go4lunch.ui.adapters.ListAdapter;
import com.benlinux.go4lunch.ui.models.Booking;
import com.benlinux.go4lunch.ui.models.Restaurant;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.maps.android.SphericalUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FetchPlacesData extends AsyncTask<Object, String, String> {

    private String googleNearByPlacesData;
    private GoogleMap googleMap;
    @SuppressLint("StaticFieldLeak")
    private final Context localContext;
    private final String dataType;

    private List<Restaurant> restaurantsList;
    private ListAdapter adapter;
    private List<Booking> bookingsOfToday;
    private int restaurantMarker;


    public FetchPlacesData(Context context, String data) {
        super();
        localContext = context;
        dataType = data;
    }

    // After getting nearby places response (String s), set Data into fragments
    @Override
    protected void onPostExecute(String s) {

        try {
            // Get results from NearBy API
            JSONObject jsonObject = new JSONObject(s);
            JSONArray jsonArray = jsonObject.getJSONArray("results");

            // If request come from map fragment, set elements on google map
            if (dataType.equals("map") ) {

                // Loop to get restaurants details from each result of the Place request
                for (int i = 0; i < jsonArray.length(); i++) {

                    // Get restaurant's data
                    JSONObject restaurantInfo = jsonArray.getJSONObject(i);

                    // Get restaurant's latitude & longitude
                    JSONObject getLocation = restaurantInfo.getJSONObject("geometry")
                            .getJSONObject("location");
                    String lat = getLocation.getString("lat");
                    String lng = getLocation.getString("lng");

                    // Get restaurant's name
                    String name = restaurantInfo.getString("name");

                    // Define restaurant LatLng for marker
                    LatLng latLng = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));

                    // Get place id (used to retrieve place info in details activity)
                    String placeId = restaurantInfo.getString("place_id");

                    // Set marker color according to booked restaurants of day
                    getBookingsOfToday().addOnCompleteListener(new OnCompleteListener<List<Booking>>() {
                        @Override
                        public void onComplete(@NonNull Task<List<Booking>> task) {
                            for (Booking booking : bookingsOfToday) {
                                if (booking.getRestaurantId().equals(placeId)) {
                                    restaurantMarker = R.drawable.ic_marker_green;
                                    break;
                                } else {
                                    restaurantMarker = R.drawable.ic_marker_48;
                                }
                            }
                            if (bookingsOfToday.isEmpty()) {
                                restaurantMarker = R.drawable.ic_marker_48;
                            }

                            // Then, define marker options (restaurant's name, address, position, icon)
                            MarkerOptions markerOptions = new MarkerOptions()
                                    .title(name)
                                    .position(latLng)
                                    .icon(BitmapDescriptorFactory.fromResource(restaurantMarker));

                            // Set rating into marker snippet
                            try {
                                String rating = restaurantInfo.getString("rating");
                                markerOptions.snippet(rating);
                            } catch (Exception e) {
                                Log.e("No rating", e.getMessage());
                            }

                            // Set place id in tag (used to retrieve place info in details activity)
                            Objects.requireNonNull(googleMap.addMarker(markerOptions)).setTag(placeId);
                            // googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
                        }
                    });
                }

                // else, request come from list fragment, so set elements into recyclerview adapter
            } else {

                for (int i = 0; i < jsonArray.length(); i++) {
                    try {
                        JSONObject restaurantInfo = jsonArray.getJSONObject(i);

                        // Get place id
                        String placeId = restaurantInfo.getString("place_id");
                        // Get restaurant's name
                        String name = restaurantInfo.getString("name");

                        // Get restaurant's full Location
                        JSONObject getLocation = restaurantInfo.getJSONObject("geometry")
                                .getJSONObject("location");

                        // Get restaurant's latitude & longitude
                        String lat = getLocation.getString("lat");
                        String lng = getLocation.getString("lng");

                        // Retrieve user location (used to calculate distance from restaurant)
                        LatLng userLocation = MainActivity.userLocation;

                        // Define restaurant's LatLng
                        LatLng restaurantLocation = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));

                        // Formatted Address
                        String formattedAddress = getFormattedAddressFromLatLng(restaurantLocation);

                        // Formatted distance
                        String distance = calculateAndFormatDistance(userLocation, restaurantLocation);

                        final List<Booking> numberOfBookings = new ArrayList<>();

                        // Set number of bookings according to bookings of day
                        getBookingsOfToday().addOnCompleteListener(new OnCompleteListener<List<Booking>>() {
                            @Override
                            public void onComplete(@NonNull Task<List<Booking>> task) {
                                for (Booking booking : task.getResult()) {
                                    if (booking.getRestaurantId().equals(placeId)) {
                                        numberOfBookings.add(booking);
                                    }
                                }
                            }
                        });

                        // If place get rating, add it with rating to the restaurant list
                        try {
                            String ratingStr = restaurantInfo.getString("rating");
                            Double rating = Double.parseDouble(ratingStr);
                            Restaurant restaurant = new Restaurant(placeId, name, formattedAddress, rating, null, distance, restaurantLocation, numberOfBookings);
                            // Add restaurant to list
                            restaurantsList.add(restaurant);
                            // If place doesn't get rating, set rating to null value & add restaurant to list
                        } catch (Exception e) {
                            Log.e("No rating", e.getMessage());
                            Restaurant restaurant = new Restaurant(placeId, name, formattedAddress, null, null, distance, restaurantLocation, numberOfBookings);
                            // Add restaurant in the list
                            restaurantsList.add(restaurant);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    // Set restaurants list into adapter
                    adapter.initList(restaurantsList);
                }
            }
        } catch (JSONException e ) {
            e.printStackTrace();
        }
    }

    // Get Nearby places (returned to string)
    @Override
    protected String doInBackground (Object... objects) {
        // if request come from mapFragment
        String url;
        if (dataType.equals("map") ) {
            try {
                googleMap = (GoogleMap) objects[0];
                url = (String) objects[1];
                PlaceDownloadUrl downloadUrl = new PlaceDownloadUrl();
                googleNearByPlacesData = downloadUrl.downloadUrl(url);

            } catch (IOException e) {
                e.printStackTrace();
            }
            // else, if request come from listFragment
        } else {
            try {
                restaurantsList = (List<Restaurant>) objects[0];
                url = (String) objects[1];
                adapter = (ListAdapter) objects[2];
                PlaceDownloadUrl downloadUrl = new PlaceDownloadUrl();
                googleNearByPlacesData = downloadUrl.downloadUrl(url);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return googleNearByPlacesData;
    }

    // Format LatLng data to readable address
    private String getFormattedAddressFromLatLng(LatLng latLng) {
        Geocoder geocoder;
        List<Address> addresses;
        geocoder = new Geocoder(localContext, Locale.getDefault());
        String formattedAddress;
        try {
            addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            Address returnedAddress = addresses.get(0);
            StringBuilder strReturnedAddress = new StringBuilder();

            for (int i = 0; i < returnedAddress.getMaxAddressLineIndex(); i++) {
                strReturnedAddress.append(returnedAddress.getAddressLine(i)).append("\n");
            }
            String mStreetNumber = returnedAddress.getSubThoroughfare();
            String mStreet = returnedAddress.getThoroughfare();
            strReturnedAddress.append(mStreetNumber).append(" ").append(mStreet);
            formattedAddress = strReturnedAddress.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        return formattedAddress;
    }

    // Calculate distance between two points, and format to string in meters
    @SuppressLint("DefaultLocale")
    private String calculateAndFormatDistance(LatLng startPoint, LatLng endPoint) {
        double distance = SphericalUtil.computeDistanceBetween(startPoint, endPoint);
        return String.format("%d m", Math.round(distance));
    }

    // Get bookings of day & automatically delete old bookings
    private Task<List<Booking>> getBookingsOfToday() {
        final BookingManager bookingManager = BookingManager.getInstance();
        return bookingManager.getAllBookingsData().addOnCompleteListener(new OnCompleteListener<List<Booking>>() {
            @Override
            public void onComplete(@NonNull Task<List<Booking>> bookingTask) {
                bookingsOfToday = new ArrayList<>();
                long currentDate = System.currentTimeMillis();
                // Define today formatted date
                @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
                String today = dateFormat.format(currentDate);
                for (Booking booking : bookingTask.getResult()) {
                    if (booking.getBookingDate().equals(today)) {
                        bookingsOfToday.add(booking);
                    }
                    // Delete old bookings
                    try {
                        Date dateOfToday = dateFormat.parse(today);
                        Date dateOfBooking = dateFormat.parse(booking.getBookingDate());
                        assert dateOfBooking != null;
                        if (dateOfBooking.before(dateOfToday)) {
                            bookingManager.deleteBookingById(booking.getBookingId()).addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void unused) {
                                    Log.d("DELETE OLD BOOKING", "old bookings deleted with success" );
                                }
                            });
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}