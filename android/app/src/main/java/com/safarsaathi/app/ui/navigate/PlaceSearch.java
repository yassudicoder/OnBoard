package com.safarsaathi.app.ui.navigate;

import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safarsaathi.app.R;
import com.safarsaathi.app.databinding.DialogPlaceSearchBinding;
import com.safarsaathi.app.databinding.ItemPlaceBinding;
import com.safarsaathi.routing.map.AirportMap;
import com.safarsaathi.routing.map.MapFloor;
import com.safarsaathi.routing.map.MapNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Navigate tab's place search ("Search the terminal", "Where to?", "Where are you now?"): every place on every
 * floor, the shown floor first, most important kinds first (gates, security, entries...). Typing filters by name in
 * the app language and in English, and by number ("32" finds Gate 32).
 */
final class PlaceSearch {

    interface OnPicked {
        void onPicked(MapNode node);
    }

    private PlaceSearch() {
    }

    static void show(Fragment fragment, AirportMap map, String language, String shownFloor, @StringRes int title,
                     OnPicked onPicked) {
        List<MapNode> all = new ArrayList<>(map.nodes);
        Collections.sort(all, (a, b) -> {
            int byFloor = Boolean.compare(!a.floor.equals(shownFloor), !b.floor.equals(shownFloor));
            if (byFloor != 0) {
                return byFloor;
            }
            int byKind = Integer.compare(MapStyle.labelPriority(a.type), MapStyle.labelPriority(b.type));
            return byKind != 0 ? byKind : naturalCompare(a.name(language), b.name(language));
        });
        List<MapNode> shown = new ArrayList<>(all);

        DialogPlaceSearchBinding binding = DialogPlaceSearchBinding.inflate(fragment.getLayoutInflater());
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return shown.size();
            }

            @Override
            public Object getItem(int position) {
                return shown.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ItemPlaceBinding row = convertView == null
                        ? ItemPlaceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false)
                        : ItemPlaceBinding.bind(convertView);
                MapNode node = shown.get(position);
                row.placeName.setText(node.name(language));
                MapFloor floor = map.floor(node.floor);
                row.placeDetail.setText(fragment.getString(R.string.nav_place_kind_floor,
                        fragment.getString(NavigateFragment.typeLabel(node)),
                        floor == null ? node.floor : floor.name(language)));
                row.placeIcon.setImageResource(MapStyle.icon(node.type));
                row.placeIcon.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(parent.getContext(), MapStyle.color(node.type))));
                return row.getRoot();
            }
        };
        binding.placeSearchList.setAdapter(adapter);

        AlertDialog dialog = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(title)
                .setView(binding.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        binding.placeSearchList.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            onPicked.onPicked(shown.get(position));
        });
        binding.placeSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                String query = text.toString().trim().toLowerCase(Locale.ROOT);
                shown.clear();
                for (MapNode node : all) {
                    if (query.isEmpty() || matches(node, language, query)) {
                        shown.add(node);
                    }
                }
                adapter.notifyDataSetChanged();
                binding.placeSearchEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
        dialog.show();
    }

    private static boolean matches(MapNode node, String language, String query) {
        return node.name(language).toLowerCase(Locale.ROOT).contains(query)
                || node.name("en").toLowerCase(Locale.ROOT).contains(query)
                || (node.ref != null && node.ref.toLowerCase(Locale.ROOT).equals(query));
    }

    /** "Gate 9" before "Gate 10": compares numbers inside names as numbers. */
    static int naturalCompare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                int byNumber = Long.compare(Long.parseLong(a.substring(startA, i)), Long.parseLong(b.substring(startB, j)));
                if (byNumber != 0) {
                    return byNumber;
                }
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
