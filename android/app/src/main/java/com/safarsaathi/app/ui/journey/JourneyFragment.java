package com.safarsaathi.app.ui.journey;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.journey.ChosenJourney;
import com.safarsaathi.app.data.journey.JourneyCatalog;
import com.safarsaathi.app.databinding.FragmentJourneyBinding;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * "Plan a journey": the traveller picks From → To and a day, sees SAMPLE flights (JourneyCatalog, hard-coded for the
 * demo) and taps one to make it the trip on Home (ChosenJourney). "Already booked?" (Figma E02) takes a booking
 * reference + last name; in the demo it finds the demo trip IJ 512. OnBoard never books or pays.
 */
public class JourneyFragment extends Fragment {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private FragmentJourneyBinding binding;
    private JourneyCatalog.City from = JourneyCatalog.city("BOM");
    private JourneyCatalog.City to = JourneyCatalog.city("GOX");

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public JourneyFragment() {
        super(R.layout.fragment_journey);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentJourneyBinding.bind(view);
        ArrayAdapter<JourneyCatalog.City> cities = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, JourneyCatalog.CITIES);
        binding.journeyFrom.setAdapter(cities);
        binding.journeyTo.setAdapter(cities);
        binding.journeyFrom.setText(from.toString(), false);
        binding.journeyTo.setText(to.toString(), false);
        binding.journeyFrom.setOnItemClickListener((parent, item, position, id) -> from = cities.getItem(position));
        binding.journeyTo.setOnItemClickListener((parent, item, position, id) -> to = cities.getItem(position));
        binding.journeySearch.setOnClickListener(button -> showFlights());
        binding.journeyFindTrip.setOnClickListener(button -> findBooking());
        showFlights();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // R.id values are not compile-time constants with AGP 9: if/else, not switch.
    private LocalDate chosenDay() {
        LocalDate today = LocalDate.now(AppConfig.TRIP_ZONE);
        int checked = binding.journeyDay.getCheckedChipId();
        if (checked == R.id.journey_tomorrow) {
            return today.plusDays(1);
        }
        if (checked == R.id.journey_day_after) {
            return today.plusDays(2);
        }
        return today;
    }

    private void showFlights() {
        binding.journeyResults.removeAllViews();
        if (from.code.equals(to.code)) {
            binding.journeyResultsTitle.setVisibility(View.VISIBLE);
            binding.journeyResultsTitle.setText(R.string.journey_same_city);
            return;
        }
        LocalDate day = chosenDay();
        binding.journeyResultsTitle.setVisibility(View.VISIBLE);
        binding.journeyResultsTitle.setText(getString(R.string.journey_results, from.name, to.name, DAY.format(day)));
        List<JourneyCatalog.Flight> flights = JourneyCatalog.flights(from, to, day);
        float dp = getResources().getDisplayMetrics().density;
        for (JourneyCatalog.Flight flight : flights) {
            MaterialCardView card = new MaterialCardView(requireContext(), null,
                    com.google.android.material.R.attr.materialCardViewOutlinedStyle);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(8 * dp);
            card.setLayoutParams(params);
            android.widget.LinearLayout column = new android.widget.LinearLayout(requireContext());
            column.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = Math.round(16 * dp);
            column.setPadding(pad, pad, pad, pad);
            TextView line = new TextView(requireContext());
            line.setTextAppearance(R.style.TextAppearance_OnBoard_TitleMedium);
            long minutes = flight.minutes();
            line.setText(getString(R.string.journey_flight_row, flight.flightNo, TIME.format(flight.departs),
                    TIME.format(flight.arrives), (minutes / 60) + " h " + (minutes % 60) + " min"));
            TextView hint = new TextView(requireContext());
            hint.setTextAppearance(R.style.TextAppearance_OnBoard_BodySmall);
            hint.setText(getString(R.string.journey_tap_to_choose) + " · " + flight.from.airport);
            column.addView(line);
            column.addView(hint);
            card.addView(column);
            card.setOnClickListener(v -> choose(flight));
            binding.journeyResults.addView(card);
        }
    }

    private void choose(JourneyCatalog.Flight flight) {
        ChosenJourney.save(requireContext(), flight);
        Snackbar.make(binding.getRoot(), getString(R.string.journey_chosen, flight.flightNo, flight.from.name,
                flight.to.name), Snackbar.LENGTH_SHORT).show();
        Navigation.findNavController(binding.getRoot()).navigateUp();
    }

    private void findBooking() {
        boolean ref = binding.journeyBookingRef.getText() != null
                && binding.journeyBookingRef.getText().toString().trim().length() >= 5;
        boolean name = binding.journeyLastName.getText() != null
                && !binding.journeyLastName.getText().toString().trim().isEmpty();
        if (!ref || !name) {
            Snackbar.make(binding.getRoot(), R.string.journey_booking_needed, Snackbar.LENGTH_LONG).show();
            return;
        }
        // Demo: the booking is the server's demo trip (IJ 512 Mumbai -> Bengaluru), so the picked SAMPLE trip goes.
        ChosenJourney.clear(requireContext());
        Snackbar.make(binding.getRoot(), R.string.journey_found, Snackbar.LENGTH_SHORT).show();
        Navigation.findNavController(binding.getRoot()).navigateUp();
    }
}
