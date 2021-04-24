package at.electrobabe.utils.calendar;

import lombok.extern.slf4j.Slf4j;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.util.MapTimeZoneCache;

import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * read a ics file from the web and filter by items on a certain day
 */
@Slf4j
public class CalendarUtils {

    private static final long ONE_DAY_IN_MILLIS = 86400000;
    private static final String DATE_FORMAT = "yyyy-MM-dd";


    public static String getCalendarEntriesForDay(String date, String url) {
        StringBuilder ret = new StringBuilder();
        log.debug("getCalendar date: {}, url: {}", date, url);
        Calendar cal = getCalendar(url);

        if (cal != null) {
            log.debug("cal: {} ...", cal.toString().substring(0, 100));
        } else {
            log.error("cal is null");
            return null;
        }

        List<VEvent> events = getEventsForDate(cal, date);
        log.debug("events: {}", events.size());
        for (VEvent e : events) {
            log.debug("e: {}", e.getName());
            ret.append(printEvent(e)).append("\n");
        }
        log.debug("ret: {}", ret);
        return ret.toString();
    }

    private static Calendar getCalendar(String urlString) {
        log.debug("urlString {}", urlString);
        try {
            // instead of ical4j.properties
            System.setProperty("net.fortuna.ical4j.timezone.cache.impl", MapTimeZoneCache.class.getName());

            URL url = new URL(urlString);
            URLConnection conn = url.openConnection();

            CalendarBuilder builder = new CalendarBuilder();
            return builder.build(conn.getInputStream());

        } catch (Exception e) {
            log.error("error reading calendar", e);
            return null;
        }
    }

    private static List<VEvent> getEventsForDate(Calendar calendar, String dateStr) {
        List<VEvent> list = new ArrayList<>();
        log.info("search for date: '{}'", dateStr);

        Date date;
        try {
            date = new Date(dateStr, DATE_FORMAT);
        } catch (ParseException e) {
            log.error("error parsing date", e);
            return list;
        }
        log.debug("date: '{}'", date);

        ComponentList<CalendarComponent> components = calendar.getComponents();
        log.debug("components: {}", components.size());

        // needs format yyyy-MM-dd
        String plainDateStr = dateStr.replace("-", "");
        log.debug("plainDateStr: '{}'", plainDateStr);

        for (CalendarComponent c : components) {
            handleComponent(list, date, plainDateStr, c);
        }
        log.debug("getEventsForDate: list={}", list.size());

        return list;
    }

    private static void handleComponent(List<VEvent> list, Date date, String plainDateStr, CalendarComponent c) {
        if (c instanceof VEvent) {
            log.debug("c: '{}'", c.getName());
            VEvent event = (VEvent) c;
            log.debug("event: '{}'", event.getSummary());
            addByDate(plainDateStr, list, date, event);
            log.debug("event added: '{}'", event.getSummary());

        } else if (c instanceof VTimeZone) {
            VTimeZone t = (VTimeZone) c;
            log.debug("time zone: {}", t.getTimeZoneId());
        } else {
            log.warn("what are you?? {}", c.getClass());
        }
    }

    private static void addByDate(String dateStr, List<VEvent> list, Date date, VEvent event) {
        if (matchesDate(dateStr, event) && !inList(list, event)) {
            list.add(event);
            log.debug("single event added: {}", event.getSummary().getValue());

        } else if (event.getSequence() != null) {

            addOccurrence(dateStr, list, date, event);
        }
    }

    private static boolean inList(List<VEvent> list, VEvent event) {
        return list.stream().anyMatch(o -> {
            if (o.getDuration() != null && event.getDuration() != null) {
                return o.getSummary().getValue().equals(event.getSummary().getValue()) && o.getDuration().getValue().equals(event.getDuration().getValue());
            } else {
                return o.getSummary().getValue().equals(event.getSummary().getValue());
            }
        });
    }

    private static void addOccurrence(String dateStr, List<VEvent> list, Date date, VEvent event) {
        try {
            // fixes: VEvent occurrence = event.getOccurrence(date);
            PeriodList periods = event.getConsumedTime(date, new Date(date.getTime() + ONE_DAY_IN_MILLIS));
            log.debug("periods {}", periods.size());
            for (final Period p : periods) {
                if (p.getStart().toString().startsWith(dateStr) && !inList(list, event)) {
                    final VEvent occurrence = (VEvent) event.copy();
                    log.debug("add occurrence {}", occurrence.getSummary());
                    occurrence.getProperties().add(new RecurrenceId(date));
                    list.add(occurrence);

                    log.debug("recurring event added: {}", event.getSummary().getValue());
                }
            }

        } catch (Exception e) {
            log.error("error getting occurrence", e);
        }
    }

    private static boolean matchesDate(String date, VEvent event) {
        if (event == null || event.getStartDate() == null || event.getStartDate().getDate() == null || event.getStartDate().getDate().toString() == null) {
            log.warn("invalid event: {}", event);
            return false;
        }
        log.debug("check start date: {} startsWith? {}", event.getStartDate().getDate().toString(), date);
        boolean ret = event.getStartDate().getDate().toString().startsWith(date);
        log.debug("matchesDate? {}", ret);
        return ret;
    }

    /**
     * see also https://dzone.com/articles/how-to-format-a-string-clarified
     *
     * @param event VEvent
     * @return formatted String
     */
    private static String printEvent(VEvent event) {
        String outOfOffice = event.getProperty("X-MICROSOFT-CDO-BUSYSTATUS") != null ? event.getProperty("X-MICROSOFT-CDO-BUSYSTATUS").getValue() : "";
        String text = String.format("%s (%s) %s", getDurationAsString(event), outOfOffice, event.getSummary().getValue());
        log.info(text);

        return text;
    }

    private static long getDuration(VEvent event) {
        return event.getEndDate().getDate().getTime() - event.getStartDate().getDate().getTime();
    }

    private static String getDurationAsString(VEvent event) {
        return formatMillis(getDuration(event));
    }

    private static String formatMillis(long durationInMillis) {
        long hours = TimeUnit.MILLISECONDS.toHours(durationInMillis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationInMillis) % 60;

        return String.format("%dh %2dm", hours, minutes);
    }
}
