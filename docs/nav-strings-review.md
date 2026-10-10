# Navigation sentences: Hindi and Marathi review

For a native speaker on the team. Everything here is what the Navigate tab shows and speaks (TTS) in Hindi or
Marathi. Please check that each sentence is natural, polite and clear for a first-time flyer, and that place
names read well inside the sentence (postpositions such as पर / जवळ / येथे after a name).

- **Where to fix:** sentences in `routing-core/src/main/resources/com/safarsaathi/routing/route/``instructions_hi.properties` and `instructions_mr.properties`; place and floor names in
  `mock-data/airports/BOM-T2/map.json` (`names`). After a change run `./mvnw test` in `backend/`
  (`InstructionTextTest` checks a few full Hindi and Marathi routes; update its expected text too).
- **Placeholders:** `{0}` = a place name in the same language, `{1}` = a floor name. Keep them in the sentence.
- **Not here yet:** the rest of the screen (buttons, notices) is English only for now (`values-hi/`,
  `values-mr/` come in phase P).
- Generated from the files by the Gate Navigation session; regenerate after edits instead of editing this file.

## 1. Sentence templates

| Key | When it is used | English | Hindi | Marathi | OK? / suggestion |
|---|---|---|---|---|---|
| `depart` | At the start, and after a lift / escalator / stairs | Head towards {0} | {0} की ओर चलें | {0} च्या दिशेने चला | |
| `slight_left` | Bend of 25-50° to the left at a place | Keep left at {0} | {0} पर हल्का बाएँ रहें | {0} जवळ किंचित डावीकडे राहा | |
| `left` | Turn of 50-140° to the left | Turn left at {0} | {0} पर बाएँ मुड़ें | {0} जवळ डावीकडे वळा | |
| `sharp_left` | Turn of 140-170° to the left | Turn sharp left at {0} | {0} पर पूरी तरह बाएँ मुड़ें | {0} जवळ पूर्ण डावीकडे वळा | |
| `slight_right` | Bend of 25-50° to the right | Keep right at {0} | {0} पर हल्का दाएँ रहें | {0} जवळ किंचित उजवीकडे राहा | |
| `right` | Turn of 50-140° to the right | Turn right at {0} | {0} पर दाएँ मुड़ें | {0} जवळ उजवीकडे वळा | |
| `sharp_right` | Turn of 140-170° to the right | Turn sharp right at {0} | {0} पर पूरी तरह दाएँ मुड़ें | {0} जवळ पूर्ण उजवीकडे वळा | |
| `u_turn` | Turn of 170° or more | Turn around at {0} | {0} पर वापस मुड़ें | {0} जवळ मागे वळा | |
| `security` | Walking through the security checkpoint | Go through {0} | {0} से होकर जाएँ | {0} मधून पुढे जा | |
| `travelator` | Getting on a moving walkway | Take the travelator towards {0} | {0} की ओर ट्रैवलेटर लें | {0} च्या दिशेने ट्रॅव्हलेटर घ्या | |
| `lift_up` | Lift to a higher floor | Take {0} up to {1} | {0} से ऊपर {1} जाएँ | {0} ने वर {1} मजल्यावर जा | |
| `lift_down` | Lift to a lower floor | Take {0} down to {1} | {0} से नीचे {1} जाएँ | {0} ने खाली {1} मजल्यावर जा | |
| `escalator_up` | Escalator up | Take the escalator up to {1} | एस्केलेटर से ऊपर {1} जाएँ | सरकत्या जिन्याने वर {1} मजल्यावर जा | |
| `escalator_down` | Escalator down | Take the escalator down to {1} | एस्केलेटर से नीचे {1} जाएँ | सरकत्या जिन्याने खाली {1} मजल्यावर जा | |
| `stairs_up` | Stairs up | Take the stairs up to {1} | सीढ़ियों से ऊपर {1} जाएँ | जिन्याने वर {1} मजल्यावर जा | |
| `stairs_down` | Stairs down | Take the stairs down to {1} | सीढ़ियों से नीचे {1} जाएँ | जिन्याने खाली {1} मजल्यावर जा | |
| `arrive` | The last step in the list and the banner | Arrive at {0} | {0} पर पहुँचें | {0} येथे पोहोचा | |
| `arrived` | Arrival card and the spoken arrival | You have arrived at {0} | आप {0} पहुँच गए हैं | तुम्ही {0} येथे पोहोचलात | |

## 2. The same sentences with real places (how they read on screen)

| Template | English | Hindi | Marathi |
|---|---|---|---|
| `depart` | Head towards Check-in row D | चेक-इन पंक्ति D की ओर चलें | चेक-इन रांग D च्या दिशेने चला |
| `left` | Turn left at Central hall | मुख्य हॉल पर बाएँ मुड़ें | मुख्य दालन जवळ डावीकडे वळा |
| `right` | Turn right at Departure board | प्रस्थान सूचना बोर्ड पर दाएँ मुड़ें | प्रस्थान फलक जवळ उजवीकडे वळा |
| `slight_left` | Keep left at Belt 3 | बेल्ट 3 पर हल्का बाएँ रहें | बेल्ट 3 जवळ किंचित डावीकडे राहा |
| `security` | Go through Security check | सुरक्षा जांच से होकर जाएँ | सुरक्षा तपासणी मधून पुढे जा |
| `travelator` | Take the travelator towards Gate 33 | गेट 33 की ओर ट्रैवलेटर लें | गेट 33 च्या दिशेने ट्रॅव्हलेटर घ्या |
| `lift_down` | Take Lift A down to Arrivals | लिफ्ट A से नीचे आगमन जाएँ | लिफ्ट A ने खाली आगमन मजल्यावर जा |
| `escalator_up` | Take the escalator up to Departures | एस्केलेटर से ऊपर प्रस्थान जाएँ | सरकत्या जिन्याने वर प्रस्थान मजल्यावर जा |
| `stairs_down` | Take the stairs down to Arrivals | सीढ़ियों से नीचे आगमन जाएँ | जिन्याने खाली आगमन मजल्यावर जा |
| `arrive` | Arrive at Gate 32 | गेट 32 पर पहुँचें | गेट 32 येथे पोहोचा |
| `arrived` | You have arrived at Lost & Found | आप खोया-पाया पहुँच गए हैं | तुम्ही हरवले-सापडले येथे पोहोचलात |

## 3. Place and floor names (from the demo map)

| Id | English | Hindi | Marathi | Hint (en / hi / mr) | OK? / suggestion |
|---|---|---|---|---|---|
| floor `DEP` | Departures | प्रस्थान | प्रस्थान | | |
| floor `ARR` | Arrivals | आगमन | आगमन | | |
| `entry_1` | Entry Gate 1 | प्रवेश द्वार 1 | प्रवेशद्वार 1 |  | |
| `entry_4` | Entry Gate 4 | प्रवेश द्वार 4 | प्रवेशद्वार 4 |  | |
| `departure_board` | Departure board | प्रस्थान सूचना बोर्ड | प्रस्थान फलक |  | |
| `info_landside` | Information desk | सूचना डेस्क | माहिती कक्ष |  | |
| `checkin_a` | Check-in row A | चेक-इन पंक्ति A | चेक-इन रांग A |  | |
| `checkin_b` | Check-in row B | चेक-इन पंक्ति B | चेक-इन रांग B |  | |
| `checkin_c` | Check-in row C | चेक-इन पंक्ति C | चेक-इन रांग C |  | |
| `checkin_d` | Check-in row D | चेक-इन पंक्ति D | चेक-इन रांग D |  | |
| `bag_drop` | Self bag drop | सेल्फ बैग ड्रॉप | सेल्फ बॅग ड्रॉप |  | |
| `washroom_landside` | Washrooms | शौचालय | स्वच्छतागृह |  | |
| `lift_b_dep` | Lift B | लिफ्ट B | लिफ्ट B |  | |
| `security` | Security check | सुरक्षा जांच | सुरक्षा तपासणी |  | |
| `duty_free` | Duty Free | ड्यूटी फ्री | ड्यूटी फ्री |  | |
| `hub` | Central hall | मुख्य हॉल | मुख्य दालन |  | |
| `food_court` | Food court | फूड कोर्ट | फूड कोर्ट |  | |
| `help_airside` | Help desk | सहायता डेस्क | मदत कक्ष |  | |
| `washroom_hub` | Washrooms | शौचालय | स्वच्छतागृह |  | |
| `buggy_hub` | Buggy stop | बग्गी स्टॉप | बग्गी थांबा |  | |
| `lift_a_dep` | Lift A | लिफ्ट A | लिफ्ट A |  | |
| `escalator_dep` | Escalator | एस्केलेटर | सरकता जिना |  | |
| `stairs_dep` | Stairs | सीढ़ियाँ | जिना |  | |
| `pier_c_sign` | Pier C sign | पियर C का संकेत | पियर C फलक | Gates 31–35 / गेट 31–35 / गेट 31–35 | |
| `gate_31` | Gate 31 | गेट 31 | गेट 31 |  | |
| `gate_32` | Gate 32 | गेट 32 | गेट 32 |  | |
| `gate_33` | Gate 33 | गेट 33 | गेट 33 |  | |
| `gate_34` | Gate 34 | गेट 34 | गेट 34 |  | |
| `gate_35` | Gate 35 | गेट 35 | गेट 35 |  | |
| `washroom_c` | Washrooms | शौचालय | स्वच्छतागृह |  | |
| `pier_d_sign` | Pier D sign | पियर D का संकेत | पियर D फलक | Gates 41–45 / गेट 41–45 / गेट 41–45 | |
| `gate_41` | Gate 41 | गेट 41 | गेट 41 |  | |
| `gate_42` | Gate 42 | गेट 42 | गेट 42 |  | |
| `gate_43` | Gate 43 | गेट 43 | गेट 43 |  | |
| `gate_44` | Gate 44 | गेट 44 | गेट 44 |  | |
| `gate_45` | Gate 45 | गेट 45 | गेट 45 |  | |
| `washroom_d` | Washrooms | शौचालय | स्वच्छतागृह |  | |
| `lift_a_arr` | Lift A | लिफ्ट A | लिफ्ट A |  | |
| `escalator_arr` | Escalator | एस्केलेटर | सरकता जिना |  | |
| `stairs_arr` | Stairs | सीढ़ियाँ | जिना |  | |
| `baggage_hall` | Baggage hall | बैगेज हॉल | बॅगेज हॉल |  | |
| `belt_1` | Belt 1 | बेल्ट 1 | बेल्ट 1 |  | |
| `belt_2` | Belt 2 | बेल्ट 2 | बेल्ट 2 |  | |
| `belt_3` | Belt 3 | बेल्ट 3 | बेल्ट 3 |  | |
| `belt_4` | Belt 4 | बेल्ट 4 | बेल्ट 4 |  | |
| `baggage_services` | Airline baggage services | एयरलाइन बैगेज सेवा | विमान कंपनी बॅगेज सेवा |  | |
| `washroom_arr` | Washrooms | शौचालय | स्वच्छतागृह |  | |
| `arr_exit` | Arrivals exit | आगमन निकास | आगमन निर्गम |  | |
| `gt_lobby` | Ground transportation lobby | ग्राउंड ट्रांसपोर्टेशन लॉबी | ग्राउंड ट्रान्सपोर्टेशन लॉबी |  | |
| `lost_found` | Lost & Found | खोया-पाया | हरवले-सापडले | Ground Transportation Lobby, Level 1 (to be verified) / ग्राउंड ट्रांसपोर्टेशन लॉबी, लेवल 1 (पुष्टि बाकी) / ग्राउंड ट्रान्सपोर्टेशन लॉबी, लेव्हल 1 (पडताळणी बाकी) | |
| `help_arr` | Arrivals information desk | आगमन सूचना डेस्क | आगमन माहिती कक्ष |  | |
| `lift_b_arr` | Lift B | लिफ्ट B | लिफ्ट B |  | |
