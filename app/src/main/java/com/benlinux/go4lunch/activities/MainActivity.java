package com.benlinux.go4lunch.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.benlinux.go4lunch.R;
import com.benlinux.go4lunch.modules.NotificationService;
import com.benlinux.go4lunch.ui.adapters.PlaceAutoCompleteAdapter;
import com.benlinux.go4lunch.data.userManager.UserManager;

import com.benlinux.go4lunch.ui.models.User;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;

import java.util.Calendar;
import java.util.Objects;


public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView drawerNavView;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private TextView userName;
    private TextView userEmail;
    private ImageView userAvatar;

    // FOR DATA
    private final UserManager userManager = UserManager.getInstance();
    public static LatLng userLocation;

    // For autocomplete search feature
    private SearchView.SearchAutoComplete autoCompleteTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        redirectUserIfNotLogged();

        configureToolBar();
        configureNavigation();
        configureDrawerLayout();
        setDrawerViews();
        updateUIWithUserData();

        // Set notifications if build >=23 (Marshmallow October 2015)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setNotificationService();
        }
    }

    private LatLng getUserLocation() {
        return userLocation;
    }

    public static void setUserLocation(LatLng location) {
        userLocation = location;
    }


    /**
     * Catch intent type. Used for voice search.
     * @param intent is caught intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }


    /**
     * Handle voice search intent & set query in autoComplete textview
     * @param intent is handled intent
     */
    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            autoCompleteTextView.setText(query);
        }
    }

    // Customize Search Bar features
    @RequiresApi(api = Build.VERSION_CODES.P)
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_menu, menu);
        MenuItem menuItem = menu.findItem(R.id.search_action);

        // Define search bar
        SearchView searchView = (SearchView) menuItem.getActionView();

        // Define autocomplete text view
        autoCompleteTextView = searchView.findViewById(androidx.appcompat.R.id.search_src_text);

        // Set adapter for Suggestions
        PlaceAutoCompleteAdapter adapter = new PlaceAutoCompleteAdapter(MainActivity.this, android.R.layout.simple_list_item_1);
        autoCompleteTextView.setAdapter(adapter);

        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        // Assumes current activity is the searchable activity
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

        // When user clicks on restaurant in list, go to details page
        autoCompleteTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String placeId = adapter.getRestaurantId(position);
                Intent restaurantDetailsActivityIntent = new Intent(MainActivity.this, RestaurantDetailsActivity.class);
                restaurantDetailsActivityIntent.putExtra("PLACE_ID", placeId);
                restaurantDetailsActivityIntent.putExtra("USER_LOCATION", getUserLocation());
                startActivity(restaurantDetailsActivityIntent);
            }
        });

        return true;
    }

    @Override
    public void onBackPressed() {
        // Handle back click to close menu
        if (this.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            this.drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    public boolean onSupportNavigateUp() {
        // Replace navigation up button with nav drawer button when on start destination
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }

    // Handle Navigation Item Click
    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id){
            case R.id.activity_main_drawer_lunch:
                Intent lunchActivityIntent = new Intent(this, UserLunchActivity.class);
                startActivity(lunchActivityIntent);
                break;
            case R.id.activity_main_drawer_settings:
                Intent settingsActivityIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsActivityIntent);
                break;
            case R.id.activity_main_drawer_logout:
                    logout();
                break;
            default:
                break;
        }
        this.drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    // Set custom Toolbar
    private void configureToolBar(){
        //FOR DESIGN
        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setElevation(R.dimen.default_elevation_size);
    }

    // Configure Drawer & Bottom Navigation
    private void configureNavigation() {

        // Set navigation views
        BottomNavigationView bottomNavView = findViewById(R.id.bottom_nav_view);
        drawerNavView = findViewById(R.id.activity_main_nav_view);
        drawerLayout = findViewById(R.id.activity_main_drawer_layout);

        // Set navigation controller
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);

        // Build and configure App bar
        appBarConfiguration =
            new AppBarConfiguration.Builder(navController.getGraph()) //Pass the ids of fragments from nav_graph which you dont want to show back button in toolbar
                .setOpenableLayout(drawerLayout)
                .build();

        //Setup toolbar with back button and drawer icon according to appBarConfiguration
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // Setup Navigation for drawer & bottom bar
        NavigationUI.setupWithNavController(bottomNavView, navController);
        NavigationUI.setupWithNavController(drawerNavView, navController);

        // Listener for selected item
        drawerNavView.setNavigationItemSelectedListener(this);
    }

    // Configure Drawer Layout with toggle
    private void configureDrawerLayout(){
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
    }

    // Drawer layout data views
    private void setDrawerViews() {
        drawerNavView = findViewById(R.id.activity_main_nav_view);
        View headerContainer = drawerNavView.getHeaderView(0);
        userName = headerContainer.findViewById(R.id.user_name);
        userEmail = headerContainer.findViewById(R.id.user_email);
        userAvatar = headerContainer.findViewById(R.id.user_avatar);

        // Hide drawer logo under 1081px screen height
        if (getScreenHeight() < 1081) {
            findViewById(R.id.activity_main_nav_view_logo).setVisibility(View.GONE);
        }
    }

    private int getScreenHeight() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.heightPixels;
    }

    // Set user data in drawer views
    private void updateUIWithUserData(){
        if(userManager.isCurrentUserLogged()) {
            // Let short time to register user for the first time in Firestore
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Task<User> getData = userManager.getUserData().addOnCompleteListener(new OnCompleteListener<User>() {
                 @Override
                 public void onComplete(@NonNull Task<User> task) {
                     User user = task.getResult();
                     // Set user name
                     String username = TextUtils.isEmpty(user.getName()) ? getString(R.string.info_no_username_found) : user.getName();
                     userName.setText(username);
                     // Set user email
                     String email = TextUtils.isEmpty(user.getEmail()) ? getString(R.string.info_no_email_found) : user.getEmail();
                     userEmail.setText(email);
                     // Set avatar
                     setUserAvatar(user.getAvatar());
                 }
             });

            getData.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d("EXCEPTION", e.toString());
                }
            });
        }
    }


    /**
     * Set user avatar into imageView
     * @param profilePictureUrl is url of the avatar
     */
    private void setUserAvatar(@Nullable String profilePictureUrl){
        if (profilePictureUrl != null) {
            Glide.with(this)
                .load(profilePictureUrl)
                .apply(RequestOptions.circleCropTransform())
                .into(userAvatar);
        } else {
            Glide.with(this)
                .load(R.mipmap.no_photo)
                .apply(RequestOptions.circleCropTransform())
                .into(userAvatar);
        }
    }

    // Logout from firebase
    private void logout() {
        // On success, close activity & go to login
        userManager.signOut(this).addOnSuccessListener(aVoid -> {
            redirectUserIfNotLogged();
            Toast.makeText(this, getString(R.string.disconnection_succeed), Toast.LENGTH_SHORT).show();
        })
        // On failure, show error toast
        .addOnFailureListener(aVoid -> Toast.makeText(this, getString(R.string.disconnection_failed), Toast.LENGTH_SHORT).show());
    }

    // Close current activities & go to login if user is not logged
    private void redirectUserIfNotLogged() {
        if (!userManager.isCurrentUserLogged()) {
            finishAndRemoveTask();
            Intent loginActivityIntent = new Intent(this, LoginActivity.class);
            ActivityCompat.startActivity(this, loginActivityIntent, null);
        }
    }

    // Set user notification, each day, at 12:00
    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint("ShortAlarm")
    private void setNotificationService() {
        // Define alarm manager
        AlarmManager alarmManager = (AlarmManager)getApplicationContext().getSystemService(Context.ALARM_SERVICE);

        // Define receiver intent for pending intent
        Intent intent = new Intent(this, NotificationService.class);

        // Define alarm intent
        PendingIntent alarmIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Define exact hour of day for notifications (12:00)
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());
        Calendar alarmMoment = Calendar.getInstance();
        alarmMoment.setTimeInMillis(System.currentTimeMillis());
        alarmMoment.set(Calendar.HOUR_OF_DAY, 12);
        alarmMoment.set(Calendar.MINUTE, 0);

        // if alarm moment is past today, set alarm for tomorrow
        if (alarmMoment.before(now)) {
           alarmMoment.add(Calendar.DAY_OF_YEAR, 1);
        }

        Log.d("ACTUAL HOUR", now.getTime().toString());
        Log.d("NEXT ALARM", alarmMoment.getTime().toString());

        // Check user notifications parameters in application account
        userManager.getUserData().addOnCompleteListener(new OnCompleteListener<User>() {
            @Override
            public void onComplete(@NonNull Task<User> task) {
                User user = task.getResult();
                // If user accepts notifications, set notification time
                if (Boolean.TRUE.equals(user.isNotified())) {
                    // Set calendar exact time & repeat alarm each day
                    alarmManager.setRepeating(AlarmManager.RTC, alarmMoment.getTimeInMillis(),
                            AlarmManager.INTERVAL_DAY, alarmIntent);
                }
            }
        });
    }


    /**
     * Used to navigate to Main activity
     * @param activity is original activity
     */
    public static void navigate(Activity activity) {
        Intent intent = new Intent(activity, MainActivity.class);
        ActivityCompat.startActivity(activity, intent, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        redirectUserIfNotLogged();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }


}