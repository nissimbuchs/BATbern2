# Correcting course on the BATbern companion watchos app

## general: handling of current event
The watch app uses the same current-api as the public web-frontend. the current api should work as follows:
1. the current event is the first event in the future or on the same day as now, that is not archived.
2. if the event has no title (or the title is "TBD" and no topic defined yet, then it should write simply the date and the venue as usual, but of course no speakers and sessions.
3. if there is no current event, then it siumply shows the BATbern animation, with a coming soom text instead of the date

## details on complications
I want to detail out the display of complications:
- if the current event is more than one day away, then it should write the date (dd.MM) in the center, and there is no count-down circle, as we have no measure of relation.
- if today is the day of the current event, then it should show the countdown to how many hours till the first session starts. the cirlce shows the countdown from midnight, till the first session starts (so if the session starts at 16:00h and it is 08:00h, then the circle count-up should show 8/16=50% full.
- if today is the day of the event, and there is a session currently running, then it shows how many minutes of this session remain and also the cirlcy cont-down shows this (so if the session is 45min, and 15min remain, then it shows 15/45=33%)
- if today is the day of the event, and its past the last session, then it shows only the BATbern-logo

## session overrun
i want to change the behaviour of in-session organizer possibilities. today in epic4, w4-2 and w4-3 say, that there is a done button at the end of the session and if its over the end, then after i press the done button, i can shift the schedule. i want to change this behaviour:
- the organizer session view should move directly to the next session, when one session ends.
- the organizer session view should show an "extend" button as soon as there are only 10' left for the session. pressing the extend button, shows several options to extend the current session (5min, 10min, 15min,20min) abd shifts all remaining sessions.
- in the first ten minutes of a session, the organizer session view should show a "delayed" button. pressing the delayed button, shows several options to extend the previous session (5min, 10min, 15min,20min), shifts all remaining sessions, and switches back to the previous, now extended session.
