# Ask OnBoard: Hindi, Marathi and Hinglish review

For native speakers on the team. Everything here is what Ask OnBoard (the assistant on Home) shows and speaks
(TTS) in Hindi, Marathi or Hinglish, and the words it understands. Please check that each sentence is natural,
polite and clear for a first-time flyer, and add words or spellings people really use.

- **Where to fix:** in `android/app/src/main/resources/com/safarsaathi/app/data/ask/`: the answers in
  `answers_hi.properties`, `answers_mr.properties`, `answers_hinglish.properties`; the understood words in
  `lexicon_hi.properties`, `lexicon_mr.properties`, `lexicon_hinglish.properties`, `lexicon_mr_latn.properties`.
  After a change run `./gradlew :app:testDebugUnitTest` in `android/`: `AnswerTextTest` checks every language
  has every sentence with the same `{placeholders}`; `IntentGoldenSetTest` checks the test questions (section 4).
- **Placeholders** such as `{flight}`, `{gate}`, `{close}`, `{date}` are filled by the app with real data. Keep them,
  move them wherever the grammar needs. Never write a number, gate or time into a sentence.
- **Hinglish** = Hindi written in English letters. Marathi typed in English letters is answered in Marathi script.
- Generated from the files (Ask OnBoard session, 2026-10-11); regenerate after edits instead of editing this file.

## 1. Answer sentences, buttons and quick questions


### The trip itself

| Key | English | Hindi | Marathi | Hinglish | OK? / suggestion |
|---|---|---|---|---|---|
| `trip.missing` | Your trip isn't saved on this phone yet. Open Home once while you're online to load it. | आपकी यात्रा अभी इस फ़ोन पर सेव नहीं है। इंटरनेट चालू होने पर एक बार होम खोलें। | तुमचा प्रवास अजून या फोनवर सेव्ह झालेला नाही. इंटरनेट चालू असताना एकदा होम उघडा. | Aapki trip abhi is phone par save nahi hai. Internet chalu hone par ek baar Home kholiye. |  |
| `flight.none` | There's no flight in your saved trip. | आपकी सेव की गई यात्रा में कोई फ्लाइट नहीं है। | तुमच्या सेव्ह केलेल्या प्रवासात कोणतीही फ्लाइट नाही. | Aapki saved trip mein koi flight nahi hai. |  |
| `flight.on_plan` | {flight} departs at {departs} and lands at {arrives}. No change in your saved plan. | फ्लाइट {flight} {departs} पर रवाना होगी और {arrives} पर पहुँचेगी। आपकी सेव की गई योजना में कोई बदलाव नहीं है। | फ्लाइट {flight} {departs} वाजता सुटेल आणि {arrives} वाजता पोहोचेल. तुमच्या सेव्ह केलेल्या योजनेत काहीही बदल नाही. | Flight {flight} {departs} baje nikalegi aur {arrives} baje pahunchegi. Aapke saved plan mein koi badlav nahi hai. |  |
| `flight.delayed` | {flight} is late: it now departs at {departs} (planned {planned}) and lands at {arrives}. | फ्लाइट {flight} देर से है: अब यह {departs} पर रवाना होगी (पहले {planned} थी) और {arrives} पर पहुँचेगी। | फ्लाइट {flight} उशिरा आहे: आता ती {departs} वाजता सुटेल (आधी {planned} होती) आणि {arrives} वाजता पोहोचेल. | Flight {flight} late hai: ab {departs} baje nikalegi (pehle {planned} thi) aur {arrives} baje pahunchegi. |  |
| `flight.changed` | {flight} has changed: it now departs at {departs} and lands at {arrives}. | फ्लाइट {flight} में बदलाव हुआ है: अब यह {departs} पर रवाना होगी और {arrives} पर पहुँचेगी। | फ्लाइट {flight} मध्ये बदल झाला आहे: आता ती {departs} वाजता सुटेल आणि {arrives} वाजता पोहोचेल. | Flight {flight} mein badlav hua hai: ab {departs} baje nikalegi aur {arrives} baje pahunchegi. |  |
| `flight.needs_you` | {flight} has changed and needs your decision. Open Home to choose. | फ्लाइट {flight} में बदलाव हुआ है और आपका फ़ैसला चाहिए। चुनने के लिए होम खोलें। | फ्लाइट {flight} मध्ये बदल झाला आहे आणि तुमचा निर्णय हवा आहे. निवडण्यासाठी होम उघडा. | Flight {flight} mein badlav hua hai aur aapka faisla chahiye. Chunne ke liye Home kholiye. |  |
| `flight.cancelled` | {flight} is cancelled. Open Home to see what you can do. | फ्लाइट {flight} रद्द हो गई है। आगे क्या कर सकते हैं, यह होम पर देखें। | फ्लाइट {flight} रद्द झाली आहे. पुढे काय करता येईल ते होमवर पाहा. | Flight {flight} cancel ho gayi hai. Aage kya kar sakte hain, Home par dekhiye. |  |
| `flight.done` | {flight} is completed in your saved plan. | आपकी सेव की गई योजना में फ्लाइट {flight} पूरी हो चुकी है। | तुमच्या सेव्ह केलेल्या योजनेत फ्लाइट {flight} पूर्ण झाली आहे. | Aapke saved plan mein flight {flight} poori ho chuki hai. |  |

### Gate and boarding (no gate source until phase G4, so "not announced" for now)

| Key | English | Hindi | Marathi | Hinglish | OK? / suggestion |
|---|---|---|---|---|---|
| `gate.known` | Your gate for {flight} is {gate}. | आपकी फ्लाइट {flight} का गेट {gate} है। | तुमच्या फ्लाइट {flight} चे गेट {gate} आहे. | Aapki flight {flight} ka gate {gate} hai. |  |
| `gate.changed` | Gate changed: {flight} now leaves from gate {gate} (it was {previous}). | गेट बदल गया है: फ्लाइट {flight} अब गेट {gate} से जाएगी (पहले {previous} था)। | गेट बदलले आहे: फ्लाइट {flight} आता गेट {gate} वरून जाईल (आधी {previous} होते). | Gate badal gaya hai: flight {flight} ab gate {gate} se jayegi (pehle {previous} tha). |  |
| `gate.not_announced` | The gate for {flight} is not announced yet. Your flight departs at {departs}. | फ्लाइट {flight} का गेट अभी घोषित नहीं हुआ है। आपकी फ्लाइट {departs} पर रवाना होगी। | फ्लाइट {flight} चे गेट अजून जाहीर झालेले नाही. तुमची फ्लाइट {departs} वाजता सुटेल. | Flight {flight} ka gate abhi announce nahi hua hai. Aapki flight {departs} baje nikalegi. |  |
| `gate.boarding_close` | Boarding closes at {close}. | बोर्डिंग {close} पर बंद होगी। | बोर्डिंग {close} वाजता बंद होईल. | Boarding {close} baje band hogi. |  |
| `gate.boarding_close_estimate` | Boarding closes at about {close} (estimate). | बोर्डिंग लगभग {close} पर बंद होगी (अनुमान)। | बोर्डिंग साधारण {close} वाजता बंद होईल (अंदाज). | Boarding lagbhag {close} baje band hogi (estimate). |  |
| `boarding.start` | Boarding for {flight} starts at {start}. | फ्लाइट {flight} की बोर्डिंग {start} पर शुरू होगी। | फ्लाइट {flight} चे बोर्डिंग {start} वाजता सुरू होईल. | Flight {flight} ki boarding {start} baje shuru hogi. |  |
| `boarding.start_estimate` | Boarding for {flight} starts at about {start} (estimate). | फ्लाइट {flight} की बोर्डिंग लगभग {start} पर शुरू होगी (अनुमान)। | फ्लाइट {flight} चे बोर्डिंग साधारण {start} वाजता सुरू होईल (अंदाज). | Flight {flight} ki boarding lagbhag {start} baje shuru hogi (estimate). |  |
| `boarding.start_unknown` | The boarding time for {flight} is not announced yet. Your flight departs at {departs}. | फ्लाइट {flight} की बोर्डिंग का समय अभी घोषित नहीं हुआ है। आपकी फ्लाइट {departs} पर रवाना होगी। | फ्लाइट {flight} च्या बोर्डिंगची वेळ अजून जाहीर झालेली नाही. तुमची फ्लाइट {departs} वाजता सुटेल. | Flight {flight} ki boarding ka time abhi announce nahi hua hai. Aapki flight {departs} baje nikalegi. |  |
| `boarding.close` | Boarding for {flight} closes at {close}. | फ्लाइट {flight} की बोर्डिंग {close} पर बंद होगी। | फ्लाइट {flight} चे बोर्डिंग {close} वाजता बंद होईल. | Flight {flight} ki boarding {close} baje band hogi. |  |
| `boarding.close_estimate` | Boarding for {flight} closes at about {close} (estimate). | फ्लाइट {flight} की बोर्डिंग लगभग {close} पर बंद होगी (अनुमान)। | फ्लाइट {flight} चे बोर्डिंग साधारण {close} वाजता बंद होईल (अंदाज). | Flight {flight} ki boarding lagbhag {close} baje band hogi (estimate). |  |
| `boarding.close_unknown` | The boarding close time for {flight} is not announced yet. Your flight departs at {departs}. | फ्लाइट {flight} की बोर्डिंग बंद होने का समय अभी घोषित नहीं हुआ है। आपकी फ्लाइट {departs} पर रवाना होगी। | फ्लाइट {flight} चे बोर्डिंग बंद होण्याची वेळ अजून जाहीर झालेली नाही. तुमची फ्लाइट {departs} वाजता सुटेल. | Flight {flight} ki boarding band hone ka time abhi announce nahi hua hai. Aapki flight {departs} baje nikalegi. |  |

### Connection

| Key | English | Hindi | Marathi | Hinglish | OK? / suggestion |
|---|---|---|---|---|---|
| `connection.none` | Your saved trip has no connecting flight after {flight}. | आपकी सेव की गई यात्रा में फ्लाइट {flight} के बाद कोई कनेक्टिंग फ्लाइट नहीं है। | तुमच्या सेव्ह केलेल्या प्रवासात फ्लाइट {flight} नंतर कोणतीही कनेक्टिंग फ्लाइट नाही. | Aapki saved trip mein flight {flight} ke baad koi connecting flight nahi hai. |  |
| `connection.gap` | {flight} lands at {arrives}. {next} departs at {departs}, so you have {gap} between them. The phone can't tell whether that's enough time at the airport: check with the airline. | फ्लाइट {flight} {arrives} पर पहुँचेगी। फ्लाइट {next} {departs} पर रवाना होगी, यानी बीच में {gap} हैं। यह समय एयरपोर्ट पर काफ़ी है या नहीं, यह फ़ोन नहीं बता सकता: एयरलाइन से पूछें। | फ्लाइट {flight} {arrives} वाजता पोहोचेल. फ्लाइट {next} {departs} वाजता सुटेल, म्हणजे मध्ये {gap} आहेत. विमानतळावर एवढा वेळ पुरेसा आहे की नाही हे फोन सांगू शकत नाही: एअरलाइनला विचारा. | Flight {flight} {arrives} baje pahunchegi. Flight {next} {departs} baje nikalegi, yaani beech mein {gap} hain. Airport par itna time kaafi hai ya nahi, yeh phone nahi bata sakta: airline se poochhiye. |  |
| `connection.too_late` | {flight} now lands at {arrives}, after {next} departs at {departs}. Talk to the airline about your connection. | फ्लाइट {flight} अब {arrives} पर पहुँचेगी, जबकि फ्लाइट {next} {departs} पर रवाना हो जाएगी। अपने कनेक्शन के बारे में एयरलाइन से बात करें। | फ्लाइट {flight} आता {arrives} वाजता पोहोचेल, पण फ्लाइट {next} {departs} वाजताच सुटेल. तुमच्या कनेक्शनबद्दल एअरलाइनशी बोला. | Flight {flight} ab {arrives} baje pahunchegi, jabki flight {next} {departs} baje nikal jayegi. Apne connection ke baare mein airline se baat kijiye. |  |
| `connection.first_cancelled` | {flight} is cancelled, so ask the airline about your connecting flight {next}. | फ्लाइट {flight} रद्द हो गई है, इसलिए अपनी कनेक्टिंग फ्लाइट {next} के बारे में एयरलाइन से पूछें। | फ्लाइट {flight} रद्द झाली आहे, म्हणून तुमच्या कनेक्टिंग फ्लाइट {next} बद्दल एअरलाइनला विचारा. | Flight {flight} cancel ho gayi hai, isliye apni connecting flight {next} ke baare mein airline se poochhiye. |  |

### Bags (no bag records until phase B1)

| Key | English | Hindi | Marathi | Hinglish | OK? / suggestion |
|---|---|---|---|---|---|
| `bag.none` | No bag is registered on this phone yet. Register it in the Bags tab before you hand it over at the counter. | इस फ़ोन पर अभी कोई बैग रजिस्टर नहीं है। काउंटर पर देने से पहले बैग्स टैब में उसे रजिस्टर करें। | या फोनवर अजून कोणतीही बॅग नोंदवलेली नाही. काउंटरवर देण्यापूर्वी Bags टॅबमध्ये ती नोंदवा. | Is phone par abhi koi bag register nahi hai. Counter par dene se pehle Bags tab mein register kijiye. |  |
| `bag.status` | Your bag (tag ending {tag}): {status}. | आपका बैग (टैग के आख़िरी अंक {tag}): {status}। | तुमची बॅग (टॅगचे शेवटचे अंक {tag}): {status}. | Aapka bag (tag ke aakhri ank {tag}): {status}. |  |
| `bag.status_REGISTERED` | registered, not handed over yet | रजिस्टर है, अभी काउंटर पर नहीं दिया | नोंदवली आहे, अजून काउंटरवर दिलेली नाही | register hai, abhi counter par nahi diya |  |
| `bag.status_DROPPED` | handed over at the counter | काउंटर पर दे दिया गया | काउंटरवर दिली आहे | counter par de diya gaya |  |
| `bag.status_LOADED` | loaded on the plane | विमान में रख दिया गया | विमानात ठेवली आहे | plane mein rakh diya gaya |  |
| `bag.status_ARRIVED` | arrived with your flight | आपकी फ्लाइट के साथ पहुँच गया | तुमच्या फ्लाइटसोबत पोहोचली आहे | aapki flight ke saath pahunch gaya |  |
| `bag.status_ON_BELT` | on the belt now | अभी बेल्ट पर है | आता बेल्टवर आहे | abhi belt par hai |  |
| `bag.status_COLLECTED` | collected | ले लिया गया | घेतली आहे | le liya gaya |  |
| `bag.status_DELAYED` | delayed | देर से आ रहा है | उशिरा येत आहे | late aa raha hai |  |
| `bag.status_DAMAGED` | reported damaged | ख़राब होने की रिपोर्ट हुई है | खराब झाल्याची नोंद आहे | kharab hone ki report hui hai |  |
| `bag.status_MISSING` | reported missing | गुम होने की रिपोर्ट हुई है | हरवल्याची नोंद आहे | gum hone ki report hui hai |  |
| `belt.known` | Bags from {flight} come on belt {belt}. | फ्लाइट {flight} के बैग बेल्ट {belt} पर आएँगे। | फ्लाइट {flight} च्या बॅगा बेल्ट {belt} वर येतील. | Flight {flight} ke bag belt {belt} par aayenge. |  |
| `belt.unknown` | The baggage belt for {flight} is not announced yet. Look for your flight number on the screens in the baggage hall. | फ्लाइट {flight} का बैगेज बेल्ट अभी घोषित नहीं हुआ है। बैगेज हॉल की स्क्रीन पर अपना फ्लाइट नंबर देखें। | फ्लाइट {flight} चा बॅगेज बेल्ट अजून जाहीर झालेला नाही. बॅगेज हॉलमधील स्क्रीनवर तुमचा फ्लाइट नंबर पाहा. | Flight {flight} ka baggage belt abhi announce nahi hua hai. Baggage hall ki screen par apna flight number dekhiye. |  |

### What next

| Key | English | Hindi | Marathi | Hinglish | OK? / suggestion |
|---|---|---|---|---|---|
| `next.airport` | Next: {flight} departs at {departs} from {from}. At the airport: check-in or bag drop, then security, then your gate. | आगे: फ्लाइट {flight} {departs} पर {from} से रवाना होगी। एयरपोर्ट पर: चेक-इन या बैग ड्रॉप, फिर सुरक्षा जांच, फिर अपना गेट। | पुढे: फ्लाइट {flight} {departs} वाजता {from} येथून सुटेल. विमानतळावर: चेक-इन किंवा बॅग ड्रॉप, मग सुरक्षा तपासणी, मग तुमचे गेट. | Aage: flight {flight} {departs} baje {from} se nikalegi. Airport par: check-in ya bag drop, phir security, phir apna gate. |  |
| `next.airport_gate` | Next: {flight} departs at {departs} from {from}. At the airport: check-in or bag drop, then security, then gate {gate}. | आगे: फ्लाइट {flight} {departs} पर {from} से रवाना होगी। एयरपोर्ट पर: चेक-इन या बैग ड्रॉप, फिर सुरक्षा जांच, फिर गेट {gate}। | पुढे: फ्लाइट {flight} {departs} वाजता {from} येथून सुटेल. विमानतळावर: चेक-इन किंवा बॅग ड्रॉप, मग सुरक्षा तपासणी, मग गेट {gate}. | Aage: flight {flight} {departs} baje {from} se nikalegi. Airport par: check-in ya bag drop, phir security, phir gate {gate}. |  |
| `next.block` | Next in your trip: {title} at {time}. | आपकी यात्रा में आगे: {title}, {time} पर। | तुमच्या प्रवासात पुढे: {title}, {time} वाजता. | Aapki trip mein aage: {title}, {time} baje. |  |
| `next.nothing` | Nothing else is planned in your saved trip. | आपकी सेव की गई यात्रा में आगे कुछ और नहीं है। | तुमच्या सेव्ह केलेल्या प्रवासात पुढे काहीही नाही. | Aapki saved trip mein aage kuch aur nahi hai. |  |

### Documents: summaries from the Document Vault (type, first name, dates, countries, status; never numbers)

| Key | English | Hindi | Marathi | Hinglish | OK? / suggestion |
|---|---|---|---|---|---|
| `documents.not_available` | There's no Document Vault on this phone yet, so I can't check your documents. | इस फ़ोन पर अभी डॉक्यूमेंट वॉल्ट नहीं है, इसलिए मैं आपके दस्तावेज़ नहीं देख सकता। | या फोनवर अजून डॉक्युमेंट व्हॉल्ट नाही, म्हणून मी तुमची कागदपत्रे पाहू शकत नाही. | Is phone par abhi Document Vault nahi hai, isliye main aapke documents nahi dekh sakta. |  |
| `documents.locked` | Your Document Vault is locked. Unlock it to see this. | आपका डॉक्यूमेंट वॉल्ट लॉक है। इसे देखने के लिए वॉल्ट अनलॉक करें। | तुमचा डॉक्युमेंट व्हॉल्ट लॉक आहे. हे पाहण्यासाठी व्हॉल्ट अनलॉक करा. | Aapka Document Vault lock hai. Ise dekhne ke liye vault unlock kijiye. |  |
| `documents.your_vault` | your vault | आपका वॉल्ट | तुमचा व्हॉल्ट | aapka vault |  |
| `documents.none_of_type` | No {document} is saved in {trip}. | {trip} में कोई {document} सेव नहीं है। | {trip} मध्ये कोणताही {document} सेव्ह केलेला नाही. | {trip} mein koi {document} save nahi hai. |  |
| `documents.all_ready` | All {count} saved documents in {trip} are ready. | {trip} में सेव किए गए सभी {count} दस्तावेज़ तैयार हैं। | {trip} मधील सेव्ह केलेली सर्व {count} कागदपत्रे तयार आहेत. | {trip} mein save kiye gaye saare {count} documents ready hain. |  |
| `documents.attention` | Needs your attention in {trip}: {items} | {trip} में इन पर ध्यान दें: {items} | {trip} मध्ये याकडे लक्ष द्या: {items} | {trip} mein inpar dhyan dijiye: {items} |  |
| `documents.list` | In {trip}: {items} | {trip} में: {items} | {trip} मध्ये: {items} | {trip} mein: {items} |  |
| `documents.sample` | SAMPLE | SAMPLE | SAMPLE | SAMPLE |  |
| `documents.requirements_note` | I can't tell which documents a country requires: check with the airline or embassy. | किसी देश के लिए कौन-से दस्तावेज़ ज़रूरी हैं, यह मैं नहीं बता सकता: एयरलाइन या दूतावास से पूछें। | एखाद्या देशासाठी कोणती कागदपत्रे आवश्यक आहेत हे मी सांगू शकत नाही: एअरलाइन किंवा दूतावासाला विचारा. | Kisi desh ke liye kaun se documents zaroori hain, yeh main nahi bata sakta: airline ya embassy se poochhiye. |  |
| `doc.kind_PASSPORT` | passport | पासपोर्ट | पासपोर्ट | passport |  |
| `doc.kind_VISA` | visa | वीज़ा | व्हिसा | visa |  |
| `doc.kind_INSURANCE` | travel insurance | यात्रा बीमा | प्रवास विमा | travel insurance |  |
| `doc.PASSPORT` | Passport | पासपोर्ट | पासपोर्ट | Passport |  |
| `doc.ID_CARD` | ID card | पहचान पत्र | ओळखपत्र | ID card |  |
| `doc.VISA` | Visa | वीज़ा | व्हिसा | Visa |  |
| `doc.E_VISA` | E-visa | ई-वीज़ा | ई-व्हिसा | E-visa |  |
| `doc.TRANSIT_VISA` | Transit visa | ट्रांज़िट वीज़ा | ट्रान्झिट व्हिसा | Transit visa |  |
| `doc.ENTRY_FORM` | Entry form | प्रवेश फ़ॉर्म | प्रवेश अर्ज | Entry form |  |
| `doc.TICKET` | Ticket | टिकट | तिकीट | Ticket |  |
| `doc.BOARDING_PASS` | Boarding pass | बोर्डिंग पास | बोर्डिंग पास | Boarding pass |  |
| `doc.HOTEL` | Hotel booking | होटल बुकिंग | हॉटेल बुकिंग | Hotel booking |  |
| `doc.INSURANCE` | Travel insurance | यात्रा बीमा | प्रवास विमा | Travel insurance |  |
| `doc.VACCINATION_CERT` | Vaccination certificate | टीकाकरण प्रमाणपत्र | लसीकरण प्रमाणपत्र | Vaccination certificate |  |
| `doc.OTHER` | Document | दस्तावेज़ | कागदपत्र | Document |  |
| `doc.for_country` | {document} for {country} | {country} के लिए {document} | {country}साठी {document} | {country} ke liye {document} |  |
| `doc.line` | {document}: {validity}, {status} | {document}: {validity}, {status} | {document}: {validity}, {status} | {document}: {validity}, {status} |  |
| `doc.line_named` | {document} ({name}): {validity}, {status} | {document} ({name}): {validity}, {status} | {document} ({name}): {validity}, {status} | {document} ({name}): {validity}, {status} |  |
| `doc.valid_until` | valid until {date} | {date} तक वैध | {date} पर्यंत वैध | {date} tak valid |  |
| `doc.no_date` | no date saved | कोई तारीख़ सेव नहीं | तारीख सेव्ह केलेली नाही | koi date save nahi |  |
| `status.READY` | ready | तैयार | तयार | ready |  |
| `status.EXPIRES_SOON` | expires soon ({days} days left) | जल्द समाप्त ({days} दिन बाकी) | लवकरच संपेल ({days} दिवस बाकी) | jaldi expire hoga ({days} din baaki) |  |
| `status.EXPIRED` | expired | समाप्त हो चुका | मुदत संपली | expire ho chuka |  |
| `status.MISMATCH` | the name doesn't match the trip, please check | नाम यात्रा से मेल नहीं खाता, जांच लें | नाव प्रवासाशी जुळत नाही, तपासा | naam trip se match nahi karta, check kijiye |  |
| `status.NOT_OFFLINE_YET` | not yet checked for offline use | ऑफ़लाइन उपयोग के लिए अभी जांचा नहीं गया | ऑफलाइन वापरासाठी अजून तपासलेले नाही | offline use ke liye abhi check nahi hua |  |
| `status.MISSING` | its saved file is missing on this phone, add it again | सेव की गई फ़ाइल इस फ़ोन पर नहीं मिली, फिर से जोड़ें | सेव्ह केलेली फाइल या फोनवर सापडली नाही, पुन्हा जोडा | save ki hui file is phone par nahi mili, dobara jodiye |  |

### Places in the terminal (from the demo map)

| Key | English | Hindi | Marathi | Hinglish | OK? / suggestion |
|---|---|---|---|---|---|
| `where.header_WASHROOM` | Washrooms in Terminal 2 | टर्मिनल 2 में शौचालय | टर्मिनल 2 मधील स्वच्छतागृहे | Terminal 2 mein washroom |  |
| `where.header_HELP_DESK` | Help desks in Terminal 2 | टर्मिनल 2 में सहायता डेस्क | टर्मिनल 2 मधील मदत कक्ष | Terminal 2 mein help desk |  |
| `where.header_LIFT` | Lifts in Terminal 2 | टर्मिनल 2 में लिफ्ट | टर्मिनल 2 मधील लिफ्ट | Terminal 2 mein lift |  |
| `where.closest_to_gate` | (closest to gate {gate} first) | (गेट {gate} के सबसे पास वाले पहले) | (गेट {gate} च्या सर्वात जवळची आधी) | (gate {gate} ke sabse paas wale pehle) |  |
| `where.item` | {name} near {near} ({floor}, {zone}) | {name}, {near} के पास ({floor}, {zone}) | {name}, {near} जवळ ({floor}, {zone}) | {name}, {near} ke paas ({floor}, {zone}) |  |
| `where.item_plain` | near {near} ({floor}, {zone}) | {near} के पास ({floor}, {zone}) | {near} जवळ ({floor}, {zone}) | {near} ke paas ({floor}, {zone}) |  |
| `where.item_no_near` | {name} ({floor}, {zone}) | {name} ({floor}, {zone}) | {name} ({floor}, {zone}) | {name} ({floor}, {zone}) |  |
| `where.demo_map` | From the demo map, not an official airport map. | यह डेमो नक्शे से है, एयरपोर्ट का आधिकारिक नक्शा नहीं। | हे डेमो नकाशावरून आहे, विमानतळाचा अधिकृत नकाशा नाही. | Yeh demo map se hai, airport ka official map nahi. |  |
| `where.none` | I couldn't find that on the Terminal 2 map. | यह टर्मिनल 2 के नक्शे पर नहीं मिला। | हे टर्मिनल 2 च्या नकाशावर सापडले नाही. | Yeh Terminal 2 ke map par nahi mila. |  |
| `where.no_map` | The Terminal 2 map isn't on this phone yet. Open the Navigate tab once to load it. | टर्मिनल 2 का नक्शा अभी इस फ़ोन पर नहीं है। उसे लाने के लिए एक बार Navigate टैब खोलें। | टर्मिनल 2 चा नकाशा अजून या फोनवर नाही. तो आणण्यासाठी एकदा Navigate टॅब उघडा. | Terminal 2 ka map abhi is phone par nahi hai. Ek baar Navigate tab kholiye. |  |
| `zone.after_security` | after security | सुरक्षा जांच के बाद | सुरक्षा तपासणीनंतर | security ke baad |  |
| `zone.before_security` | before security | सुरक्षा जांच से पहले | सुरक्षा तपासणीपूर्वी | security se pehle |  |
| `zone.arrivals_inside` | baggage hall side | बैगेज हॉल की तरफ़ | बॅगेज हॉलच्या बाजूला | baggage hall ki taraf |  |
| `zone.arrivals_outside` | outside, after the exit | बाहर, निकास के बाद | बाहेर, निर्गमनानंतर | bahar, exit ke baad |  |
| `list.start` | : | : | : | : |  |
| `list.separator` | ; | ; | ; | ; |  |
| `sentence.end` | . | । | . | . |  |

### Other questions

| Key | English | Hindi | Marathi | Hinglish | OK? / suggestion |
|---|---|---|---|---|---|
| `help.menu` | I can answer these on your phone, even offline: your gate, boarding times, flight status, your connection, your bag and belt, what to do next, and where washrooms, help desks and lifts are in Terminal 2. | मैं ये सवाल आपके फ़ोन पर ही, बिना इंटरनेट के भी, बता सकता हूँ: आपका गेट, बोर्डिंग का समय, फ्लाइट की स्थिति, कनेक्शन, आपका बैग और बेल्ट, आगे क्या करना है, और टर्मिनल 2 में शौचालय, सहायता डेस्क और लिफ्ट कहाँ हैं। | मी हे प्रश्न तुमच्या फोनवरच, इंटरनेटशिवायसुद्धा, सांगू शकतो: तुमचे गेट, बोर्डिंगची वेळ, फ्लाइटची स्थिती, कनेक्शन, तुमची बॅग आणि बेल्ट, पुढे काय करायचे, आणि टर्मिनल 2 मध्ये स्वच्छतागृहे, मदत कक्ष आणि लिफ्ट कुठे आहेत. | Main yeh sawaal aapke phone par hi, bina internet ke bhi, bata sakta hoon: aapka gate, boarding ka time, flight status, connection, aapka bag aur belt, aage kya karna hai, aur Terminal 2 mein washroom, help desk aur lift kahan hain. |  |
| `cloud.search` | I can't search flights or hotels on the phone. That needs the online assistant, which isn't switched on yet. | मैं फ़ोन पर फ्लाइट या होटल नहीं खोज सकता। इसके लिए ऑनलाइन असिस्टेंट चाहिए, जो अभी चालू नहीं है। | मी फोनवर फ्लाइट किंवा हॉटेल शोधू शकत नाही. त्यासाठी ऑनलाइन असिस्टंट लागतो, जो अजून चालू नाही. | Main phone par flight ya hotel search nahi kar sakta. Uske liye online assistant chahiye, jo abhi chalu nahi hai. |  |
| `cloud.cab` | To get a cab, use the ride buttons on Home: they open Uber with the places filled in. | कैब के लिए होम पर राइड बटन इस्तेमाल करें: वे जगहें भरकर Uber खोलते हैं। | कॅबसाठी होमवरील राइड बटणे वापरा: ती ठिकाणे भरून Uber उघडतात. | Cab ke liye Home par ride buttons use kijiye: woh jagah bharkar Uber kholte hain. |  |
| `unknown` | Sorry, I didn't get that. Try asking about your gate, boarding, flight, bag or washrooms. | माफ़ कीजिए, मैं समझ नहीं पाया। अपने गेट, बोर्डिंग, फ्लाइट, बैग या शौचालय के बारे में पूछें। | माफ करा, मला समजले नाही. तुमचे गेट, बोर्डिंग, फ्लाइट, बॅग किंवा स्वच्छतागृह याबद्दल विचारा. | Maaf kijiye, main samjha nahi. Apne gate, boarding, flight, bag ya washroom ke baare mein poochhiye. |  |

### Times and durations

| Key | English | Hindi | Marathi | Hinglish | OK? / suggestion |
|---|---|---|---|---|---|
| `time.tomorrow` | {time} tomorrow | कल {time} | उद्या {time} | kal {time} |  |
| `time.yesterday` | {time} yesterday | पिछले दिन {time} | काल {time} | pichhle din {time} |  |
| `time.other_day` | {time} on {date} | {date} को {time} | {date} रोजी {time} | {date} ko {time} |  |
| `duration.h_m` | {h} h {m} min | {h} घंटे {m} मिनट | {h} तास {m} मिनिटे | {h} ghante {m} minute |  |
| `duration.h` | {h} h | {h} घंटे | {h} तास | {h} ghante |  |
| `duration.m` | {m} min | {m} मिनट | {m} मिनिटे | {m} minute |  |

### Labels around the answer (chip under the reply, buttons, quick questions)

| Key | English | Hindi | Marathi | Hinglish | OK? / suggestion |
|---|---|---|---|---|---|
| `source.phone` | Answered on your phone | आपके फ़ोन पर दिया गया जवाब | तुमच्या फोनवर दिलेले उत्तर | Aapke phone par jawab |  |
| `source.as_of` | as of {time} | {time} तक की जानकारी | {time} पर्यंतची माहिती | {time} tak ki jaankari |  |
| `action.NAVIGATE` | Navigate to gate | गेट तक रास्ता | गेटपर्यंत मार्ग | Gate tak raasta |  |
| `action.OPEN_MAP` | Open the map | नक्शा खोलें | नकाशा उघडा | Map kholiye |  |
| `action.OPEN_BAGS` | Open Bags | बैग्स खोलें | Bags उघडा | Bags kholiye |  |
| `action.OPEN_VAULT` | Open Document vault | डॉक्यूमेंट वॉल्ट खोलें | डॉक्युमेंट व्हॉल्ट उघडा | Document vault kholiye |  |
| `action.UNLOCK_VAULT` | Unlock vault | वॉल्ट अनलॉक करें | व्हॉल्ट अनलॉक करा | Vault unlock kijiye |  |
| `action.OPEN_HOME` | Open Home | होम खोलें | होम उघडा | Home kholiye |  |
| `chip.gate` | Where is my gate? | मेरा गेट कहाँ है? | माझे गेट कोणते आहे? | Mera gate kahan hai? |  |
| `chip.boarding_close` | When does boarding close? | बोर्डिंग कब बंद होगी? | बोर्डिंग किती वाजता बंद होईल? | Boarding kitne baje band hogi? |  |
| `chip.bag` | Where is my bag? | मेरा बैग कहाँ है? | माझी बॅग कुठे आहे? | Mera bag kahan hai? |  |
| `chip.flight` | Is my flight on time? | क्या मेरी फ्लाइट समय पर है? | माझी फ्लाइट वेळेवर आहे का? | Meri flight time pe hai? |  |
| `chip.washroom` | Where are the washrooms? | शौचालय कहाँ है? | स्वच्छतागृह कुठे आहे? | Washroom kidhar hai? |  |
| `chip.next` | What should I do next? | अब मुझे क्या करना है? | आता पुढे काय करायचे? | Ab kya karna hai? |  |

## 2. Real answers (how the sentences read with data)

Demo data: flight IJ 512 Mumbai 08:00 to Bengaluru 09:45, a connection SM 220 at 11:05, the T2 demo map, and
Paramvir's SAMPLE vault documents. Built by the app's own code.

| Situation | English | Hindi | Marathi | Hinglish | OK? / suggestion |
|---|---|---|---|---|---|
| Gate not announced yet | The gate for IJ 512 is not announced yet. Your flight departs at 08:00. | फ्लाइट IJ 512 का गेट अभी घोषित नहीं हुआ है। आपकी फ्लाइट 08:00 पर रवाना होगी। | फ्लाइट IJ 512 चे गेट अजून जाहीर झालेले नाही. तुमची फ्लाइट 08:00 वाजता सुटेल. | Flight IJ 512 ka gate abhi announce nahi hua hai. Aapki flight 08:00 baje nikalegi. | |
| Gate known, boarding close is an estimate | Your gate for IJ 512 is 32. Boarding closes at about 07:35 (estimate). | आपकी फ्लाइट IJ 512 का गेट 32 है। बोर्डिंग लगभग 07:35 पर बंद होगी (अनुमान)। | तुमच्या फ्लाइट IJ 512 चे गेट 32 आहे. बोर्डिंग साधारण 07:35 वाजता बंद होईल (अंदाज). | Aapki flight IJ 512 ka gate 32 hai. Boarding lagbhag 07:35 baje band hogi (estimate). | |
| Boarding close not announced | The boarding close time for IJ 512 is not announced yet. Your flight departs at 08:00. | फ्लाइट IJ 512 की बोर्डिंग बंद होने का समय अभी घोषित नहीं हुआ है। आपकी फ्लाइट 08:00 पर रवाना होगी। | फ्लाइट IJ 512 चे बोर्डिंग बंद होण्याची वेळ अजून जाहीर झालेली नाही. तुमची फ्लाइट 08:00 वाजता सुटेल. | Flight IJ 512 ki boarding band hone ka time abhi announce nahi hua hai. Aapki flight 08:00 baje nikalegi. | |
| Flight on plan | IJ 512 departs at 08:00 and lands at 09:45. No change in your saved plan. | फ्लाइट IJ 512 08:00 पर रवाना होगी और 09:45 पर पहुँचेगी। आपकी सेव की गई योजना में कोई बदलाव नहीं है। | फ्लाइट IJ 512 08:00 वाजता सुटेल आणि 09:45 वाजता पोहोचेल. तुमच्या सेव्ह केलेल्या योजनेत काहीही बदल नाही. | Flight IJ 512 08:00 baje nikalegi aur 09:45 baje pahunchegi. Aapke saved plan mein koi badlav nahi hai. | |
| Connection (time gap only) | IJ 512 lands at 09:45. SM 220 departs at 11:05, so you have 1 h 20 min between them. The phone can't tell whether that's enough time at the airport: check with the airline. | फ्लाइट IJ 512 09:45 पर पहुँचेगी। फ्लाइट SM 220 11:05 पर रवाना होगी, यानी बीच में 1 घंटे 20 मिनट हैं। यह समय एयरपोर्ट पर काफ़ी है या नहीं, यह फ़ोन नहीं बता सकता: एयरलाइन से पूछें। | फ्लाइट IJ 512 09:45 वाजता पोहोचेल. फ्लाइट SM 220 11:05 वाजता सुटेल, म्हणजे मध्ये 1 तास 20 मिनिटे आहेत. विमानतळावर एवढा वेळ पुरेसा आहे की नाही हे फोन सांगू शकत नाही: एअरलाइनला विचारा. | Flight IJ 512 09:45 baje pahunchegi. Flight SM 220 11:05 baje nikalegi, yaani beech mein 1 ghante 20 minute hain. Airport par itna time kaafi hai ya nahi, yeh phone nahi bata sakta: airline se poochhiye. | |
| No bag registered | No bag is registered on this phone yet. Register it in the Bags tab before you hand it over at the counter. | इस फ़ोन पर अभी कोई बैग रजिस्टर नहीं है। काउंटर पर देने से पहले बैग्स टैब में उसे रजिस्टर करें। | या फोनवर अजून कोणतीही बॅग नोंदवलेली नाही. काउंटरवर देण्यापूर्वी Bags टॅबमध्ये ती नोंदवा. | Is phone par abhi koi bag register nahi hai. Counter par dene se pehle Bags tab mein register kijiye. | |
| What next | Next: IJ 512 departs at 08:00 from Mumbai Airport T2 (BOM). At the airport: check-in or bag drop, then security, then your gate. | आगे: फ्लाइट IJ 512 08:00 पर Mumbai Airport T2 (BOM) से रवाना होगी। एयरपोर्ट पर: चेक-इन या बैग ड्रॉप, फिर सुरक्षा जांच, फिर अपना गेट। | पुढे: फ्लाइट IJ 512 08:00 वाजता Mumbai Airport T2 (BOM) येथून सुटेल. विमानतळावर: चेक-इन किंवा बॅग ड्रॉप, मग सुरक्षा तपासणी, मग तुमचे गेट. | Aage: flight IJ 512 08:00 baje Mumbai Airport T2 (BOM) se nikalegi. Airport par: check-in ya bag drop, phir security, phir apna gate. | |
| Washrooms near gate 41 | Washrooms in Terminal 2 (closest to gate 41 first): near Gate 42 (Departures, after security); near Food court (Departures, after security); near Gate 32 (Departures, after security); near Check-in row D (Departures, before security). From the demo map, not an official airport map. | टर्मिनल 2 में शौचालय (गेट 41 के सबसे पास वाले पहले): गेट 42 के पास (प्रस्थान, सुरक्षा जांच के बाद); फूड कोर्ट के पास (प्रस्थान, सुरक्षा जांच के बाद); गेट 32 के पास (प्रस्थान, सुरक्षा जांच के बाद); चेक-इन पंक्ति D के पास (प्रस्थान, सुरक्षा जांच से पहले)। यह डेमो नक्शे से है, एयरपोर्ट का आधिकारिक नक्शा नहीं। | टर्मिनल 2 मधील स्वच्छतागृहे (गेट 41 च्या सर्वात जवळची आधी): गेट 42 जवळ (प्रस्थान, सुरक्षा तपासणीनंतर); फूड कोर्ट जवळ (प्रस्थान, सुरक्षा तपासणीनंतर); गेट 32 जवळ (प्रस्थान, सुरक्षा तपासणीनंतर); चेक-इन रांग D जवळ (प्रस्थान, सुरक्षा तपासणीपूर्वी). हे डेमो नकाशावरून आहे, विमानतळाचा अधिकृत नकाशा नाही. | Terminal 2 mein washroom (gate 41 ke sabse paas wale pehle): Gate 42 ke paas (Departures, security ke baad); Food court ke paas (Departures, security ke baad); Gate 32 ke paas (Departures, security ke baad); Check-in row D ke paas (Departures, security se pehle). Yeh demo map se hai, airport ka official map nahi. | |
| Passport and visa (vault summaries) | In Delhi → Kinshasa: Passport (Paramvir): valid until 12 Mar 2031, ready (SAMPLE). | Delhi → Kinshasa में: पासपोर्ट (Paramvir): 12 मार्च 2031 तक वैध, तैयार (SAMPLE)। | Delhi → Kinshasa मध्ये: पासपोर्ट (Paramvir): 12 मार्च 2031 पर्यंत वैध, तयार (SAMPLE). | Delhi → Kinshasa mein: Passport (Paramvir): 12 Mar 2031 tak valid, ready (SAMPLE). | |
| Is the visa saved? | In Delhi → Kinshasa: E-visa for Rwanda (Paramvir): valid until 20 Oct 2026, expires soon (8 days left) (SAMPLE). I can't tell which documents a country requires: check with the airline or embassy. | Delhi → Kinshasa में: रवांडा के लिए ई-वीज़ा (Paramvir): 20 अक्तू॰ 2026 तक वैध, जल्द समाप्त (8 दिन बाकी) (SAMPLE)। किसी देश के लिए कौन-से दस्तावेज़ ज़रूरी हैं, यह मैं नहीं बता सकता: एयरलाइन या दूतावास से पूछें। | Delhi → Kinshasa मध्ये: रवांडासाठी ई-व्हिसा (Paramvir): 20 ऑक्टो 2026 पर्यंत वैध, लवकरच संपेल (8 दिवस बाकी) (SAMPLE). एखाद्या देशासाठी कोणती कागदपत्रे आवश्यक आहेत हे मी सांगू शकत नाही: एअरलाइन किंवा दूतावासाला विचारा. | Delhi → Kinshasa mein: Rwanda ke liye E-visa (Paramvir): 20 Oct 2026 tak valid, jaldi expire hoga (8 din baaki) (SAMPLE). Kisi desh ke liye kaun se documents zaroori hain, yeh main nahi bata sakta: airline ya embassy se poochhiye. | |
| Vault locked | Your Document Vault is locked. Unlock it to see this. | आपका डॉक्यूमेंट वॉल्ट लॉक है। इसे देखने के लिए वॉल्ट अनलॉक करें। | तुमचा डॉक्युमेंट व्हॉल्ट लॉक आहे. हे पाहण्यासाठी व्हॉल्ट अनलॉक करा. | Aapka Document Vault lock hai. Ise dekhne ke liye vault unlock kijiye. | |
| Hotel search (needs the online assistant) | I can't search flights or hotels on the phone. That needs the online assistant, which isn't switched on yet. | मैं फ़ोन पर फ्लाइट या होटल नहीं खोज सकता। इसके लिए ऑनलाइन असिस्टेंट चाहिए, जो अभी चालू नहीं है। | मी फोनवर फ्लाइट किंवा हॉटेल शोधू शकत नाही. त्यासाठी ऑनलाइन असिस्टंट लागतो, जो अजून चालू नाही. | Main phone par flight ya hotel search nahi kar sakta. Uske liye online assistant chahiye, jo abhi chalu nahi hai. | |
| Not understood | Sorry, I didn't get that. Try asking about your gate, boarding, flight, bag or washrooms. | माफ़ कीजिए, मैं समझ नहीं पाया। अपने गेट, बोर्डिंग, फ्लाइट, बैग या शौचालय के बारे में पूछें। | माफ करा, मला समजले नाही. तुमचे गेट, बोर्डिंग, फ्लाइट, बॅग किंवा स्वच्छतागृह याबद्दल विचारा. | Maaf kijiye, main samjha nahi. Apne gate, boarding, flight, bag ya washroom ke baare mein poochhiye. | |

## 3. Words Ask understands (lexicons)

One row per idea (concept). The app also forgives small typos in words of 5+ letters and cuts common Marathi
endings (-चा/-ची/-चे/-ला/-वर/-मध्ये, -cha/-chi/-var...). Add missing words or spellings in the last column.

| Concept | English | Hindi | Marathi | Hinglish | Marathi in English letters | Missing words? |
|---|---|---|---|---|---|---|
| `GATE` | gate, gates, gate number, gate no, boarding gate | गेट, गेट नंबर, द्वार | गेट, गेट क्रमांक, गेट नंबर | gate no, mera get, meri get, get kahan, get kaha, get kidhar, get number, get no, konsa get, kaunsa get, get kya hai | gate konta, get konta, gate kuthe, maza gate, majha gate, majhe gate, maze gate |  |
| `BOARDING` | boarding, board, boards | बोर्डिंग | बोर्डिंग | boarding, bording, boarding kab | boarding |  |
| `CLOSE` | close, closes, closing, closed, last call, final call, shut, shuts | बंद, बंद होगी, बंद होगा, लास्ट कॉल, आखिरी कॉल | बंद, बंद होईल, बंद होणार | band, bandh, band hogi, band hoga, close hogi, close hoga, last call, gate band | band, bandh, band hoil, band honar |  |
| `START` | start, starts, starting, begin, begins, open, opens | शुरू, शुरु, चालू | सुरू, सुरु, चालू | shuru, chalu, start hogi, shuru hogi, shuru hoga | suru, chalu, suru hoil, suru honar |  |
| `WHEN` | when, what time, time, how long, by when, at what time, how much time | कब, कितने बजे, किस समय, कितनी देर, कब तक, टाइम, समय | कधी, केव्हा, किती वाजता, किती वेळ, वेळ, किती वाजेपर्यंत | kab, kitne baje, kitne bje, kitne bajey, kis time, kitna time, kab tak, kitni der, kitna samay | kadhi, kevha, kiti vajta, kiti vajata, kiti vel, kiti vajeparyant |  |
| `WHERE` | where, which way, how do i get, how to get to, way to, where is, where are, nearest, closest | कहाँ, किधर, कहाँ पर | कुठे, कुठं, कोणत्या बाजूला | kahan, kaha, kidhar, kahaan, kider, kahan par, kaha pe, kahan pe | kuthe, kuthay, kute, kuthe aahe, kontya baju |  |
| `GO` | go, going, head, walk | जाना, जाऊँ, जाएँ, जाना है | जाऊ, जायचे, जायचं | jaana, jana, jaun, jau, jaaun, jaana hai, jaaye, jaaun kya | jau, jaycha, jaayche, jayche, jaaycha, jaaychay |  |
| `FLIGHT` | flight, flights, plane, my flight | फ्लाइट, फ्लाईट, उड़ान, प्लेन, जहाज | फ्लाइट, विमान | flight, flite, flyt, plane, jahaz, hawai jahaz, viman | vimaan, viman, vimana |  |
| `STATUS` | status, update, updates, news, info | स्टेटस, स्थिति, क्या हुआ, अपडेट | स्थिती, स्टेटस, काय झाले, काय झालं, अपडेट | status, haal, kya hua, kya haal, update | kay zala, kay jhala, kay jhale, kay zale |  |
| `DELAY` | delay, delayed, delays, late, running late, behind schedule | देर, देरी, लेट, विलंब, देर से | उशीर, उशिरा, लेट, विलंब | late, der, deri, delay, der se, late hai, late hogi | ushira, ushir, ushiraa |  |
| `CANCEL` | cancel, cancelled, canceled, cancellation | रद्द, कैंसल, कैंसिल | रद्द, कॅन्सल | cancel, radd, cancel ho gayi, cancel hui, cancel hua | radd, radda |  |
| `ON_TIME` | on time, on schedule | समय पर, टाइम पर, सही समय पर | वेळेवर, वेळेत | time pe, time par, samay par, time pr, sahi time | velevar, veli var, time var, velet |  |
| `ARRIVE` | land, lands, landing, arrive, arrives, arriving, arrival, reach, reaches | पहुँचेगी, पहुँचेगा, पहुँचेंगे, उतरेगी, लैंड, लैंडिंग, पहुँचना | पोहोचेल, पोचेल, उतरेल, लँड, पोहोचणार | pahunchegi, pahuchegi, pahunchna, pahunchenge, utregi, utrenge, landing kab, pahunch | pohochel, pohchel, pohochnar, utrel |  |
| `CONNECTION` | connection, connecting, connecting flight, transfer, layover, transit, onward flight, next leg | कनेक्शन, कनेक्टिंग, कनेक्टिंग फ्लाइट, अगली फ्लाइट, ट्रांजिट | कनेक्शन, कनेक्टिंग, कनेक्टिंग फ्लाइट, पुढची फ्लाइट, ट्रान्झिट | connection, connecting, connecting flight, agli flight, transit, doosri flight pakadni | pudhchi flight, pudhcha flight |  |
| `MISS` | miss, missed, missing, make it | छूट, छूटेगी, छूट जाएगी, मिस | चुकेल, चुकणार, चुकली, मिस | miss, chhoot, chhut, chhootegi, chhutegi, chhut jayegi, chhoot jayegi, miss hogi, miss hoga, miss ho jayegi | chukel, chukanar, chuknar, miss honar, miss hoil, chukli |  |
| `BAG` | bag, bags, baggage, luggage, suitcase, suitcases, checked bag, baggage claim | बैग, सामान, लगेज, बैगेज, अटैची | बॅग, बॅगा, बॅगेज, सामान, लगेज | bag, baig, saman, samaan, saaman, luggage, baggage, bagaj, attachi | baga, bagaa, bagecha |  |
| `BELT` | belt, carousel, baggage belt, belt number, conveyor | बेल्ट, बेल्ट नंबर | बेल्ट | belt, belt number, belt no |  |  |
| `COLLECT` | collect, pick up, claim, baggage claim | मिलेगा, मिलेगी, मिलेंगे, लेना, उठाना | मिळेल, मिळणार, घ्यायची, घ्यायचे | milega, milegi, milenge, lena, lene, uthana, collect | milel, milnar, ghyaychi, ghyaychya |  |
| `NEXT` | next, next step, now what, after this, then what | आगे, अगला कदम, अब क्या | पुढे, पुढचे, पुढची पायरी, आता काय | aage, aagey, ab kya, agla step, next | pudhe, pudhcha, pudhchi, aata kay |  |
| `WHAT_TO_DO` | what should i do, what do i do, what to do, what do i need, what now, what next | क्या करूँ, क्या करना है, क्या करना, क्या करें | काय करू, काय करायचे, काय करायचं, काय करावे | kya karu, kya karun, kya karoon, kya karna hai, kya karna, ab kya karu, ab kya karna hai, ab kya karna, kya kare, kya karein | kay karu, aata kay karu, kay karayche, kay karaycha, kay karaychay, kay karave |  |
| `DOCUMENT` | document, documents, papers, travel documents, id | दस्तावेज़, कागज़ात, कागज़ | कागदपत्रे, कागदपत्र, दस्तऐवज | document, documents, kagaz, kagzat, dastavez | kagadpatra, kagadpatre |  |
| `PASSPORT` | passport | पासपोर्ट | पासपोर्ट | passport |  |  |
| `VISA` | visa, e visa, evisa, transit visa | वीज़ा, विज़ा | व्हिसा, विसा | visa |  |  |
| `INSURANCE` | insurance, travel insurance, insurance policy | बीमा, इंश्योरेंस, ट्रैवल इंश्योरेंस | विमा, इन्शुरन्स | insurance, bima, beema | vima |  |
| `SAVED` | saved, save, stored, do i have, have i got, have i saved, on my phone, in my vault, uploaded | सेव, सेव है, सहेजा, रखा है, फ़ोन में है | सेव्ह, सेव, ठेवला, ठेवली, ठेवले, जतन | saved, save hai, save kiya, rakha hai, rakha, phone mein hai, vault mein hai | theva, thevla, thevli, thevle, jatan, save aahe |  |
| `EXPIRY` | expire, expires, expiry, expiring, expired, valid, validity, valid till, valid until | ख़त्म, खतम, समाप्त, एक्सपायर, वैध, वैधता, मान्य | संपेल, संपतो, संपते, मुदत, वैध, समाप्त, एक्सपायर | expire, khatam, samapt, valid, validity, kab tak valid | sampel, sampto, sampte, mudat |  |
| `WASHROOM` | washroom, washrooms, toilet, toilets, restroom, restrooms, rest room, bathroom, loo, lavatory, wc, gents, ladies | शौचालय, टॉयलेट, वॉशरूम, बाथरूम | स्वच्छतागृह, शौचालय, टॉयलेट, वॉशरूम, बाथरूम, मुतारी | toilet, washroom, bathroom, shauchalay, latrine, pishab ghar | swachhatagruha, swachhtagruh, swachhatagruh, mutari, sandas |  |
| `HELP_DESK` | help desk, helpdesk, information desk, info desk, information counter, enquiry, inquiry, customer service | हेल्प डेस्क, सहायता डेस्क, सूचना डेस्क, पूछताछ, सहायता केंद्र | मदत कक्ष, माहिती कक्ष, चौकशी, हेल्प डेस्क | help desk, helpdesk, enquiry, inquiry, information desk, madad kendra, sahayata kendra, info counter | madat kaksha, mahiti kaksha, chaukashi |  |
| `LIFT` | lift, lifts, elevator, elevators | लिफ्ट, एलिवेटर | लिफ्ट | lift, elevator |  |  |
| `HELP` | help, assist, assistance, support, what can you do, how does this work | मदद, सहायता, हेल्प | मदत, सहाय्य | madad, help, sahayata, madat | madat |  |
| `HOTEL` | hotel, hotels, place to stay, accommodation | होटल, रुकने की जगह | हॉटेल, राहण्याची जागा | hotel, rukne ki jagah, hotel chahiye | rahaychi jaga, rahnyachi jaga |  |
| `CAB` | cab, cabs, taxi, uber, ola, ride | कैब, टैक्सी, गाड़ी, उबर, ओला | कॅब, टॅक्सी, गाडी, रिक्षा, उबर | cab, taxi, gaadi, gadi, uber, ola, auto | rickshaw, riksha |  |
| `SEARCH` | find, search, look for, best, cheapest, fastest, another, other flights, alternative, alternatives | ढूँढो, खोजो, सबसे सस्ता, सबसे अच्छी, कोई और, दूसरी | शोधा, शोधून, स्वस्त, दुसरी, दुसरे | dhundo, dhoondo, dhundho, khojo, sabse sasta, sabse accha, best, koi aur, dusri, doosri, flights dikhao | shodha, shodh, dusri, dusra, swasta |  |
| `BOOK` | book, booking, reserve | बुक, बुकिंग | बुक, बुकिंग | book, book karo, booking | book kara |  |
| `MARKERS` | the, is, my, where, when, what, does, do, will, i, a, an, of, for, are, how, which, can, should, near, it, at, there | है, हैं, कहाँ, मेरा, मेरी, मेरे, मुझे, क्या, कब, कितने, बजे, होगा, होगी, कौन, कौनसा, किधर, चाहिए, गया, गई, अब, और, कैसे, हूँ, जाएगी, अभी | आहे, आहेत, कुठे, माझा, माझी, माझे, माझ्या, मला, काय, कधी, किती, वाजता, होईल, होणार, येईल, झाले, झाली, आता, पुढे, कोणता, कोणते, कोणती, कोणत्या, केव्हा, हवी, हवे, पाहिजे, तुमचा, तुमची, शोधा, हॉटेल | hai, hain, kahan, kaha, kahaan, kidhar, mera, meri, mere, mujhe, kab, kitne, kitna, baje, kya, hoga, hogi, kaun, kaunsa, konsa, kaise, chahiye, gaya, gayi, hua, hui, aur, ab, bhai, yaar, karo, karna, wala, wali, abhi, pe, par, ke, ki, paas, liye, mein, dhundo | aahe, ahe, hoto, aahet, ahet, kuthe, kuthay, majhi, majha, majhe, majhya, mazi, maza, maze, mala, kay, kiti, vajta, kadhi, kevha, honar, hoil, yeil, zala, jhala, aata, pudhe, kontya, konta, konti, tumcha, tumchi, pahije, havi, shodha |  |

`MARKERS` are everyday words that show which language a question is in (they pick the reply language).

## 4. Test questions (golden set)

The app must understand at least 90% of these (today: all). Please add questions the way you would ask them.

| Language | Question | Understood as | Natural? / better wording |
|---|---|---|---|
| HI | मेरा गेट कहाँ है? | GATE | |
| HI | मेरी फ्लाइट का गेट नंबर क्या है | GATE | |
| HI | गेट कौनसा है | GATE | |
| HI | बोर्डिंग कब शुरू होगी? | BOARDING_TIME | |
| HI | बोर्डिंग कितने बजे है | BOARDING_TIME | |
| HI | बोर्डिंग कब बंद होगी? | BOARDING_CLOSE | |
| HI | गेट कितने बजे बंद होगा | BOARDING_CLOSE | |
| HI | क्या मेरी फ्लाइट समय पर है? | FLIGHT_STATUS | |
| HI | मेरी फ्लाइट लेट है क्या | FLIGHT_STATUS | |
| HI | फ़्लाइट रद्द हो गई क्या | FLIGHT_STATUS | |
| HI | मेरी कनेक्टिंग फ्लाइट छूट जाएगी क्या? | CONNECTION_STATUS | |
| HI | मेरा बैग कहाँ है? | BAG_STATUS | |
| HI | मेरा सामान कहां है | BAG_STATUS | |
| HI | मेरा बैग किस बेल्ट पर आएगा? | BAGGAGE_BELT | |
| HI | अब मुझे क्या करना है? | NEXT_STEP | |
| HI | अब कहाँ जाना है | NEXT_STEP | |
| HI | मेरा पासपोर्ट कब ख़त्म होगा? | DOCUMENT_EXPIRY | |
| HI | शौचालय कहाँ है? | WHERE_IS | |
| HI | लिफ्ट किधर है | WHERE_IS | |
| HI | सहायता डेस्क कहाँ है | WHERE_IS | |
| HI | मुझे मदद चाहिए | HELP | |
| HI | एयरपोर्ट के पास होटल ढूँढो | SEARCH_HOTELS | |
| HI | होटल के लिए टैक्सी चाहिए | CAB | |
| MR | माझे गेट कोणते आहे? | GATE | |
| MR | माझ्या फ्लाइटचे गेट कुठे आहे | GATE | |
| MR | बोर्डिंग कधी सुरू होईल? | BOARDING_TIME | |
| MR | बोर्डिंग किती वाजता बंद होईल? | BOARDING_CLOSE | |
| MR | गेट कधी बंद होणार | BOARDING_CLOSE | |
| MR | माझी फ्लाइट वेळेवर आहे का? | FLIGHT_STATUS | |
| MR | विमान उशिरा आहे का? | FLIGHT_STATUS | |
| MR | माझे विमान रद्द झाले का | FLIGHT_STATUS | |
| MR | माझी कनेक्टिंग फ्लाइट चुकेल का? | CONNECTION_STATUS | |
| MR | माझी बॅग कुठे आहे? | BAG_STATUS | |
| MR | माझे सामान कुठे आहे | BAG_STATUS | |
| MR | बॅग कोणत्या बेल्टवर येईल? | BAGGAGE_BELT | |
| MR | आता पुढे काय करायचे? | NEXT_STEP | |
| MR | मी आता काय करू? | NEXT_STEP | |
| MR | माझ्या पासपोर्टची मुदत कधी संपते? | DOCUMENT_EXPIRY | |
| MR | स्वच्छतागृह कुठे आहे? | WHERE_IS | |
| MR | लिफ्ट कुठे आहे | WHERE_IS | |
| MR | मदत कक्ष कुठे आहे | WHERE_IS | |
| MR | मला मदत हवी आहे | HELP | |
| MR | विमानतळाजवळ हॉटेल शोधा | SEARCH_HOTELS | |
| HINGLISH | Mera gate kahan hai? | GATE | |
| HINGLISH | meri flight ka gate number kya hai | GATE | |
| HINGLISH | mera get kaha hai | GATE | |
| HINGLISH | gate kidhar hai bhai | GATE | |
| HINGLISH | boarding kab shuru hogi | BOARDING_TIME | |
| HINGLISH | Boarding kitne baje band hogi? | BOARDING_CLOSE | |
| HINGLISH | gate kab band hoga | BOARDING_CLOSE | |
| HINGLISH | Meri flight time pe hai? | FLIGHT_STATUS | |
| HINGLISH | flight late hai kya | FLIGHT_STATUS | |
| HINGLISH | meri flight cancel ho gayi kya | FLIGHT_STATUS | |
| HINGLISH | Doha connection miss hoga kya? | CONNECTION_STATUS | |
| HINGLISH | connecting flight chhoot jayegi kya | CONNECTION_STATUS | |
| HINGLISH | mera samaan kahan hai | BAG_STATUS | |
| HINGLISH | Mera bag kahaan hai? | BAG_STATUS | |
| HINGLISH | bag kis belt pe aayega | BAGGAGE_BELT | |
| HINGLISH | saman kahan milega | BAGGAGE_BELT | |
| HINGLISH | ab kya karna hai | NEXT_STEP | |
| HINGLISH | ab kya karu | NEXT_STEP | |
| HINGLISH | passport kab expire hoga | DOCUMENT_EXPIRY | |
| HINGLISH | Washroom kidhar hai? | WHERE_IS | |
| HINGLISH | toilet kahan hai | WHERE_IS | |
| HINGLISH | lift kidhar hai | WHERE_IS | |
| HINGLISH | madad chahiye | HELP | |
| HINGLISH | airport ke paas hotel dhundo | SEARCH_HOTELS | |
| HINGLISH | mujhe taxi chahiye | CAB | |
| HINGLISH | kal mausam kaisa hoga | UNKNOWN | |
| MR | maza gate konta aahe? | GATE | |
| MR | boarding kiti vajta band hoil? | BOARDING_CLOSE | |
| MR | vimaan ushira aahe ka? | FLIGHT_STATUS | |
| MR | majhi flight velevar aahe ka | FLIGHT_STATUS | |
| MR | Majhi connection miss honar ka? | CONNECTION_STATUS | |
| MR | Majhi bag kuthe aahe? | BAG_STATUS | |
| MR | majhi bag kontya belt var yeil | BAGGAGE_BELT | |
| MR | aata kay karu? | NEXT_STEP | |
| MR | passport kadhi sampel | DOCUMENT_EXPIRY | |
| MR | toilet kuthe aahe | WHERE_IS | |
| MR | lift kuthay aahe | WHERE_IS | |
| MR | mala madat pahije | HELP | |
| HINGLISH | Kya mera visa saved hai? | DOCUMENT_MISSING | |
| HI | क्या मेरा वीज़ा सेव है? | DOCUMENT_MISSING | |
| MR | माझा व्हिसा सेव्ह आहे का? | DOCUMENT_MISSING | |
| MR | Majha passport thevla aahe ka? | DOCUMENT_MISSING | |
| HINGLISH | mera insurance phone mein hai kya? | DOCUMENT_MISSING | |
| MR | Majha passport kadhi expire hoto? | DOCUMENT_EXPIRY | |
| HI | मेरा बीमा कब तक वैध है? | DOCUMENT_EXPIRY | |
| HINGLISH | visa kab tak valid hai | DOCUMENT_EXPIRY | |
