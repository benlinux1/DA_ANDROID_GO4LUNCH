package com.benlinux.go4lunch.ui.fragments;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.benlinux.go4lunch.R;
import com.benlinux.go4lunch.data.bookingManager.BookingManager;
import com.benlinux.go4lunch.data.userManager.UserManager;
import com.benlinux.go4lunch.databinding.FragmentWorkmatesBinding;

import com.benlinux.go4lunch.ui.adapters.WorkmateAdapter;
import com.benlinux.go4lunch.ui.models.Booking;
import com.benlinux.go4lunch.ui.models.User;
import com.google.android.gms.tasks.OnCompleteListener;

import com.google.android.gms.tasks.Task;
import com.google.android.material.divider.MaterialDividerItemDecoration;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class WorkmatesFragment extends Fragment {

    private FragmentWorkmatesBinding binding;
    private List<User> mWorkmates;
    private RecyclerView mRecyclerView;

    // FOR DATA
    private final UserManager userManager = UserManager.getInstance();
    private final BookingManager bookingManager = BookingManager.getInstance();

    private List<Booking> bookingsOfToday;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getBookingsOfToday().addOnCompleteListener(new OnCompleteListener<List<Booking>>() {
            @Override
            public void onComplete(@NonNull Task<List<Booking>> task) {
                setBookingsOfTodayInUserDatabase().addOnCompleteListener(new OnCompleteListener<List<User>>() {
                    @Override
                    public void onComplete(@NonNull Task<List<User>> task) {
                        getWorkmatesFromFireStore();
                    }
                });
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentWorkmatesBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        mRecyclerView = binding.listWorkmates;

        return view;
    }

    private Task<List<Booking>> getBookingsOfToday() {
        return bookingManager.getAllBookingsData().addOnCompleteListener(new OnCompleteListener<List<Booking>>() {
            @Override
            public void onComplete(@NonNull Task<List<Booking>> bookingTask) {
                bookingsOfToday = new ArrayList<>();
                final Calendar currentDate = Calendar.getInstance(Locale.FRANCE);
                // Define today formatted date
                @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
                String today = dateFormat.format(currentDate.getTime());
                for (Booking booking : bookingTask.getResult()) {
                    if (booking.getBookingDate().equals(today)) {
                        bookingsOfToday.add(booking);
                    }
                }
            }
        });
    }

    // Check bookings of today & update users restaurants of day for each of them
    private Task<List<User>> setBookingsOfTodayInUserDatabase() {
        return userManager.getAllUsersData().addOnCompleteListener(new OnCompleteListener<List<User>>() {
            @Override
            public void onComplete(@NonNull Task<List<User>> userTask) {
                List<User> users = userTask.getResult();
                for (User user : users) {
                    for (Booking bookingOfDay : bookingsOfToday) {
                        if (bookingOfDay.getUserId().equals(user.getId())) {
                            userManager.updateUserRestaurantOfTheDay(user.getId(), bookingOfDay.getRestaurantName());
                            userManager.updateUserRestaurantAddressOfTheDay(user.getId(), bookingOfDay.getFullAddress());
                            userManager.updateUserRestaurantIdOfTheDay(user.getId(), bookingOfDay.getRestaurantId());
                            break;
                        } else {
                            userManager.updateUserRestaurantOfTheDay(user.getId(), "");
                            userManager.updateUserRestaurantAddressOfTheDay(user.getId(), "");
                            userManager.updateUserRestaurantIdOfTheDay(user.getId(), "");
                        }
                    }
                    if (bookingsOfToday.isEmpty()) {
                        userManager.updateUserRestaurantOfTheDay(user.getId(), "");
                        userManager.updateUserRestaurantAddressOfTheDay(user.getId(), "");
                        userManager.updateUserRestaurantIdOfTheDay(user.getId(), "");
                    }
                }
            }
        });
    }


    // Set Workmates in Recycler View
    private void getWorkmatesFromFireStore() {
        // Get workmates list from firestore
        userManager.getAllUsersData().addOnCompleteListener(new OnCompleteListener<List<User>>() {
            @Override
            public void onComplete(@NonNull Task<List<User>> task) {
                if (task.isSuccessful()) {
                    // if result OK, set workmates list & configure recycler view
                    List<User> workmates = new ArrayList<>();
                    String currentUserId = userManager.getCurrentUser().getUid();
                    for (User user : task.getResult()) {
                        // Add workmates except current user to list
                        if (!Objects.equals(user.getId(), currentUserId)) {
                            workmates.add(user);
                        }
                    }
                    setWorkmatesList(workmates);
                    configRecyclerView();
                }
            }
        });
    }

    private void setWorkmatesList(List<User> workmates) {
        this.mWorkmates = workmates;
    }

    private List<User> getWorkmatesList() {
        return this.mWorkmates;
    }


    /**
     * Init the recyclerView that contains workmates
     */
    private void configRecyclerView() {
        WorkmateAdapter adapter = new WorkmateAdapter(getWorkmatesList(), getContext());
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        MaterialDividerItemDecoration divider = new MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL);
        divider.setDividerInsetStart(228);
        // Hide logo in landScape
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            divider.setDividerInsetEnd(180);
        }
        mRecyclerView.addItemDecoration(divider);

        mRecyclerView.setAdapter(adapter);

        // If workmates list is empty, show notification text instead of recyclerview
        adapter.notifyItemRangeInserted(-1, mWorkmates.size());
        if (mWorkmates.size() == 0) {
           binding.textWorkmates.setText(R.string.no_available_workmates);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}