package com.safarsaathi.app.ui.navigate;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import com.safarsaathi.app.R;
import com.safarsaathi.routing.map.NodeType;

/**
 * How each kind of place looks on the map and in lists, Google-Maps style: a white icon on a coloured circle
 * (colours in res/values/colors.xml, poi_*). Places of one family share a colour: green = ways in and out,
 * orange = check-in and bags, red = checkpoints, blue = piers and gates, grey = lifts / escalators / stairs,
 * purple = help, teal = landmarks. Gates are drawn as a blue badge with their number instead of an icon.
 */
final class MapStyle {

    private MapStyle() {
    }

    @ColorRes
    static int color(NodeType type) {
        switch (type) {
            case ENTRY:
                return R.color.poi_entry;
            case CHECKIN_ROW:
            case BAG_DROP:
            case BAGGAGE_BELT:
                return R.color.poi_checkin;
            case SECURITY:
            case IMMIGRATION:
                return R.color.poi_security;
            case PIER_JUNCTION:
            case GATE:
                return R.color.poi_gate;
            case LIFT:
            case ESCALATOR:
            case STAIRS:
            case TRAVELATOR:
                return R.color.poi_transport;
            case WASHROOM:
                return R.color.poi_washroom;
            case HELP_DESK:
            case BUGGY_STOP:
            case BAGGAGE_SERVICES:
            case LOST_FOUND:
                return R.color.poi_help;
            case LANDMARK:
            default:
                return R.color.poi_landmark;
        }
    }

    @DrawableRes
    static int icon(NodeType type) {
        switch (type) {
            case ENTRY:
                return R.drawable.ic_poi_entry;
            case CHECKIN_ROW:
                return R.drawable.ic_poi_checkin;
            case BAG_DROP:
            case BAGGAGE_BELT:
            case BAGGAGE_SERVICES:
                return R.drawable.ic_poi_luggage;
            case SECURITY:
            case IMMIGRATION:
                return R.drawable.ic_poi_security;
            case PIER_JUNCTION:
            case GATE:
                return R.drawable.ic_poi_signpost;
            case LIFT:
                return R.drawable.ic_poi_lift;
            case ESCALATOR:
                return R.drawable.ic_poi_escalator;
            case STAIRS:
                return R.drawable.ic_poi_stairs;
            case TRAVELATOR:
                return R.drawable.ic_poi_travelator;
            case WASHROOM:
                return R.drawable.ic_poi_wc;
            case HELP_DESK:
                return R.drawable.ic_poi_info;
            case BUGGY_STOP:
                return R.drawable.ic_poi_buggy;
            case LOST_FOUND:
                return R.drawable.ic_poi_search;
            case LANDMARK:
            default:
                return R.drawable.ic_poi_star;
        }
    }

    /** Lower = more important: its label wins when labels would overlap. Gates first. */
    static int labelPriority(NodeType type) {
        switch (type) {
            case GATE:
                return 0;
            case SECURITY:
            case IMMIGRATION:
                return 1;
            case ENTRY:
                return 2;
            case PIER_JUNCTION:
                return 3;
            case CHECKIN_ROW:
            case BAG_DROP:
            case BAGGAGE_BELT:
            case BAGGAGE_SERVICES:
            case LOST_FOUND:
                return 4;
            case HELP_DESK:
                return 5;
            case LANDMARK:
                return 6;
            case LIFT:
            case ESCALATOR:
            case STAIRS:
                return 7;
            default:
                return 8;
        }
    }
}
